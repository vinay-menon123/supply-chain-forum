package com.cscen.forum.service.agents;

import com.cscen.forum.service.erp.ErpPort;
import com.cscen.forum.service.erp.ErpPort.LoadLine;
import com.cscen.forum.service.erp.ErpPort.Rdc;
import com.cscen.forum.service.erp.ErpPort.RdcCover;
import com.cscen.forum.service.erp.ErpPort.RdcLeg;
import com.cscen.forum.service.erp.ErpPort.SalesChannel;
import com.cscen.forum.service.erp.ErpPort.Shipment;
import com.cscen.forum.service.erp.ErpPort.Sku;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The demand-side brain: multi-echelon sourcing + channel allocation.
 *
 * <p>A stranded consignment is not lost stock — it is <b>late</b> stock. So the real
 * question is never "can we move the truck", it is "which consumer channels can we
 * still serve inside their promise window, and from where". This planner:
 * <ol>
 *   <li>computes how much each RDC can <b>lend</b> without breaking its own cover
 *       before its replenishment lands ({@code (daysOfCover - replenish - safety) x dailyDemand}),</li>
 *   <li>allocates every available unit across SKU x city x channel by <b>avoided
 *       penalty</b> (Qcommerce/Ecommerce first, traditional trade last — it can wait
 *       for the next beat), routing each line to the <b>fastest</b> source that has it,</li>
 *   <li>prices the result: short-haul freight + service penalties + handling, and</li>
 *   <li>works out the <b>backfill</b> — what the lending RDC is owed and where it comes from.</li>
 * </ol>
 * All numbers are deterministic; nothing here is model-generated.
 */
@Service
public class DistributionPlanner {

    /** Days of its own cover an RDC keeps back beyond its inbound replenishment. */
    public static final double RDC_SAFETY_DAYS = 1.0;
    /** Handling / stock-transfer admin cost per unit moved out of an RDC. */
    private static final double HANDLING_PER_UNIT = 0.5;
    /** Short-haul freight is less efficient per km than a full trunk run. */
    private static final double SHORT_HAUL_INEFFICIENCY = 1.6;
    private static final double MIN_RATE_PER_KG = 2.5;

    private final ErpPort erp;

    public DistributionPlanner(ErpPort erp) {
        this.erp = erp;
    }

    // ── output DTOs ──

    public record ChannelFill(String channel, String channelName, int demand, int onTimeUnits,
                              int lateUnits, int unfilledUnits, int fillPct, double etaHrs,
                              double promiseHrs, long penaltyInr, String verdict) {}

    public record CityPlan(String city, int demand, int onTimeUnits, int fillPct, double etaHrs,
                           List<ChannelFill> channels) {}

    public record SkuPlan(String sku, String name, int demand, int fromRdc, int fromTruck,
                          int unfilled, int fillPct) {}

    public record SourceLine(String source, String kind, String city, int units, double etaHrs,
                             long freightInr) {}

    public record Backfill(String rdc, int unitsLent, int unitsRepaid, double repaidInHrs, String note) {}

    public record FulfilmentPlan(String strategyId, int totalDemand, int onTimeUnits, int lateUnits,
                                 int unfilledUnits, int fillPct, long freightInr, long penaltyInr,
                                 long handlingInr, long totalCostInr, List<CityPlan> cities,
                                 List<ChannelFill> channelTotals, List<SkuPlan> skus,
                                 List<SourceLine> sources, List<Backfill> backfills, String summary) {}

    /** One place stock can come from, with what it has and how fast it reaches each city. */
    public record Supply(String name, String kind, Map<String, Integer> availableBySku,
                         Map<String, Double> etaByCity, Map<String, Double> ratePerKgByCity) {}

    /** A single demand line to be satisfied. */
    private record DemandLine(String sku, String city, String channel, int units,
                              double penaltyPerUnit, double promiseHrs, int priority) {}

    /** Mutable allocation record. */
    private static final class Alloc {
        final String source, kind, sku, city, channel;
        final int units;
        final double etaHrs, promiseHrs, penaltyPerUnit;
        Alloc(String source, String kind, String sku, String city, String channel, int units,
              double etaHrs, double promiseHrs, double penaltyPerUnit) {
            this.source = source; this.kind = kind; this.sku = sku; this.city = city;
            this.channel = channel; this.units = units; this.etaHrs = etaHrs;
            this.promiseHrs = promiseHrs; this.penaltyPerUnit = penaltyPerUnit;
        }
        boolean onTime() { return etaHrs <= promiseHrs; }
    }

