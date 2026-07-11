package com.cscen.forum.service;

import com.cscen.forum.service.agents.ExternalSignals;
import com.cscen.forum.service.agents.ExternalSignals.EwayBillSignal;
import com.cscen.forum.service.agents.ExternalSignals.FestivalSignal;
import com.cscen.forum.service.agents.ExternalSignals.WeatherSignal;
import com.cscen.forum.service.erp.ErpPort;
import com.cscen.forum.service.erp.ErpPort.Advisory;
import com.cscen.forum.service.erp.ErpPort.Carrier;
import com.cscen.forum.service.erp.ErpPort.City;
import com.cscen.forum.service.erp.ErpPort.DistributionCentre;
import com.cscen.forum.service.erp.ErpPort.Lane;
import com.cscen.forum.service.erp.ErpPort.ServicePoint;
import com.cscen.forum.service.erp.ErpPort.Shipment;
import com.cscen.forum.service.erp.ErpPort.Vehicle;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The "control tower" decision engine. Given a disrupted shipment it runs twelve
 * role-based specialist agents over the ERP ({@link ErpPort}) and live external
 * signals ({@link ExternalSignals}), generates every applicable recovery strategy,
 * and ranks them by <b>risk-adjusted expected landed cost</b> — pricing transport,
 * the expected SLA penalty, stock-out and spoilage exposure, and CO2, then adding a
 * priority-weighted variance term so important cargo is treated risk-averse.
 *
 * <p>All numbers are computed deterministically (nothing hallucinated); the LLM only
 * writes narrative, and a templated narrative is used when it's unavailable.
 */