    // ── RDC lendable capacity ──

    /**
     * Units an RDC can lend per SKU without running dry before its own replenishment
     * lands. It must retain {@code (replenishInDays + safety)} days of its own sales.
     */
    public Map<String, Integer> lendableBySku(Rdc rdc) {
        Map<String, Integer> out = new LinkedHashMap<>();
        double retainDays = rdc.replenishInDays() + RDC_SAFETY_DAYS;
        for (RdcCover c : safe(rdc.cover())) {
            double spareDays = c.daysOfCover() - retainDays;
            int units = (int) Math.max(0, Math.floor(spareDays * c.dailyDemandUnits()));
            out.put(c.sku(), units);
        }
        return out;
    }

    /** Short-haul rate per kg for an RDC leg, scaled off the trunk rate. */
    public double shortHaulRatePerKg(double trunkRatePerKg, double trunkKm, double legKm) {
        if (trunkKm <= 0) trunkKm = 800;
        double rate = trunkRatePerKg * (legKm / trunkKm) * SHORT_HAUL_INEFFICIENCY;
        return Math.max(MIN_RATE_PER_KG, round2(rate));
    }

    /** Build a Supply for an RDC serving the shipment's destination cities. */
    public Optional<Supply> rdcSupply(Rdc rdc, Shipment s, double extDelayHrs) {
        Map<String, Integer> avail = lendableBySku(rdc);
        if (avail.values().stream().allMatch(v -> v <= 0)) return Optional.empty();
        Map<String, Double> eta = new LinkedHashMap<>();
        Map<String, Double> rate = new LinkedHashMap<>();
        double trunkRate = s.freightRatePerKg() > 0 ? s.freightRatePerKg() : 8.0;
        for (RdcLeg leg : safe(rdc.legs())) {
            if (!destinations(s).contains(leg.toCity())) continue;
            // RDC stock is already picked/packed at the DC, so only the leg + a short
            // dispatch window applies; weather still bites.
            eta.put(leg.toCity(), round1(leg.transitHrs() + 2.0 + extDelayHrs * 0.5));
            rate.put(leg.toCity(), shortHaulRatePerKg(trunkRate, 800, leg.distanceKm()));
        }
        if (eta.isEmpty()) return Optional.empty();
        return Optional.of(new Supply(rdc.city() + " RDC", "RDC", avail, eta, rate));
    }

    /**
     * Build a Supply for the stranded consignment itself — it still carries the whole
     * load, so this is a 100%-availability source that is simply slower.
     */
    public Supply truckSupply(String name, Shipment s, Map<String, Double> etaByCity,
                              Map<String, Double> rateByCity) {
        Map<String, Integer> avail = new LinkedHashMap<>();
        for (LoadLine l : safe(s.loadLines())) {
            avail.merge(l.sku(), lineUnits(l), Integer::sum);
        }
        return new Supply(name, "TRUCK", avail, etaByCity, rateByCity);
    }

    // ── the allocation ──

    public FulfilmentPlan plan(String strategyId, Shipment s, List<Supply> supplies) {
        List<DemandLine> demand = demandLines(s);
        int totalDemand = demand.stream().mapToInt(DemandLine::units).sum();

        // Mutable stock pool per supply.
        Map<String, Map<String, Integer>> pool = new LinkedHashMap<>();
        for (Supply sup : supplies) pool.put(sup.name(), new LinkedHashMap<>(sup.availableBySku()));

        // Highest avoided penalty first, then tightest promise window.
        List<DemandLine> ordered = new ArrayList<>(demand);
        ordered.sort(Comparator.comparingDouble((DemandLine d) -> -d.penaltyPerUnit())
                .thenComparingDouble(DemandLine::promiseHrs)
                .thenComparing(DemandLine::sku));

        List<Alloc> allocs = new ArrayList<>();
        Map<String, Integer> unfilled = new LinkedHashMap<>(); // key sku|city|channel

        for (DemandLine d : ordered) {
            int need = d.units();
            // Fastest source that can actually reach this city with this SKU.
            List<Supply> candidates = supplies.stream()
                    .filter(sup -> sup.etaByCity().containsKey(d.city()))
                    .filter(sup -> pool.get(sup.name()).getOrDefault(d.sku(), 0) > 0)
                    .sorted(Comparator.comparingDouble(sup -> sup.etaByCity().get(d.city())))
                    .toList();
            for (Supply sup : candidates) {
                if (need <= 0) break;
                int have = pool.get(sup.name()).getOrDefault(d.sku(), 0);
                int take = Math.min(have, need);
                if (take <= 0) continue;
                pool.get(sup.name()).put(d.sku(), have - take);
                allocs.add(new Alloc(sup.name(), sup.kind(), d.sku(), d.city(), d.channel(), take,
                        sup.etaByCity().get(d.city()), d.promiseHrs(), d.penaltyPerUnit()));
                need -= take;
            }
            if (need > 0) unfilled.put(key(d.sku(), d.city(), d.channel()), need);
        }

        return assemble(strategyId, s, supplies, demand, allocs, unfilled, totalDemand, pool);
    }

    // ── assembling the plan output ──

    private FulfilmentPlan assemble(String strategyId, Shipment s, List<Supply> supplies,
                                    List<DemandLine> demand, List<Alloc> allocs,
                                    Map<String, Integer> unfilled, int totalDemand,
                                    Map<String, Map<String, Integer>> pool) {

        int onTimeUnits = allocs.stream().filter(Alloc::onTime).mapToInt(a -> a.units).sum();
        int lateUnits = allocs.stream().filter(a -> !a.onTime()).mapToInt(a -> a.units).sum();
        int unfilledUnits = unfilled.values().stream().mapToInt(Integer::intValue).sum();

        // Penalty: units that miss their channel's promise window, and units never filled.
        long penalty = 0;
        for (Alloc a : allocs) if (!a.onTime()) penalty += Math.round(a.units * a.penaltyPerUnit);
        for (DemandLine d : demand) {
            Integer miss = unfilled.get(key(d.sku(), d.city(), d.channel()));
            if (miss != null) penalty += Math.round(miss * d.penaltyPerUnit());
        }

        // Freight + handling, aggregated per source x city.
        Map<String, SourceAgg> srcAgg = new LinkedHashMap<>();
        long handling = 0;
        for (Alloc a : allocs) {
            Supply sup = supplies.stream().filter(x -> x.name().equals(a.source)).findFirst().orElse(null);
            if (sup == null) continue;
            double kg = a.units * unitWeightKg(a.sku);
            double rate = sup.ratePerKgByCity().getOrDefault(a.city, 8.0);
            SourceAgg agg = srcAgg.computeIfAbsent(a.source + "|" + a.city,
                    k -> new SourceAgg(a.source, a.kind, a.city, sup.etaByCity().get(a.city)));
            agg.units += a.units;
            agg.freight += kg * rate;
            if ("RDC".equals(a.kind)) handling += Math.round(a.units * HANDLING_PER_UNIT);
        }
        long freight = srcAgg.values().stream().mapToLong(x -> Math.round(x.freight)).sum();

        List<SourceLine> sources = srcAgg.values().stream()
                .sorted(Comparator.comparingDouble((SourceAgg x) -> x.etaHrs))
                .map(x -> new SourceLine(x.source, x.kind, x.city, x.units, round1(x.etaHrs), Math.round(x.freight)))
                .toList();

        List<ChannelFill> channelTotals = channelRollup(demand, allocs, unfilled, null);
        List<CityPlan> cities = new ArrayList<>();
        for (String city : destinations(s)) {
            List<ChannelFill> ch = channelRollup(demand, allocs, unfilled, city);
            int dem = demand.stream().filter(d -> d.city().equals(city)).mapToInt(DemandLine::units).sum();
            int ot = allocs.stream().filter(a -> a.city.equals(city) && a.onTime()).mapToInt(a -> a.units).sum();
            double eta = allocs.stream().filter(a -> a.city.equals(city))
                    .mapToDouble(a -> a.etaHrs).max().orElse(0);
            cities.add(new CityPlan(city, dem, ot, pct(ot, dem), round1(eta), ch));
        }

        List<SkuPlan> skuPlans = new ArrayList<>();
        for (Sku sku : erp.skus()) {
            int dem = demand.stream().filter(d -> d.sku().equals(sku.code())).mapToInt(DemandLine::units).sum();
            if (dem == 0) continue;
            int rdc = allocs.stream().filter(a -> a.sku.equals(sku.code()) && "RDC".equals(a.kind)).mapToInt(a -> a.units).sum();
            int truck = allocs.stream().filter(a -> a.sku.equals(sku.code()) && "TRUCK".equals(a.kind)).mapToInt(a -> a.units).sum();
            int miss = unfilled.entrySet().stream().filter(e -> e.getKey().startsWith(sku.code() + "|"))
                    .mapToInt(Map.Entry::getValue).sum();
            int ot = allocs.stream().filter(a -> a.sku.equals(sku.code()) && a.onTime()).mapToInt(a -> a.units).sum();
            skuPlans.add(new SkuPlan(sku.code(), sku.name(), dem, rdc, truck, miss, pct(ot, dem)));
        }

        // Backfill: what each RDC lent, and whether the recovered load can repay it.
        List<Backfill> backfills = new ArrayList<>();
        Supply truck = supplies.stream().filter(x -> "TRUCK".equals(x.kind())).findFirst().orElse(null);
        int truckSurplus = truck == null ? 0
                : pool.get(truck.name()).values().stream().mapToInt(Integer::intValue).sum();
        for (Supply sup : supplies) {
            if (!"RDC".equals(sup.kind())) continue;
            int lent = allocs.stream().filter(a -> a.source.equals(sup.name())).mapToInt(a -> a.units).sum();
            if (lent == 0) continue;
            int repaid = Math.min(lent, truckSurplus);
            truckSurplus -= repaid;
            String note = repaid >= lent
                    ? "Fully repaid from the recovered consignment (it is already at the RDC gate) - no extra freight."
                    : repaid > 0
                    ? "Partly repaid from the recovered load; raise an STO on the mother WH for the balance."
                    : "Not repaid - RDC runs on its retained cover until its inbound lands.";
            backfills.add(new Backfill(sup.name(), lent, repaid, 0, note));
        }

        long total = freight + penalty + handling;
        String summary = summarise(strategyId, s, totalDemand, onTimeUnits, unfilledUnits, channelTotals,
                sources, backfills, freight, penalty, total);

        return new FulfilmentPlan(strategyId, totalDemand, onTimeUnits, lateUnits, unfilledUnits,
                pct(onTimeUnits, totalDemand), freight, penalty, handling, total,
                cities, channelTotals, skuPlans, sources, backfills, summary);
    }