@Service
public class AgentOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(AgentOrchestrator.class);

    // Transparent physics / cost model.
    private static final double SPEED_KMPH = 45.0;
    private static final double RAIL_KMPH = 38.0;
    private static final double INTERNAL_COST_PER_KM = 24.0;
    private static final double RAIL_COST_PER_KM = 11.0;
    private static final double AIR_COST_PER_KM = 130.0;
    private static final double LOAD_TRANSFER_HRS = 2.5;
    private static final int OWN_FLEET_RELIABILITY = 90;
    private static final int REPAIR_RELIABILITY = 66;
    private static final double CO2_TRUCK = 0.85, CO2_RAIL = 0.22, CO2_AIR = 2.4; // kg/km
    private static final double CARBON_PRICE = 2.0;   // INR per kg CO2
    private static final double LAMBDA = 0.5;         // risk-aversion coefficient (ETA-variance term)
    // Expected cost of an option failing its commitment (relationship damage + expedite
    // recovery), as a fraction of cargo value at full failure. Scaled by priority so a
    // key-account / CRITICAL load won't be gambled to shave transport cost.
    private static final double SERVICE_RISK_FRACTION = 0.06;

    // How many discrete factors the full catalog spans (surfaced selectively per run).
    private static final int FACTOR_CATALOG_SIZE = 118;

    private final ErpPort erp;
    private final ExternalSignals signals;
    private final AgentAiClient ai;
    private final ObjectMapper mapper;

    public AgentOrchestrator(ErpPort erp, ExternalSignals signals, AgentAiClient ai, ObjectMapper mapper) {
        this.erp = erp;
        this.signals = signals;
        this.ai = ai;
        this.mapper = mapper;
    }

    // ── output DTOs (serialized to the frontend) ──

    /** source ∈ LIVE|MODELED|COMPUTED|ROADMAP ; impact ∈ INFO|GOOD|CAUTION|CRITICAL */
    public record Factor(String code, String label, String value, String source, String impact) {}

    public record AgentReport(String name, String role, String emoji, String domain,
                              String headline, List<Factor> factors) {}

    public record CostLine(String label, long amount) {}

    public record OptionView(String id, String title, String type, String provider,
                             double etaHours, boolean onTime, int onTimeProbPct, int reliabilityPct,
                             long expectedCost, List<CostLine> costBreakdown, double co2Kg,
                             int score, boolean recommended,
                             List<String> pros, List<String> cons, List<String> risks) {}

    public record SignalView(String kind, String label, String detail, String source, String impact) {}

    public record ScenarioView(String shipmentId, String ref, String cargo, double tons, String route,
                               String origin, String destination, String priority, long value,
                               double remainingKm, double slaHoursRemaining, String vehiclePlate,
                               String disruption, String customerTier, String summary) {}

    public record Evidence(String label, String value, String source) {}

    public record Recommendation(String optionId, String title, String rationale, List<Evidence> evidence) {}

    public record AgentRun(ScenarioView scenario, List<SignalView> signals, List<AgentReport> agents,
                           List<OptionView> options, Recommendation recommendation,
                           int factorsConsidered, boolean aiPowered, String aiProvider) {}

    public boolean isAiEnabled() { return ai.isEnabled(); }

    public String aiProvider() { return ai.provider(); }

    // ── mutable working draft ──

    private static final class Draft {
        String id, title, type, provider;
        double etaMean, etaSigma;
        int reliability;
        double co2Kg;
        long transport;
        // computed by scoring:
        double pOnTime, eLate, costSigma, riskAdjusted;
        long penaltyEV, stockoutEV, spoilageEV, serviceRisk, co2Cost, expectedCost;
        boolean onTime;
        int score;
        boolean reefer = false;
        List<String> pros = new ArrayList<>();
        List<String> cons = new ArrayList<>();
        List<String> risks = new ArrayList<>();
    }

    /** Gathered context for a run — read once, shared by every agent. */
    private record Ctx(Shipment s, Vehicle veh, Lane lane, City origin, City dest, DistributionCentre dc,
                       ServicePoint originService, WeatherSignal wxOrigin, WeatherSignal wxDest,
                       FestivalSignal fest, EwayBillSignal eway, List<Advisory> advisories,
                       double extDelay, double extSigma, double base, double daysOfSupply,
                       int advisorySevPenalty, String worstCondition, String worstWxSource, double laneKm) {}

    // ── main entry ──

    public AgentRun run(String shipmentId, String disruptionInput) {
        Shipment s = erp.findShipment(shipmentId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown shipment " + shipmentId));

        String disruption = (disruptionInput == null || disruptionInput.isBlank())
                ? (s.breakdownIssue() != null ? "Vehicle breakdown" : "Recovery review")
                : disruptionInput.trim();

        Ctx ctx = gather(s);
        List<Draft> drafts = buildStrategies(ctx);
        scoreAndRank(ctx, drafts);

        List<Draft> top = drafts.stream().limit(4).toList();
        List<OptionView> options = new ArrayList<>();
        for (int i = 0; i < top.size(); i++) {
            options.add(toView(top.get(i), i == 0));
        }
        Draft best = top.get(0);

        ScenarioView scenario = buildScenario(ctx, disruption);
        List<SignalView> sig = buildSignals(ctx);
        List<AgentReport> agents = buildAgents(ctx, drafts, top);
        List<Evidence> evidence = buildEvidence(ctx, best);

        // Templated narrative first (always works), then enrich with AI.
        String rationale = templatedRationale(ctx, best);
        boolean aiPowered = false;
        Optional<Narrative> ai2 = ai.isEnabled() ? aiNarrative(ctx, scenario, options, best) : Optional.empty();
        if (ai2.isPresent()) {
            rationale = or(ai2.get().rationale(), rationale);
            agents = applyNarrative(agents, ai2.get());
            aiPowered = true;
        }

        Recommendation rec = new Recommendation(best.id, best.title, rationale, evidence);
        return new AgentRun(scenario, sig, agents, options, rec, FACTOR_CATALOG_SIZE, aiPowered, aiProvider());
    }

    // ── 1. gather context (ERP + live signals) ──

    private Ctx gather(Shipment s) {
        Vehicle veh = erp.findVehicle(s.vehicleId()).orElse(null);
        Lane lane = erp.findLane(s.route()).orElse(null);
        double laneKm = lane != null ? lane.distanceKm() : s.remainingKm();
        City origin = erp.findCity(s.origin()).orElse(null);
        City dest = erp.findCity(s.destination()).orElse(null);
        DistributionCentre dc = erp.findDc(s.destination()).orElse(null);
        ServicePoint originService = erp.findServicePoint(s.origin()).orElse(null);

        WeatherSignal wxOrigin = signals.weather(origin);
        WeatherSignal wxDest = signals.weather(dest);
        FestivalSignal fest = signals.nextFestival();
        EwayBillSignal eway = signals.eWayBill(laneKm, s.eWayBillIssuedHoursAgo());
        List<Advisory> advisories = erp.advisoriesForRoute(s.route());

        double advisoryDelay = advisories.stream().mapToDouble(Advisory::delayHrs).sum();
        int advisorySevPenalty = Math.min(15, advisories.stream().mapToInt(a -> severityPenalty(a.severity())).sum());

        // Worst weather segment drives the delay + uncertainty.
        WeatherSignal worst = wxOrigin.delayHrs() >= wxDest.delayHrs() ? wxOrigin : wxDest;
        double wxDelay = worst.delayHrs();
        double festDelay = fest.active() ? fest.delayHrs() : 0.0;
        double extDelay = wxDelay + festDelay + advisoryDelay;

        double extSigma = worst.sigmaHrs()
                + (lane != null && lane.ghatSection() ? 0.8 : 0.0)
                + (advisories.stream().anyMatch(a -> "HIGH".equalsIgnoreCase(a.severity())) ? 1.5 : 0.0);
        double base = s.remainingKm() / SPEED_KMPH;
        double daysOfSupply = dc != null ? (double) dc.onHandUnits() / Math.max(1, dc.dailyDemandUnits()) : 99;

        return new Ctx(s, veh, lane, origin, dest, dc, originService, wxOrigin, wxDest, fest, eway,
                advisories, extDelay, extSigma, base, daysOfSupply, advisorySevPenalty,
                worst.condition(), worst.source(), laneKm);
    }

    // ── 2. generate applicable recovery strategies ──

    private List<Draft> buildStrategies(Ctx ctx) {
        Shipment s = ctx.s();
        List<Draft> drafts = new ArrayList<>();
        char letter = 'A';

        // A) External carriers on this lane
        for (String cid : safe(s.candidateCarrierIds())) {
            Carrier c = erp.findCarrier(cid).orElse(null);
            if (c == null) continue;
            Draft d = new Draft();
            d.id = String.valueOf(letter++);
            d.type = "EXTERNAL_CARRIER";
            d.provider = c.name();
            d.title = "Reassign to " + c.name();
            d.etaMean = ctx.base() + c.avgResponseHrs() + ctx.extDelay();
            d.etaSigma = ctx.extSigma() + ctx.base() * 0.06 + (c.spot() ? 1.2 : 0.6) + (100 - c.reliabilityPct()) * 0.03;
            d.transport = Math.round(s.remainingKm() * c.costPerKm()) + (ctx.lane() != null ? ctx.lane().tollInr() : 0);
            d.reliability = clampRel(c.reliabilityPct() - ctx.advisorySevPenalty());
            d.co2Kg = round1(s.remainingKm() * CO2_TRUCK);
            d.reefer = c.name().toLowerCase().contains("tci"); // TCI is reefer-capable in the dataset
            d.pros.add("Covers the " + routeLabel(s.route()) + " lane");
            d.pros.add("Mobilises in ~" + c.avgResponseHrs() + "h");
            if (c.reliabilityPct() >= 90) d.pros.add("Strong SLA record (" + c.reliabilityPct() + "%)");
            d.cons.add("Rate INR " + fmt(Math.round(c.costPerKm())) + "/km + toll");
            if (!c.compliant()) { d.risks.add(c.name() + " has a compliance lapse (PUC/permit)"); d.reliability = clampRel(d.reliability - 6); }
            if (c.reliabilityPct() < 80) d.risks.add(c.name() + " has a variable service record");
            drafts.add(d);
        }

        // B) Reposition a nearby own vehicle / deploy a co-located standby
        if (s.nearbySpare() != null) {
            var sp = s.nearbySpare();
            Vehicle spareVeh = erp.findVehicle(sp.vehicleId()).orElse(null);
            boolean standby = sp.repositionKm() <= 1;
            double repositionHrs = sp.repositionKm() / SPEED_KMPH;
            double transferHrs = standby ? 0.5 : LOAD_TRANSFER_HRS;
            Draft d = new Draft();
            d.id = String.valueOf(letter++);
            d.type = "OWN_FLEET";
            d.provider = "Own fleet " + sp.vehicleId();
            d.title = standby ? "Deploy standby unit " + sp.vehicleId()
                    : "Reposition own unit " + sp.vehicleId() + " from " + sp.fromCity();
            d.etaMean = repositionHrs + transferHrs + ctx.base() + ctx.extDelay();
            d.etaSigma = ctx.extSigma() + ctx.base() * 0.05 + 0.5;
            d.transport = Math.round((sp.repositionKm() + s.remainingKm()) * INTERNAL_COST_PER_KM)
                    + (ctx.lane() != null ? ctx.lane().tollInr() : 0);
            d.reliability = clampRel(OWN_FLEET_RELIABILITY - ctx.advisorySevPenalty());
            d.co2Kg = round1((sp.repositionKm() + s.remainingKm()) * CO2_TRUCK);
            d.reefer = spareVeh != null && spareVeh.reefer();
            d.pros.add("No external markup - lowest running cost");
            d.pros.add("Full visibility & control (own fleet)");
            if (!standby) {
                d.cons.add("Repositioning leg of " + fmt(Math.round(sp.repositionKm())) + " km");
                d.risks.add("Cross-dock transfer at breakdown site (+" + LOAD_TRANSFER_HRS + "h)");
            } else {
                d.pros.add("Co-located - no repositioning deadhead");
            }
            drafts.add(d);
        }

        // C) Repair on site & continue
        if (s.breakdownIssue() != null) {
            String issue = s.breakdownIssue().toLowerCase();
            boolean heavy = issue.contains("gearbox") || issue.contains("axle") || issue.contains("engine");
            double repairHrs = heavy ? 7.0 : 3.5;
            long repairCost = heavy ? 28000L : 9000L;
            boolean canHeavy = ctx.originService() != null && ctx.originService().heavyRepair();
            Draft d = new Draft();
            d.id = String.valueOf(letter++);
            d.type = "REPAIR";
            String plate = ctx.veh() != null ? ctx.veh().plate() : s.vehicleId();
            d.provider = "On-site repair - " + plate;
            d.title = "Repair " + plate + " & continue";
            if (heavy && !canHeavy) {
                repairHrs += 4; repairCost += 15000;
                d.risks.add("No heavy-repair bay at " + s.origin() + " - tow / mobile crew needed");
            }
            d.etaMean = repairHrs + ctx.base() + ctx.extDelay();
            d.etaSigma = ctx.extSigma() + repairHrs * 0.25 + ctx.base() * 0.05;
            d.transport = repairCost;
            d.reliability = clampRel(REPAIR_RELIABILITY - ctx.advisorySevPenalty());
            d.co2Kg = round1(s.remainingKm() * CO2_TRUCK);
            d.pros.add("Keeps the original consignment intact - no transfer");
            d.cons.add("~" + fmt(round1(repairHrs)) + "h stationary while repairing");
            d.risks.add("Same unit - residual risk of repeat failure");
            drafts.add(d);
        }

        // D) Reroute around an active HIGH blockage (alternate corridor)
        boolean highBlock = ctx.advisories().stream()
                .anyMatch(a -> "BLOCKAGE".equalsIgnoreCase(a.type()) && "HIGH".equalsIgnoreCase(a.severity()));
        if (highBlock && ctx.lane() != null && ctx.lane().altRouteKm() > 0) {
            double blockDelay = ctx.advisories().stream()
                    .filter(a -> "BLOCKAGE".equalsIgnoreCase(a.type()))
                    .mapToDouble(Advisory::delayHrs).sum();
            double extraKm = Math.max(0, ctx.lane().altRouteKm() - ctx.laneKm());
            double extraHrs = extraKm / SPEED_KMPH;
            double extDelayNoBlock = ctx.extDelay() - blockDelay;
            Draft d = new Draft();
            d.id = String.valueOf(letter++);
            d.type = "ROUTE_CHANGE";
            d.provider = "Alternate corridor";
            d.title = "Reroute via alternate corridor (avoid blockage)";
            d.etaMean = ctx.base() + extraHrs + 3.0 /*mobilise*/ + Math.max(0, extDelayNoBlock);
            d.etaSigma = ctx.extSigma() + ctx.base() * 0.06 + 0.8;
            d.transport = Math.round((s.remainingKm() + extraKm) * INTERNAL_COST_PER_KM);
            d.reliability = clampRel(OWN_FLEET_RELIABILITY - 4);
            d.co2Kg = round1((s.remainingKm() + extraKm) * CO2_TRUCK);
            d.pros.add("Sidesteps the " + fmt(round1(blockDelay)) + "h blockage delay");
            d.cons.add("+" + fmt(Math.round(extraKm)) + " km on the detour");
            drafts.add(d);
        }

        // E) Mode-shift to rail (cheaper + far lower CO2, slower)
        if (ctx.lane() != null && ctx.lane().hasRail()) {
            Draft d = new Draft();
            d.id = String.valueOf(letter++);
            d.type = "MODE_RAIL";
            d.provider = "Intermodal rail";
            d.title = "Mode-shift to rail";
            d.etaMean = ctx.lane().railTerminalHrs() + ctx.laneKm() / RAIL_KMPH + ctx.extDelay() * 0.3;
            d.etaSigma = ctx.extSigma() * 0.6 + 1.2;
            d.transport = Math.round(ctx.laneKm() * RAIL_COST_PER_KM) + 6000; // handling both ends
            d.reliability = clampRel(82);
            d.co2Kg = round1(ctx.laneKm() * CO2_RAIL);
            d.pros.add("Lowest cost & CO2 of all options");
            d.cons.add("Terminal handling adds ~" + fmt(round1(ctx.lane().railTerminalHrs())) + "h each end");
            d.risks.add("Rake availability not guaranteed same-day");
            drafts.add(d);
        }

        // F) Mode-shift to air (for tight, high-value, lighter loads)
        if (ctx.lane() != null && ctx.lane().hasAir() && s.tons() <= 10
                && ("CRITICAL".equalsIgnoreCase(s.priority()) || s.slaHoursRemaining() < ctx.base())) {
            Draft d = new Draft();
            d.id = String.valueOf(letter++);
            d.type = "MODE_AIR";
            d.provider = "Air freight";
            d.title = "Expedite by air";
            d.etaMean = ctx.lane().airCutoffHrs() + ctx.laneKm() / 550.0 + 3.0 /*last mile*/;
            d.etaSigma = 0.8;
            d.transport = Math.round(s.remainingKm() * AIR_COST_PER_KM);
            d.reliability = clampRel(88);
            d.co2Kg = round1(ctx.laneKm() * CO2_AIR);
            d.pros.add("Fastest possible recovery");
            d.cons.add("Steep expedite premium");
            d.risks.add("High CO2 - only for genuinely critical cargo");
            drafts.add(d);
        }

        // G) Re-fulfill from an alternate DC (don't recover the truck) - only if the
        //    destination is low on stock and there's a nearer source.
        if (ctx.dc() != null && ctx.dc().altSourceCity() != null && ctx.daysOfSupply() < 1.5) {
            Draft d = new Draft();
            d.id = String.valueOf(letter++);
            d.type = "ALT_DC";
            d.provider = "Alt DC " + ctx.dc().altSourceCity();
            d.title = "Re-fulfill from " + ctx.dc().altSourceCity() + " DC";
            d.etaMean = ctx.dc().altSourceLeadHrs() + ctx.extDelay() * 0.4;
            d.etaSigma = ctx.extSigma() * 0.7 + 0.8;
            d.transport = Math.round(ctx.dc().altSourceLeadHrs() * SPEED_KMPH * INTERNAL_COST_PER_KM * 0.7);
            d.reliability = clampRel(85);
            d.co2Kg = round1(ctx.dc().altSourceLeadHrs() * SPEED_KMPH * CO2_TRUCK);
            d.pros.add("Serves demand without waiting on the stranded truck");
            d.cons.add("Original consignment still needs recovery separately");
            drafts.add(d);
        }

        return drafts;
    }

    // ── 3. price + rank every option (risk-adjusted expected landed cost) ──

    private void scoreAndRank(Ctx ctx, List<Draft> drafts) {
        Shipment s = ctx.s();
        double priorityWeight = priorityWeight(s.priority());

        for (Draft d : drafts) {
            double z = (s.slaHoursRemaining() - d.etaMean) / Math.max(0.3, d.etaSigma);
            d.pOnTime = clampP(Phi(z));
            d.onTime = d.etaMean <= s.slaHoursRemaining();

            // Expected lateness E[max(0, ETA - SLA)] under N(etaMean, etaSigma).
            double diff = d.etaMean - s.slaHoursRemaining();
            double zc = diff / Math.max(0.3, d.etaSigma);
            d.eLate = Math.max(0, diff * Phi(zc) + d.etaSigma * phi(zc));

            d.penaltyEV = Math.round(Math.min(s.penaltyCapInr(), s.penaltyPerHourInr() * d.eLate));

            // Service-risk: expected cost of the option failing its commitment. Both the ETA
            // (on-time odds) and the carrier/mode (reliability) have to go right, so combine
            // them. Scaled by priority + value, so a HIGH/CRITICAL load is not gambled to save
            // transport rupees, but a routine load still lets the cheap option win.
            double pFail = Math.max(0, 1 - d.pOnTime * (d.reliability / 100.0));
            d.serviceRisk = Math.round(pFail * priorityWeight * s.value() * SERVICE_RISK_FRACTION);

            // Stock-out exposure: goods arriving after the destination runs dry.
            long stockout = 0;
            if (ctx.dc() != null && s.destination().equalsIgnoreCase(ctx.dc().city())) {
                double arrivalDays = d.etaMean / 24.0;
                double shortfall = arrivalDays - ctx.daysOfSupply();
                if (shortfall > 0) {
                    double pStockout = clamp01(shortfall / 0.5);
                    stockout = Math.round(pStockout * s.value() * 0.15);
                }
            }
            d.stockoutEV = stockout;

            // Spoilage exposure: perishable cargo past shelf life, or a non-reefer mover.
            long spoil = 0;
            if (s.perishable() || s.tempControlled()) {
                boolean reeferOk = d.reefer || d.type.equals("REPAIR") || d.type.equals("MODE_AIR");
                if (s.tempControlled() && !reeferOk) {
                    spoil = Math.round(s.value() * 0.30);
                    d.risks.add("Not reefer-capable - cold-chain break risk");
                } else if (s.shelfLifeHrs() > 0 && d.etaMean > s.shelfLifeHrs()) {
                    double pSpoil = clamp01((d.etaMean - s.shelfLifeHrs()) / 6.0);
                    spoil = Math.round(pSpoil * s.value() * 0.5);
                }
            }
            d.spoilageEV = spoil;

            d.co2Cost = Math.round(d.co2Kg * CARBON_PRICE);
            d.expectedCost = d.transport + d.penaltyEV + d.serviceRisk + d.stockoutEV + d.spoilageEV + d.co2Cost;

            // Cost variance is dominated by penalty risk (ETA uncertainty) + spoilage tail.
            d.costSigma = s.penaltyPerHourInr() * d.etaSigma + d.spoilageEV * 0.5;
            d.riskAdjusted = d.expectedCost + LAMBDA * priorityWeight * d.costSigma;

            if (!d.onTime) {
                d.risks.add(0, "Projected to MISS SLA by ~" + fmt(round1(diff)) + "h");
            } else if (s.slaHoursRemaining() - d.etaMean < 2) {
                d.risks.add("Tight - only " + fmt(round1(s.slaHoursRemaining() - d.etaMean)) + "h of slack");
            }
        }

        drafts.sort((a, b) -> Double.compare(a.riskAdjusted, b.riskAdjusted));

        // Display score 30..95, best first, order-consistent with risk-adjusted cost.
        double min = drafts.stream().mapToDouble(x -> x.riskAdjusted).min().orElse(0);
        double max = drafts.stream().mapToDouble(x -> x.riskAdjusted).max().orElse(1);
        for (Draft d : drafts) {
            double norm = max == min ? 0 : (d.riskAdjusted - min) / (max - min);
            d.score = (int) Math.round(30 + 65 * (1 - norm));
        }

        // Fold the live advisories into every option's risk list so the trade-off is explicit.
        for (Draft d : drafts) {
            for (Advisory a : ctx.advisories()) d.risks.add(advisoryLabel(a));
            d.risks = dedupe(d.risks);
        }
    }

    private OptionView toView(Draft d, boolean recommended) {
        List<CostLine> breakdown = new ArrayList<>();
        breakdown.add(new CostLine("Transport / execution", d.transport));
        if (d.penaltyEV > 0) breakdown.add(new CostLine("Expected SLA penalty", d.penaltyEV));
        if (d.stockoutEV > 0) breakdown.add(new CostLine("Stock-out exposure", d.stockoutEV));
        if (d.spoilageEV > 0) breakdown.add(new CostLine("Spoilage / cold-chain", d.spoilageEV));
        if (d.serviceRisk > 0) breakdown.add(new CostLine("Service-risk (miss x unreliability)", d.serviceRisk));
        if (d.co2Cost > 0) breakdown.add(new CostLine("Carbon cost", d.co2Cost));
        return new OptionView(d.id, d.title, d.type, d.provider, round1(d.etaMean), d.onTime,
                (int) Math.round(d.pOnTime * 100), d.reliability, d.expectedCost, breakdown, d.co2Kg,
                d.score, recommended, d.pros, d.cons, d.risks);
    }

    // ── 4. build the twelve specialist agent reports ──

    private List<AgentReport> buildAgents(Ctx ctx, List<Draft> all, List<Draft> top) {
        Shipment s = ctx.s();
        Draft best = top.get(0);
        Draft cheapest = all.stream().min((a, b) -> Long.compare(a.transport, b.transport)).orElse(best);
        Draft greenest = all.stream().min((a, b) -> Double.compare(a.co2Kg, b.co2Kg)).orElse(best);

        List<AgentReport> out = new ArrayList<>();

        // 1 · Cargo & Order
        out.add(new AgentReport("Cargo & Order Analyst", "Prices what's at risk and how hard the SLA bites", "📦", "Cargo",
                String.format("%.0ft of %s worth INR %s for a %s customer; %sh of SLA and INR %s/h in penalties on the line.",
                        s.tons(), s.cargo(), fmt(s.value()), tierLabel(s.customerTier()), fmt(s.slaHoursRemaining()), fmt(s.penaltyPerHourInr())),
                List.of(
                        f("VALUE", "Cargo value at risk", "INR " + fmt(s.value()), "MODELED", "INFO"),
                        f("PRIO", "Priority / SKU class", s.priority() + " · class " + s.skuCriticality(), "MODELED", impactForPriority(s.priority())),
                        f("SLA", "SLA window remaining", fmt(s.slaHoursRemaining()) + "h", "MODELED", s.slaHoursRemaining() < 12 ? "CAUTION" : "INFO"),
                        f("PEN", "Penalty exposure", "INR " + fmt(s.penaltyPerHourInr()) + "/h, cap " + fmt(s.penaltyCapInr()), "MODELED", "CAUTION"),
                        f("PERISH", "Perishability", s.tempControlled() ? "temp-controlled, " + fmt(s.shelfLifeHrs()) + "h shelf life" : "ambient / non-perishable", "MODELED", s.tempControlled() ? "CAUTION" : "INFO")
                )));

        // 2 · Asset & Vehicle
        Vehicle v = ctx.veh();
        boolean down = v != null && "BREAKDOWN".equalsIgnoreCase(v.status());
        out.add(new AgentReport("Asset & Vehicle Engineer", "Triages the failed unit and nearby iron", "🔧", "Asset",
                v == null ? "Assigned unit not found in fleet master." :
                        String.format("%s (%s) is %s%s. %s",
                                v.plate(), v.type(), v.status(),
                                down ? " - " + s.breakdownIssue() : "",
                                s.nearbySpare() != null ? "Spare " + s.nearbySpare().vehicleId() + " is " + (s.nearbySpare().repositionKm() <= 1 ? "co-located." : fmt(Math.round(s.nearbySpare().repositionKm())) + " km away.") : "No idle spare in range."),
                listOf(
                        v == null ? null : f("STATUS", "Assigned vehicle", v.plate() + " · " + v.status(), "MODELED", down ? "CRITICAL" : "GOOD"),
                        down ? f("CAUSE", "Failure mode", s.breakdownIssue(), "MODELED", "CRITICAL") : null,
                        v == null ? null : f("HEALTH", "Age / failure rate", v.ageYears() + "y · " + v.failureRatePct() + "% hist. failures", "MODELED", v.failureRatePct() >= 14 ? "CAUTION" : "INFO"),
                        v == null ? null : f("FUEL", "Fuel level", v.fuelPct() + "%", "MODELED", v.fuelPct() < 35 ? "CAUTION" : "INFO"),
                        ctx.originService() == null ? null : f("REPAIR", "Repair capability at origin", ctx.originService().heavyRepair() ? "heavy-repair bay available" : "light repairs only", "MODELED", ctx.originService().heavyRepair() ? "GOOD" : "CAUTION"),
                        s.nearbySpare() == null ? null : f("SPARE", "Nearest own spare", s.nearbySpare().vehicleId() + " · " + fmt(Math.round(s.nearbySpare().repositionKm())) + " km", "MODELED", "GOOD")
                )));

        // 3 · Driver & Labour
        double hos = v != null ? v.driverHosLeftHrs() : 0;
        out.add(new AgentReport("Driver & Labour Coordinator", "Checks hours-of-service, relief and handling crew", "🧑‍✈️", "Labour",
                v == null ? "No driver record for the assigned unit." :
                        String.format("Driver %s has %sh of duty left%s; cross-dock handling crew available at depot.",
                                v.driver(), fmt(round1(hos)), hos < 4 ? " - relief needed for a long leg" : ""),
                listOf(
                        v == null ? null : f("HOS", "Driver hours-of-service left", fmt(round1(hos)) + "h", "MODELED", hos < 4 ? "CAUTION" : "GOOD"),
                        f("RELIEF", "Relief driver", hos < 4 ? "required for onward leg" : "not required", "MODELED", hos < 4 ? "CAUTION" : "INFO"),
                        f("CREW", "Cross-dock handling crew", "available at depot", "MODELED", "GOOD"),
                        f("GEAR", "Handling equipment", "forklift on site", "MODELED", "INFO")
                )));

        // 4 · Carrier Sourcing
        List<Carrier> cands = new ArrayList<>();
        for (String cid : safe(s.candidateCarrierIds())) erp.findCarrier(cid).ifPresent(cands::add);
        Carrier mostReliable = cands.stream().max((a, b) -> Integer.compare(a.reliabilityPct(), b.reliabilityPct())).orElse(null);
        Carrier cheapestC = cands.stream().min((a, b) -> Double.compare(a.costPerKm(), b.costPerKm())).orElse(null);
        long nonCompliant = cands.stream().filter(c -> !c.compliant()).count();
        out.add(new AgentReport("Carrier Sourcing Agent", "Shops the lane across contract & spot carriers", "🚚", "Sourcing",
                cands.isEmpty() ? "No pre-qualified carriers on this lane." :
                        String.format("%d carriers cover %s. Most reliable: %s (%d%%); cheapest: %s (INR %s/km).%s",
                                cands.size(), routeLabel(s.route()),
                                mostReliable != null ? mostReliable.name() : "-", mostReliable != null ? mostReliable.reliabilityPct() : 0,
                                cheapestC != null ? cheapestC.name() : "-", cheapestC != null ? fmt(Math.round(cheapestC.costPerKm())) : "-",
                                nonCompliant > 0 ? " " + nonCompliant + " has a compliance lapse." : ""),
                listOf(
                        f("POOL", "Qualified carriers on lane", String.valueOf(cands.size()), "MODELED", "INFO"),
                        mostReliable == null ? null : f("BEST", "Most reliable", mostReliable.name() + " · " + mostReliable.reliabilityPct() + "%", "MODELED", "GOOD"),
                        cheapestC == null ? null : f("CHEAP", "Lowest rate", cheapestC.name() + " · INR " + fmt(Math.round(cheapestC.costPerKm())) + "/km", "MODELED", "INFO"),
                        f("COMPLY", "Compliance", nonCompliant == 0 ? "all clear" : nonCompliant + " carrier flagged", "MODELED", nonCompliant == 0 ? "GOOD" : "CAUTION"),
                        f("SPOT", "Live spot-rate market", "not connected", "ROADMAP", "INFO")
                )));

        // 5 · Route & Network
        Lane lane = ctx.lane();
        out.add(new AgentReport("Route & Network Agent", "Maps distance, tolls, terrain and choke points", "🗺️", "Routing",
                lane == null ? "Lane not in the network model." :
                        String.format("%s km on %s, base transit ~%sh at %.0f km/h. Toll INR %s%s%s.",
                                fmt(lane.distanceKm()), routeLabel(s.route()), fmt(round1(ctx.base())), SPEED_KMPH, fmt(lane.tollInr()),
                                lane.ghatSection() ? ", ghat section" : "", lane.checkposts() > 0 ? ", " + lane.checkposts() + " check-post" : ""),
                listOf(
                        f("DIST", "Remaining distance", fmt(s.remainingKm()) + " km", "MODELED", "INFO"),
                        f("BASE", "Base transit time", fmt(round1(ctx.base())) + "h @ " + (int) SPEED_KMPH + " km/h", "COMPUTED", "INFO"),
                        lane == null ? null : f("TOLL", "Toll", "INR " + fmt(lane.tollInr()), "MODELED", "INFO"),
                        lane == null ? null : f("GHAT", "Terrain", lane.ghatSection() ? "ghat / gradient section" : "plains", "MODELED", lane.ghatSection() ? "CAUTION" : "GOOD"),
                        lane == null ? null : f("ALT", "Alternate corridor", lane.altRouteKm() > 0 ? fmt(lane.altRouteKm()) + " km detour available" : "none", "MODELED", "INFO"),
                        f("LIVETRAFFIC", "Live traffic feed", "not connected", "ROADMAP", "INFO")
                )));

        // 6 · Mode-Shift
        boolean rail = lane != null && lane.hasRail();
        boolean air = lane != null && lane.hasAir();
        out.add(new AgentReport("Mode-Shift Agent", "Weighs rail, air and coastal against road", "🚉", "Multimodal",
                String.format("%s%s%s",
                        rail ? "Rail is available (terminal ~" + fmt(round1(lane.railTerminalHrs())) + "h/end). " : "No rail on this lane. ",
                        air ? "Air is an option for tight, light loads. " : "",
                        rail ? "Rail is the low-cost, low-CO2 play when SLA allows." : ""),
                listOf(
                        f("RAIL", "Rail feasibility", rail ? "available" : "not on lane", "MODELED", rail ? "GOOD" : "INFO"),
                        f("AIR", "Air feasibility", air && s.tons() <= 10 ? "viable (<=10t)" : "not suited to this load", "MODELED", "INFO"),
                        f("CO2", "Rail CO2 vs road", rail ? "~" + (int) Math.round(CO2_RAIL / CO2_TRUCK * 100) + "% of road" : "n/a", "COMPUTED", rail ? "GOOD" : "INFO"),
                        f("COASTAL", "Coastal / short-sea", "not applicable inland", "MODELED", "INFO")
                )));

        // 7 · Inventory & Fulfillment
        DistributionCentre dc = ctx.dc();
        out.add(new AgentReport("Inventory & Network Re-opt", "Checks destination stock and alternate sourcing", "🏭", "Fulfillment",
                dc == null ? "No DC stock signal for the destination." :
                        String.format("%s DC holds %s units (~%s days of supply)%s. Alt source: %s (%sh).",
                                dc.city(), fmt(dc.onHandUnits()), fmt(round1(ctx.daysOfSupply())),
                                ctx.daysOfSupply() < 1.5 ? " - thin, any delay risks a stock-out" : " - comfortable",
                                dc.altSourceCity(), fmt(round1(dc.altSourceLeadHrs()))),
                listOf(
                        dc == null ? null : f("STOCK", "Destination on-hand", fmt(dc.onHandUnits()) + " units", "MODELED", "INFO"),
                        dc == null ? null : f("DOS", "Days of supply", fmt(round1(ctx.daysOfSupply())) + " days", "COMPUTED", ctx.daysOfSupply() < 1.5 ? "CRITICAL" : "GOOD"),
                        dc == null ? null : f("ALTDC", "Alternate DC", dc.altSourceCity() + " · " + fmt(round1(dc.altSourceLeadHrs())) + "h", "MODELED", "INFO"),
                        f("JIT", "Downstream dependency", "OEM_LINE".equals(s.customerTier()) ? "JIT line - stoppage risk" : "buffered", "MODELED", "OEM_LINE".equals(s.customerTier()) ? "CRITICAL" : "INFO")
                )));

        // 8 · External Conditions (LIVE)
        WeatherSignal wo = ctx.wxOrigin(), wd = ctx.wxDest();
        FestivalSignal fest = ctx.fest();
        boolean liveWx = "LIVE".equals(wo.source()) || "LIVE".equals(wd.source());
        out.add(new AgentReport("External Conditions Agent", "Live weather, festivals and route advisories", "🌦️", "Conditions",
                String.format("%s: %s %s / %s %s%s%s.",
                        liveWx ? "Live weather" : "Weather (modeled)",
                        wo.city(), wo.condition(), wd.city(), wd.condition(),
                        fest.active() ? "; " + fest.name() + " congestion active" : (fest.daysAway() >= 0 ? "; next festival " + fest.name() + " in " + fest.daysAway() + "d" : ""),
                        ctx.advisories().isEmpty() ? "" : "; " + ctx.advisories().size() + " route advisory active"),
                listOf(
                        f("WXO", "Weather · " + wo.city(), wo.condition() + (wo.delayHrs() > 0 ? " (+" + fmt(wo.delayHrs()) + "h)" : ""), wo.source(), wo.delayHrs() >= 2 ? "CAUTION" : "GOOD"),
                        f("WXD", "Weather · " + wd.city(), wd.condition() + (wd.delayHrs() > 0 ? " (+" + fmt(wd.delayHrs()) + "h)" : ""), wd.source(), wd.delayHrs() >= 2 ? "CAUTION" : "GOOD"),
                        f("FEST", "Festival calendar", fest.active() ? fest.name() + " - active (+" + fmt(fest.delayHrs()) + "h)" : (fest.daysAway() >= 0 ? fest.name() + " in " + fest.daysAway() + "d" : "none upcoming"), "LIVE", fest.active() ? "CAUTION" : "GOOD"),
                        ctx.advisories().isEmpty() ? f("ADV", "Route advisories", "none active", "MODELED", "GOOD")
                                : f("ADV", "Route advisory", ctx.advisories().get(0).type() + " (+" + fmt(ctx.advisories().get(0).delayHrs()) + "h)", "MODELED", "HIGH".equalsIgnoreCase(ctx.advisories().get(0).severity()) ? "CRITICAL" : "CAUTION")
                )));

        // 9 · Regulatory & Compliance
        EwayBillSignal eway = ctx.eway();
        String ewayImpact = eway.expired() ? "CRITICAL" : eway.remainingHrs() < best.etaMean ? "CRITICAL" : eway.remainingHrs() < best.etaMean + 6 ? "CAUTION" : "GOOD";
        out.add(new AgentReport("Regulatory & Compliance Agent", "Watches the e-way-bill clock, permits and cold chain", "📋", "Compliance",
                String.format("E-way bill has %sh left of a %sh window%s. %s",
                        fmt(eway.remainingHrs()), fmt(eway.validityHrs()),
                        eway.expired() ? " - EXPIRED, must be regenerated" : (eway.remainingHrs() < best.etaMean ? " - won't cover the recommended ETA, extend before dispatch" : ""),
                        s.tempControlled() ? "Cold-chain temperature log must stay unbroken." : "Standard documentation."),
                listOf(
                        f("EWAY", "E-way bill remaining", fmt(eway.remainingHrs()) + "h / " + fmt(eway.validityHrs()) + "h", "COMPUTED", ewayImpact),
                        lane == null ? null : f("PERMIT", "Interstate check-posts", lane.checkposts() + " on route", "MODELED", lane.checkposts() > 0 ? "CAUTION" : "GOOD"),
                        f("COLD", "Cold-chain compliance", s.tempControlled() ? "reefer + temp log required" : "n/a", "MODELED", s.tempControlled() ? "CAUTION" : "INFO"),
                        f("HAZ", "Hazmat", s.hazmat() ? "DG permit required" : "non-hazardous", "MODELED", "INFO")
                )));

        // 10 · Cost & Finance
        out.add(new AgentReport("Cost & Finance Agent", "Totals landed cost incl. penalty & risk exposure", "💰", "Finance",
                String.format("Recommended landed cost ~INR %s (of which INR %s is expected penalty/exposure). Cheapest raw execution: %s at INR %s.",
                        fmt(best.expectedCost), fmt(best.penaltyEV + best.stockoutEV + best.spoilageEV + best.serviceRisk), cheapest.title, fmt(cheapest.transport)),
                listOf(
                        f("LANDED", "Recommended E[landed cost]", "INR " + fmt(best.expectedCost), "COMPUTED", "INFO"),
                        f("PENEV", "Expected SLA penalty", "INR " + fmt(best.penaltyEV), "COMPUTED", best.penaltyEV > 0 ? "CAUTION" : "GOOD"),
                        best.stockoutEV > 0 ? f("STOCKEV", "Stock-out exposure", "INR " + fmt(best.stockoutEV), "COMPUTED", "CAUTION") : null,
                        best.spoilageEV > 0 ? f("SPOILEV", "Spoilage exposure", "INR " + fmt(best.spoilageEV), "COMPUTED", "CAUTION") : null,
                        f("CHEAP", "Lowest execution cost", cheapest.title + " · INR " + fmt(cheapest.transport), "COMPUTED", "INFO")
                )));

        // 11 · Risk & Reliability
        out.add(new AgentReport("Risk & Reliability Agent", "On-time odds, cascade risk, forecast confidence", "🎯", "Risk",
                String.format("Recommended option is %d%% likely on-time at %d%% carrier reliability. %s",
                        (int) Math.round(best.pOnTime * 100), best.reliability,
                        best.type.equals("REPAIR") ? "Repair carries a repeat-failure tail." :
                                best.type.equals("OWN_FLEET") ? "Own-fleet move, no cascade to other loads." : "No knock-on to other shipments."),
                listOf(
                        f("ONTIME", "On-time probability", (int) Math.round(best.pOnTime * 100) + "%", "COMPUTED", best.pOnTime >= 0.8 ? "GOOD" : best.pOnTime >= 0.6 ? "CAUTION" : "CRITICAL"),
                        f("REL", "Carrier / mode reliability", best.reliability + "%", "MODELED", best.reliability >= 85 ? "GOOD" : "CAUTION"),
                        f("CASCADE", "Cascade risk", "none - uses idle capacity", "COMPUTED", "GOOD"),
                        f("WXCONF", "Weather forecast confidence", liveWx ? "live feed" : "modeled fallback", liveWx ? "LIVE" : "MODELED", liveWx ? "GOOD" : "INFO")
                )));

        // 12 · Sustainability
        out.add(new AgentReport("ESG Agent", "Carbon cost and empty-km of each option", "🌱", "Sustainability",
                String.format("Recommended option emits ~%s kg CO2. Greenest available: %s at ~%s kg.",
                        fmt(best.co2Kg), greenest.title, fmt(greenest.co2Kg)),
                listOf(
                        f("CO2REC", "Recommended CO2", fmt(best.co2Kg) + " kg", "COMPUTED", "INFO"),
                        f("CO2MIN", "Greenest option", greenest.title + " · " + fmt(greenest.co2Kg) + " kg", "COMPUTED", "GOOD"),
                        f("CARBON", "Carbon cost (recommended)", "INR " + fmt(best.co2Cost), "COMPUTED", "INFO")
                )));

        return out;
    }

    // ── evidence trail + signals strip ──

    private List<Evidence> buildEvidence(Ctx ctx, Draft best) {
        List<Evidence> e = new ArrayList<>();
        e.add(new Evidence("Shipment · cargo", ctx.s().ref() + " · " + fmt(ctx.s().tons()) + "t " + ctx.s().cargo() + " · " + ctx.s().priority(), "MODELED"));
        e.add(new Evidence("On-time probability", (int) Math.round(best.pOnTime * 100) + "% (ETA " + fmt(round1(best.etaMean)) + "h vs " + fmt(ctx.s().slaHoursRemaining()) + "h SLA)", "COMPUTED"));
        e.add(new Evidence("E-way-bill clock", fmt(ctx.eway().remainingHrs()) + "h remaining", "COMPUTED"));
        WeatherSignal worst = ctx.wxOrigin().delayHrs() >= ctx.wxDest().delayHrs() ? ctx.wxOrigin() : ctx.wxDest();
        e.add(new Evidence("Route weather", worst.city() + " " + worst.condition() + (worst.delayHrs() > 0 ? " +" + fmt(worst.delayHrs()) + "h" : ""), worst.source()));
        if (ctx.dc() != null) e.add(new Evidence("Destination stock", fmt(round1(ctx.daysOfSupply())) + " days of supply at " + ctx.dc().city(), "COMPUTED"));
        if (best.penaltyEV > 0) e.add(new Evidence("Expected penalty", "INR " + fmt(best.penaltyEV), "COMPUTED"));
        e.add(new Evidence("Expected landed cost", "INR " + fmt(best.expectedCost) + " (risk-adjusted rank #1)", "COMPUTED"));
        return e;
    }

    private List<SignalView> buildSignals(Ctx ctx) {
        List<SignalView> out = new ArrayList<>();
        WeatherSignal worst = ctx.wxOrigin().delayHrs() >= ctx.wxDest().delayHrs() ? ctx.wxOrigin() : ctx.wxDest();
        out.add(new SignalView("WEATHER", "Weather · " + routeLabel(ctx.s().route()),
                ctx.wxOrigin().city() + " " + ctx.wxOrigin().condition() + " / " + ctx.wxDest().city() + " " + ctx.wxDest().condition(),
                worst.source(), worst.delayHrs() >= 2 ? "CAUTION" : "GOOD"));
        FestivalSignal fest = ctx.fest();
        out.add(new SignalView("FESTIVAL", "Festival calendar",
                fest.active() ? fest.name() + " - congestion active" : (fest.daysAway() >= 0 ? "Next: " + fest.name() + " in " + fest.daysAway() + "d" : "none upcoming"),
                "LIVE", fest.active() ? "CAUTION" : "GOOD"));
        EwayBillSignal eway = ctx.eway();
        out.add(new SignalView("EWAYBILL", "E-way bill",
                fmt(eway.remainingHrs()) + "h left of " + fmt(eway.validityHrs()) + "h",
                "COMPUTED", eway.expired() ? "CRITICAL" : eway.remainingHrs() < ctx.base() + 6 ? "CAUTION" : "GOOD"));
        ctx.advisories().stream().filter(a -> "HIGH".equalsIgnoreCase(a.severity())).findFirst().ifPresent(a ->
                out.add(new SignalView("BLOCKAGE", a.type() + " · " + a.area(), a.message(), "MODELED", "CRITICAL")));
        return out;
    }

    private ScenarioView buildScenario(Ctx ctx, String disruption) {
        Shipment s = ctx.s();
        String plate = ctx.veh() != null ? ctx.veh().plate() : s.vehicleId();
        String issue = s.breakdownIssue() != null ? s.breakdownIssue() : "recovery review";
        String summary = String.format("%.0ft %s on the %s lane - %s. %s km and %sh of SLA remaining; INR %s at risk for a %s customer.",
                s.tons(), s.cargo(), routeLabel(s.route()), issue, fmt(s.remainingKm()), fmt(s.slaHoursRemaining()),
                fmt(s.value()), tierLabel(s.customerTier()));
        return new ScenarioView(s.id(), s.ref(), s.cargo(), s.tons(), routeLabel(s.route()), s.origin(), s.destination(),
                s.priority(), s.value(), s.remainingKm(), s.slaHoursRemaining(), plate, disruption, s.customerTier(), summary);
    }

    // ── narration (templated + optional AI) ──

    private String templatedRationale(Ctx ctx, Draft best) {
        String timing = best.onTime
                ? "delivers ~" + fmt(round1(ctx.s().slaHoursRemaining() - best.etaMean)) + "h inside the " + fmt(ctx.s().slaHoursRemaining()) + "h SLA"
                : "is the least-late option (misses SLA by ~" + fmt(round1(best.etaMean - ctx.s().slaHoursRemaining())) + "h)";
        return String.format(
                "Recommend \"%s\": it %s at %d%% on-time odds, for a risk-adjusted landed cost of ~INR %s (%d%% reliability). It wins because it best balances on-time delivery against penalty, spoilage and stock-out exposure for this %s cargo. Review and approve, or fall back to the runner-up.",
                best.title, timing, (int) Math.round(best.pOnTime * 100), fmt(best.expectedCost), best.reliability, ctx.s().priority());
    }

    private record Narrative(String rationale, String cargo, String conditions, String risk) {}

    private Optional<Narrative> aiNarrative(Ctx ctx, ScenarioView scenario, List<OptionView> options, Draft best) {
        String system = """
                You are the Decision Manager of an AI supply-chain "control tower" for CSCE Nexus.
                You are given a disrupted shipment, live signals, and options that have ALREADY been
                scored deterministically. Write tight, confident operational prose (1-2 sentences each).
                GROUND every claim strictly in the numbers provided - never invent ETAs, costs or figures.
                Return ONLY minified JSON, no markdown:
                {"rationale":"why the recommended option wins","cargo":"one line on what's at stake",
                 "conditions":"one line on live weather/festival/e-way-bill","risk":"one line on the main residual risk"}
                """;
        try {
            String payload = "SCENARIO:\n" + mapper.writeValueAsString(scenario)
                    + "\n\nSIGNALS:\n" + mapper.writeValueAsString(buildSignals(ctx))
                    + "\n\nSCORED_OPTIONS (ranked, first is recommended):\n" + mapper.writeValueAsString(options)
                    + "\n\nRECOMMENDED_OPTION_ID: " + best.id;
            Optional<String> out = ai.complete(system, payload, 900);
            if (out.isEmpty()) return Optional.empty();
            String json = out.get().trim();
            if (json.startsWith("```")) json = json.replaceAll("^```[a-zA-Z]*\\s*", "").replaceAll("\\s*```$", "");
            JsonNode n = mapper.readTree(json);
            String rationale = n.path("rationale").asText("").trim();
            if (rationale.isBlank()) return Optional.empty();
            return Optional.of(new Narrative(rationale,
                    n.path("cargo").asText("").trim(),
                    n.path("conditions").asText("").trim(),
                    n.path("risk").asText("").trim()));
        } catch (Exception e) {
            log.warn("AI narration failed, keeping templated: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /** Fold the AI one-liners onto the matching agent headlines (numbers stay templated). */
    private List<AgentReport> applyNarrative(List<AgentReport> agents, Narrative n) {
        List<AgentReport> out = new ArrayList<>(agents);
        replaceHeadline(out, "Cargo", n.cargo());
        replaceHeadline(out, "Conditions", n.conditions());
        replaceHeadline(out, "Risk", n.risk());
        return out;
    }

    private void replaceHeadline(List<AgentReport> agents, String domain, String headline) {
        if (headline == null || headline.isBlank()) return;
        for (int i = 0; i < agents.size(); i++) {
            AgentReport a = agents.get(i);
            if (a.domain().equals(domain)) {
                agents.set(i, new AgentReport(a.name(), a.role(), a.emoji(), a.domain(), headline, a.factors()));
                return;
            }
        }
    }

    // ── helpers ──

    private static Factor f(String code, String label, String value, String source, String impact) {
        return new Factor(code, label, value, source, impact);
    }

    @SafeVarargs
    private static <T> List<T> listOf(T... items) {
        List<T> out = new ArrayList<>();
        for (T i : items) if (i != null) out.add(i);
        return out;
    }

    private static String tierLabel(String tier) {
        return switch (tier == null ? "" : tier) {
            case "KEY_ACCOUNT" -> "key-account";
            case "OEM_LINE" -> "OEM-line (JIT)";
            case "HOSPITAL_CHAIN" -> "hospital-chain";
            default -> "standard";
        };
    }

    private static String impactForPriority(String p) {
        return "CRITICAL".equalsIgnoreCase(p) ? "CRITICAL" : "HIGH".equalsIgnoreCase(p) ? "CAUTION" : "INFO";
    }

    private static double priorityWeight(String priority) {
        return switch (priority == null ? "" : priority.toUpperCase()) {
            case "CRITICAL" -> 1.0;
            case "HIGH" -> 0.6;
            default -> 0.25;
        };
    }

    private String advisoryLabel(Advisory a) {
        return a.type() + " (" + a.severity() + ", +" + fmt(a.delayHrs()) + "h): " + a.message();
    }

    private static int severityPenalty(String severity) {
        return switch (severity == null ? "" : severity.toUpperCase()) {
            case "HIGH" -> 12;
            case "MODERATE" -> 6;
            default -> 2;
        };
    }

    private static String routeLabel(String route) {
        return route == null ? "" : route.replace("-", " to ");
    }

    // Standard normal CDF/PDF via erf (Abramowitz & Stegun 7.1.26).
    private static double Phi(double z) {
        return 0.5 * (1 + erf(z / Math.sqrt(2)));
    }

    private static double phi(double x) {
        return Math.exp(-0.5 * x * x) / Math.sqrt(2 * Math.PI);
    }

    private static double erf(double x) {
        double t = 1 / (1 + 0.3275911 * Math.abs(x));
        double y = 1 - (((((1.061405429 * t - 1.453152027) * t) + 1.421413741) * t - 0.284496736) * t + 0.254829592) * t * Math.exp(-x * x);
        return Math.signum(x) * y;
    }

    private static double clampP(double p) { return Math.max(0.02, Math.min(0.999, p)); }

    private static double clamp01(double v) { return Math.max(0, Math.min(0.95, v)); }

    private static int clampRel(int r) { return Math.max(40, Math.min(99, r)); }

    private static double round1(double v) { return Math.round(v * 10.0) / 10.0; }

    private static <T> List<T> safe(List<T> l) { return l == null ? List.of() : l; }

    private static List<String> dedupe(List<String> in) {
        return in.stream().distinct().toList();
    }

    private static String or(String v, String fallback) {
        return v == null || v.isBlank() ? fallback : v;
    }

    private static String fmt(double v) {
        if (v == Math.floor(v) && !Double.isInfinite(v)) return String.format("%,d", (long) v);
        return String.format("%,.1f", v);
    }
}