    private static final class SourceAgg {
        final String source, kind, city;
        final double etaHrs;
        int units;
        double freight;
        SourceAgg(String source, String kind, String city, double etaHrs) {
            this.source = source; this.kind = kind; this.city = city; this.etaHrs = etaHrs;
        }
    }

    /** Roll allocations up per channel (optionally filtered to one city). */
    private List<ChannelFill> channelRollup(List<DemandLine> demand, List<Alloc> allocs,
                                            Map<String, Integer> unfilled, String cityOrNull) {
        List<ChannelFill> out = new ArrayList<>();
        for (SalesChannel c : erp.salesChannels()) {
            int dem = demand.stream()
                    .filter(d -> d.channel().equals(c.code()))
                    .filter(d -> cityOrNull == null || d.city().equals(cityOrNull))
                    .mapToInt(DemandLine::units).sum();
            if (dem == 0) continue;
            List<Alloc> mine = allocs.stream()
                    .filter(a -> a.channel.equals(c.code()))
                    .filter(a -> cityOrNull == null || a.city.equals(cityOrNull))
                    .toList();
            int ot = mine.stream().filter(Alloc::onTime).mapToInt(a -> a.units).sum();
            int late = mine.stream().filter(a -> !a.onTime()).mapToInt(a -> a.units).sum();
            int miss = unfilled.entrySet().stream()
                    .filter(e -> e.getKey().endsWith("|" + c.code()))
                    .filter(e -> cityOrNull == null || e.getKey().split("\\|")[1].equals(cityOrNull))
                    .mapToInt(Map.Entry::getValue).sum();
            double eta = mine.stream().mapToDouble(a -> a.etaHrs).max().orElse(0);
            long pen = Math.round(mine.stream().filter(a -> !a.onTime())
                    .mapToDouble(a -> a.units * a.penaltyPerUnit).sum()
                    + miss * c.penaltyPerUnitInr());
            int fill = pct(ot, dem);
            String verdict = fill >= 100 ? "Fully served inside promise"
                    : fill >= 60 ? "Partly served - shortfall slips to the next wave"
                    : fill > 0 ? "Largely missed - escalate to the channel owner"
                    : "Not served in window - notify and re-promise";
            out.add(new ChannelFill(c.code(), c.name(), dem, ot, late, miss, fill, round1(eta),
                    c.maxDelayHrs(), pen, verdict));
        }
        out.sort(Comparator.comparingInt(x -> channelPriority(x.channel())));
        return out;
    }

    // ── the written summary for each choice ──

    private String summarise(String strategyId, Shipment s, int totalDemand, int onTime, int unfilledUnits,
                             List<ChannelFill> channels, List<SourceLine> sources,
                             List<Backfill> backfills, long freight, long penalty, long total) {
        StringBuilder b = new StringBuilder();
        int fill = pct(onTime, totalDemand);
        b.append(String.format("Serves %s of %s units (%d%%) inside their channel promise windows. ",
                fmt(onTime), fmt(totalDemand), fill));

        List<String> good = channels.stream().filter(c -> c.fillPct() >= 100)
                .map(ChannelFill::channel).toList();
        List<String> bad = channels.stream().filter(c -> c.fillPct() < 100)
                .map(c -> c.channel() + " " + c.fillPct() + "%").toList();
        if (!good.isEmpty()) b.append("Fully protected: ").append(String.join(", ", good)).append(". ");
        if (!bad.isEmpty()) b.append("Exposed: ").append(String.join(", ", bad)).append(". ");

        if (!sources.isEmpty()) {
            String srcTxt = sources.stream()
                    .map(x -> String.format("%s -> %s (%s units, %sh)", x.source(), x.city(), fmt(x.units()), fmt(x.etaHrs())))
                    .reduce((x, y) -> x + "; " + y).orElse("");
            b.append("Sourcing: ").append(srcTxt).append(". ");
        }
        if (unfilledUnits > 0) {
            b.append(String.format("%s units cannot be covered in this window and roll to the next replenishment. ",
                    fmt(unfilledUnits)));
        }
        for (Backfill bf : backfills) {
            b.append(String.format("%s lends %s units; %s ", bf.rdc(), fmt(bf.unitsLent()), bf.note()));
        }
        b.append(String.format("Cost: INR %s freight + INR %s service penalty = INR %s all-in.",
                fmt(freight), fmt(penalty), fmt(total)));
        return b.toString();
    }

    // ── helpers ──

    public List<String> destinations(Shipment s) {
        if (s.destinationCities() != null && !s.destinationCities().isEmpty()) return s.destinationCities();
        return s.destination() == null ? List.of() : List.of(s.destination());
    }

    private List<DemandLine> demandLines(Shipment s) {
        List<DemandLine> out = new ArrayList<>();
        for (LoadLine l : safe(s.loadLines())) {
            if (l.channels() == null) continue;
            for (Map.Entry<String, Integer> e : l.channels().entrySet()) {
                if (e.getValue() == null || e.getValue() <= 0) continue;
                SalesChannel c = erp.findChannel(e.getKey()).orElse(null);
                double pen = c != null ? c.penaltyPerUnitInr() : 5;
                double promise = c != null ? c.maxDelayHrs() : 48;
                int prio = c != null ? c.priority() : 9;
                out.add(new DemandLine(l.sku(), l.city(), e.getKey(), e.getValue(), pen, promise, prio));
            }
        }
        return out;
    }

    private int channelPriority(String code) {
        return erp.findChannel(code).map(SalesChannel::priority).orElse(9);
    }

    private double unitWeightKg(String sku) {
        return erp.findSku(sku).map(Sku::unitWeightKg).orElse(0.2667);
    }

    private static int lineUnits(LoadLine l) {
        if (l.channels() == null) return 0;
        return l.channels().values().stream().filter(java.util.Objects::nonNull).mapToInt(Integer::intValue).sum();
    }

    private static String key(String sku, String city, String channel) {
        return sku + "|" + city + "|" + channel;
    }

    private static int pct(int part, int whole) {
        return whole <= 0 ? 0 : (int) Math.round(100.0 * part / whole);
    }

    private static <T> List<T> safe(List<T> l) {
        return l == null ? List.of() : l;
    }

    private static double round1(double v) { return Math.round(v * 10.0) / 10.0; }

    private static double round2(double v) { return Math.round(v * 100.0) / 100.0; }

    private static String fmt(double v) {
        if (v == Math.floor(v) && !Double.isInfinite(v)) return String.format("%,d", (long) v);
        return String.format("%,.1f", v);
    }
}
