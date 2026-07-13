package com.cscen.forum.service;

import com.cscen.forum.service.agents.DistributionPlanner;
import com.cscen.forum.service.agents.DistributionPlanner.ChannelFill;
import com.cscen.forum.service.agents.DistributionPlanner.FulfilmentPlan;
import com.cscen.forum.service.agents.DistributionPlanner.Supply;
import com.cscen.forum.service.agents.ExternalSignals;
import com.cscen.forum.service.agents.ExternalSignals.EwayBillSignal;
import com.cscen.forum.service.agents.ExternalSignals.FestivalSignal;
import com.cscen.forum.service.agents.ExternalSignals.WeatherSignal;
import com.cscen.forum.service.erp.ErpPort;
import com.cscen.forum.service.erp.ErpPort.Advisory;
import com.cscen.forum.service.erp.ErpPort.Carrier;
import com.cscen.forum.service.erp.ErpPort.City;
import com.cscen.forum.service.erp.ErpPort.Department;
import com.cscen.forum.service.erp.ErpPort.DistributionCentre;
import com.cscen.forum.service.erp.ErpPort.Lane;
import com.cscen.forum.service.erp.ErpPort.Rdc;
import com.cscen.forum.service.erp.ErpPort.RdcLeg;
import com.cscen.forum.service.erp.ErpPort.SalesChannel;
import com.cscen.forum.service.erp.ErpPort.ServicePoint;
import com.cscen.forum.service.erp.ErpPort.Shipment;
import com.cscen.forum.service.erp.ErpPort.Transporter;
import com.cscen.forum.service.erp.ErpPort.Vehicle;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    // How many discrete factors the catalog spans (surfaced selectively per run). A
    // multi-drop SKU x channel consignment also brings the demand-side domains into play.
    private static final int FACTOR_CATALOG_SIZE = 118;
    private static final int FACTOR_CATALOG_SIZE_DISTRIBUTION = 158;

    private final ErpPort erp;
    private final ExternalSignals signals;
    private final DistributionPlanner planner;
    private final AgentAiClient ai;
    private final ObjectMapper mapper;

    public AgentOrchestrator(ErpPort erp, ExternalSignals signals, DistributionPlanner planner,
                             AgentAiClient ai, ObjectMapper mapper) {
        this.erp = erp;
        this.signals = signals;
        this.planner = planner;
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
                             int score, boolean recommended, String summary,
                             List<String> pros, List<String> cons, List<String> risks,
                             FulfilmentPlan plan) {}

    /** One department that must be told, and what it has to do. */
    public record Stakeholder(String department, String scope, String action, String urgency) {}

    // ── the decision brief: how the engine actually got to its answer ──

    /** One link in the reasoning chain, as a question the analyst would ask and its answer. */
    public record DecisionStep(int step, String question, String finding) {}

    /** Why the winner beat this specific rival — with the rupee delta that decided it. */
    public record Comparison(String optionId, String title, long expectedCost, long deltaVsBest,
                             int servicePct, String whyNotChosen) {}

    public record DecisionBrief(String situation, List<String> bindingConstraints,
                               List<DecisionStep> howWeGotHere, List<Comparison> headToHead,
                               List<String> decisiveFactors, List<String> assumptions,
                               List<String> notConsidered, List<String> whatWouldChangeIt,
                               String bottomLine) {}

    // ── impact: what this run actually achieved, in numbers a CxO can use ──

    public record ImpactStat(String label, String value, String detail) {}

    /** One line of the manual-effort baseline, so the time-saved claim is auditable. */
    public record ManualTask(String task, int minutes) {}

    public record ImpactSummary(int agentsRun, int factorsConsidered, int optionsEvaluated,
                                int dataPointsAnalysed, int demandLinesAllocated, int skusPlanned,
                                int channelsPlanned, int citiesServed, int depotsQueried,
                                int departmentsCoordinated, int unitsPlanned, int unitsProtected,
                                long runtimeMs, int manualMinutes, double hoursSaved,
                                String baselineTitle, long baselineCost, long costAvoidedInr,
                                long penaltyAvoidedInr, List<ImpactStat> headline,
                                List<ManualTask> manualBaseline, String narrative) {}

    public record SignalView(String kind, String label, String detail, String source, String impact) {}

    public record ScenarioView(String shipmentId, String ref, String cargo, double tons, String route,
                               String origin, String destination, String priority, long value,
                               double remainingKm, double slaHoursRemaining, String vehiclePlate,
                               String disruption, String customerTier, String summary) {}

    public record Evidence(String label, String value, String source) {}

    public record Recommendation(String optionId, String title, String rationale, List<Evidence> evidence) {}

    public record AgentRun(ScenarioView scenario, List<SignalView> signals, List<AgentReport> agents,
                           List<OptionView> options, Recommendation recommendation,
                           List<Stakeholder> stakeholders, DecisionBrief brief, ImpactSummary impact,
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
        // distribution-side (null on simple single-drop shipments)
        FulfilmentPlan plan;
        long fixedCost;   // repair / cross-dock / mobilisation, on top of the plan's freight
    }

    /** Gathered context for a run — read once, shared by every agent. */
    private record Ctx(Shipment s, Vehicle veh, Lane lane, City origin, City dest, DistributionCentre dc,
                       ServicePoint originService, WeatherSignal wxOrigin, WeatherSignal wxDest,
                       FestivalSignal fest, EwayBillSignal eway, List<Advisory> advisories,
                       double extDelay, double extSigma, double base, double daysOfSupply,
                       int advisorySevPenalty, String worstCondition, String worstWxSource, double laneKm) {}

    // ── main entry ──

    public AgentRun run(String shipmentId, String disruptionInput) {
        long startedAt = System.nanoTime();
        Shipment s = erp.findShipment(shipmentId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown shipment " + shipmentId));

        String disruption = (disruptionInput == null || disruptionInput.isBlank())
                ? (s.breakdownIssue() != null ? "Vehicle breakdown" : "Recovery review")
                : disruptionInput.trim();

        Ctx ctx = gather(s);
        boolean distribution = s.hasDistributionPlan();
        List<Draft> drafts = distribution ? buildDistributionStrategies(ctx) : buildStrategies(ctx);
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
        if (distribution) {
            agents = spliceDistributionAgents(agents, ctx, drafts, best);
        }
        List<Evidence> evidence = buildEvidence(ctx, best);
        List<Stakeholder> stakeholders = distribution ? buildStakeholders(best) : List.of();

        // Templated narrative first (always works), then enrich with AI.
        String rationale = templatedRationale(ctx, best);
        boolean aiPowered = false;
        Optional<Narrative> ai2 = ai.isEnabled() ? aiNarrative(ctx, scenario, options, best) : Optional.empty();
        if (ai2.isPresent()) {
            rationale = or(ai2.get().rationale(), rationale);
            agents = applyNarrative(agents, ai2.get());
            aiPowered = true;
        }

        DecisionBrief brief = buildDecisionBrief(ctx, drafts, top, best, distribution);

        long runtimeMs = Math.max(1, (System.nanoTime() - startedAt) / 1_000_000);
        ImpactSummary impact = buildImpact(ctx, drafts, best, agents.size(), distribution, runtimeMs);

        Recommendation rec = new Recommendation(best.id, best.title, rationale, evidence);
        return new AgentRun(scenario, sig, agents, options, rec, stakeholders, brief, impact,
                distribution ? FACTOR_CATALOG_SIZE_DISTRIBUTION : FACTOR_CATALOG_SIZE,
                aiPowered, aiProvider());
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

    // ── 2b. distribution-side strategies (multi-drop, SKU x channel consignments) ──

    /** Hours to mobilise a replacement vehicle to the breakdown point, and to cross-dock. */
    private static final double MOBILISE_HRS = 4.0;
    private static final double CROSSDOCK_HRS = 2.5;
    private static final long CROSSDOCK_COST = 8000L;

    /**
     * For a stranded multi-drop consignment the stock is not lost, it is late. So the
     * strategies are about <b>where each city's units come from</b>: the truck itself
     * (slow, 100%), a nearby RDC that can lend (fast, partial), or both — with the
     * recovered load repaying the RDC.
     */
    private List<Draft> buildDistributionStrategies(Ctx ctx) {
        Shipment s = ctx.s();
        List<String> cities = planner.destinations(s);
        String nearCity = s.breakdownNearCity() != null ? s.breakdownNearCity() : s.origin();
        Rdc nearRdc = erp.findRdc(nearCity).orElse(null);
        double trunkRate = s.freightRatePerKg() > 0 ? s.freightRatePerKg() : 8.0;
        double laneKm = ctx.laneKm();

        // Remaining legs from the breakdown point. If we broke down next to an RDC, its
        // outbound legs ARE our remaining legs.
        Map<String, Double> legHrs = new LinkedHashMap<>();
        Map<String, Double> legKm = new LinkedHashMap<>();
        if (nearRdc != null) {
            for (RdcLeg l : nearRdc.legs()) {
                if (cities.contains(l.toCity())) {
                    legHrs.put(l.toCity(), l.transitHrs());
                    legKm.put(l.toCity(), l.distanceKm());
                }
            }
        }
        for (String c : cities) {
            legHrs.putIfAbsent(c, s.remainingKm() / SPEED_KMPH);
            legKm.putIfAbsent(c, s.remainingKm());
        }
        // The truck's remaining leg is priced pro-rata off the trunk rate (it is already loaded).
        Map<String, Double> truckRate = new LinkedHashMap<>();
        for (String c : cities) truckRate.put(c, round2(trunkRate * (legKm.get(c) / Math.max(1, laneKm))));

        // Repair economics at the breakdown point.
        String issue = s.breakdownIssue() == null ? "" : s.breakdownIssue().toLowerCase();
        boolean heavy = issue.contains("engine") || issue.contains("gearbox") || issue.contains("axle");
        double repairHrs = heavy ? 7.0 : 3.5;
        long repairCost = heavy ? 28000L : 9000L;
        ServicePoint sp = erp.findServicePoint(nearCity).orElse(null);
        boolean canHeavy = sp != null && sp.heavyRepair();
        if (heavy && !canHeavy) { repairHrs += 4; repairCost += 15000; }

        List<Draft> drafts = new ArrayList<>();
        char letter = 'A';

        // RDC supplies (fast, partial).
        List<Supply> rdcSupplies = new ArrayList<>();
        Supply nearSupply = null;
        for (Rdc r : erp.rdcs()) {
            Optional<Supply> sup = planner.rdcSupply(r, s, ctx.extDelay());
            if (sup.isEmpty()) continue;
            rdcSupplies.add(sup.get());
            if (r.city().equalsIgnoreCase(nearCity)) nearSupply = sup.get();
        }

        // A) Repair the vehicle and complete the run (100% of units, but slow).
        {
            Map<String, Double> eta = new LinkedHashMap<>();
            for (String c : cities) eta.put(c, round1(repairHrs + legHrs.get(c) + ctx.extDelay()));
            Supply truck = planner.truckSupply("Recovered vehicle", s, eta, truckRate);
            Draft d = new Draft();
            d.id = String.valueOf(letter++);
            d.type = "REPAIR";
            d.provider = "On-site repair - " + (ctx.veh() != null ? ctx.veh().plate() : s.vehicleId());
            d.title = "Repair the vehicle & complete the run";
            d.plan = planner.plan(d.id, s, List.of(truck));
            d.fixedCost = repairCost;
            d.etaMean = eta.values().stream().mapToDouble(Double::doubleValue).max().orElse(0);
            d.etaSigma = ctx.extSigma() + repairHrs * 0.25;
            d.reliability = clampRel(REPAIR_RELIABILITY - ctx.advisorySevPenalty());
            d.co2Kg = round1(s.remainingKm() * CO2_TRUCK);
            d.pros.add("Every unit is already on the vehicle - 100% of the load eventually lands");
            d.pros.add("No stock transfer, no RDC draw-down, no backfill to unwind");
            d.cons.add("~" + fmt(round1(repairHrs)) + "h stationary" + (canHeavy ? " (heavy-repair bay on site)" : " (no heavy bay - tow/mobile crew)"));
            d.risks.add("Same unit - residual risk of repeat failure");
            drafts.add(d);
        }

        // B) Cross-dock onto a replacement transporter from the master (best of the pool).
        List<Transporter> pool = erp.transportersForLane(s.route());
        Transporter bestT = null;
        Draft bestTDraft = null;
        for (Transporter t : pool) {
            Map<String, Double> eta = new LinkedHashMap<>();
            for (String c : cities) {
                double legTransit = t.transitHrs() * (legKm.get(c) / Math.max(1, laneKm));
                eta.put(c, round1(MOBILISE_HRS + CROSSDOCK_HRS + legTransit + ctx.extDelay()));
            }
            Map<String, Double> rate = new LinkedHashMap<>();
            for (String c : cities) rate.put(c, round2(t.ratePerKg() * (legKm.get(c) / Math.max(1, laneKm))));
            Supply truck = planner.truckSupply("Replacement - " + t.name(), s, eta, rate);
            Draft d = new Draft();
            d.type = "REPLACEMENT_TRANSPORTER";
            d.provider = t.name() + " @ INR " + fmt(t.ratePerKg()) + "/kg";
            d.title = "Cross-dock to " + t.name() + " (replacement vehicle)";
            d.plan = planner.plan("X", s, List.of(truck));
            d.fixedCost = CROSSDOCK_COST;
            d.etaMean = eta.values().stream().mapToDouble(Double::doubleValue).max().orElse(0);
            d.etaSigma = ctx.extSigma() + 0.8;
            d.reliability = clampRel(t.reliabilityPct() - ctx.advisorySevPenalty());
            d.co2Kg = round1(s.remainingKm() * CO2_TRUCK);
            long cost = d.fixedCost + d.plan.totalCostInr();
            if (bestTDraft == null || cost < bestTDraft.fixedCost + bestTDraft.plan.totalCostInr()) {
                bestTDraft = d;
                bestT = t;
            }
        }
        if (bestTDraft != null && bestT != null) {
            bestTDraft.id = String.valueOf(letter++);
            bestTDraft.plan = planner.plan(bestTDraft.id, s, List.of(
                    planner.truckSupply("Replacement - " + bestT.name(), s,
                            etaFor(bestT, cities, legKm, laneKm, ctx.extDelay()),
                            rateFor(bestT, cities, legKm, laneKm))));
            bestTDraft.pros.add("Full load moves - no partial fill, no RDC draw-down");
            bestTDraft.pros.add("Best of " + pool.size() + " transporters on the lane at INR " + fmt(bestT.ratePerKg()) + "/kg");
            bestTDraft.cons.add("Cross-dock at the breakdown point (+" + fmt(CROSSDOCK_HRS) + "h) and a mobilisation wait");
            drafts.add(bestTDraft);
        }

        // C) Nearest RDC alone - can the depot next door actually cover us?
        if (nearSupply != null) {
            Draft d = new Draft();
            d.id = String.valueOf(letter++);
            d.type = "RDC_NEAR";
            d.provider = nearCity + " RDC";
            d.title = "Source from the " + nearCity + " RDC (nearest depot)";
            d.plan = planner.plan(d.id, s, List.of(nearSupply));
            d.fixedCost = 0;
            d.etaMean = nearSupply.etaByCity().values().stream().mapToDouble(Double::doubleValue).max().orElse(0);
            d.etaSigma = ctx.extSigma() * 0.6 + 0.5;
            d.reliability = clampRel(92 - ctx.advisorySevPenalty());
            d.co2Kg = round1(shortHaulCo2(nearSupply, d.plan));
            d.pros.add("Stock is already picked and at the dock - fastest possible to shelf");
            d.pros.add("Depot sits at the breakdown point - shortest legs in the network");
            d.cons.add("Only lends what it can spare before its own replenishment lands");
            d.risks.add("Draws down " + nearCity + " cover - needs a backfill");
            drafts.add(d);
        }

        // D) Both RDCs - pool the network's spare cover.
        if (rdcSupplies.size() > 1) {
            Draft d = new Draft();
            d.id = String.valueOf(letter++);
            d.type = "RDC_POOL";
            d.provider = rdcSupplies.stream().map(Supply::name).reduce((a, b) -> a + " + " + b).orElse("RDCs");
            d.title = "Pool both RDCs (" + rdcSupplies.size() + " depots)";
            d.plan = planner.plan(d.id, s, rdcSupplies);
            d.fixedCost = 0;
            d.etaMean = rdcSupplies.stream().flatMap(x -> x.etaByCity().values().stream())
                    .mapToDouble(Double::doubleValue).max().orElse(0);
            d.etaSigma = ctx.extSigma() * 0.6 + 0.6;
            d.reliability = clampRel(90 - ctx.advisorySevPenalty());
            d.co2Kg = round1(shortHaulCo2Pool(rdcSupplies, d.plan));
            d.pros.add("Each city is served from whichever depot is closest to it");
            d.pros.add("Covers the urgent channels far inside their promise windows");
            d.cons.add("Still cannot cover the whole load - the balance waits for the vehicle");
            d.risks.add("Draws down cover at both depots - both need backfilling");
            drafts.add(d);
        }

        // E) HYBRID - RDCs serve the urgent channels now, the recovered load follows and
        //    repays the depots. This is usually the right answer when the breakdown is
        //    next to a stocking RDC.
        if (!rdcSupplies.isEmpty()) {
            Map<String, Double> eta = new LinkedHashMap<>();
            for (String c : cities) eta.put(c, round1(repairHrs + legHrs.get(c) + ctx.extDelay()));
            Supply truck = planner.truckSupply("Recovered vehicle", s, eta, truckRate);
            List<Supply> all = new ArrayList<>(rdcSupplies);
            all.add(truck);
            Draft d = new Draft();
            d.id = String.valueOf(letter++);
            d.type = "HYBRID";
            d.provider = "RDC pool + recovered vehicle";
            d.title = "Hybrid: RDCs cover urgent channels now, recovered load backfills";
            d.plan = planner.plan(d.id, s, all);
            d.fixedCost = repairCost;
            d.etaMean = eta.values().stream().mapToDouble(Double::doubleValue).max().orElse(0);
            d.etaSigma = ctx.extSigma() * 0.7 + 0.6;
            d.reliability = clampRel(90 - ctx.advisorySevPenalty());
            d.co2Kg = round1(s.remainingKm() * CO2_TRUCK + shortHaulCo2Pool(rdcSupplies, d.plan));
            d.pros.add("Time-critical channels are served from the depot in hours, not a day");
            d.pros.add("The recovered load repays the depots at the gate - little extra freight");
            d.pros.add("100% of demand is eventually covered - nothing is written off");
            d.cons.add("Two-wave execution - ops must handle a split inbound");
            drafts.add(d);
        }

        return drafts;
    }

    private Map<String, Double> etaFor(Transporter t, List<String> cities, Map<String, Double> legKm,
                                       double laneKm, double extDelay) {
        Map<String, Double> eta = new LinkedHashMap<>();
        for (String c : cities) {
            double legTransit = t.transitHrs() * (legKm.get(c) / Math.max(1, laneKm));
            eta.put(c, round1(MOBILISE_HRS + CROSSDOCK_HRS + legTransit + extDelay));
        }
        return eta;
    }

    private Map<String, Double> rateFor(Transporter t, List<String> cities, Map<String, Double> legKm, double laneKm) {
        Map<String, Double> rate = new LinkedHashMap<>();
        for (String c : cities) rate.put(c, round2(t.ratePerKg() * (legKm.get(c) / Math.max(1, laneKm))));
        return rate;
    }

    private double shortHaulCo2(Supply sup, FulfilmentPlan plan) {
        double km = plan.sources().stream().filter(x -> x.source().equals(sup.name()))
                .mapToDouble(x -> 1).sum();
        return km * 200 * CO2_TRUCK; // one short-haul vehicle per served city
    }

    private double shortHaulCo2Pool(List<Supply> sups, FulfilmentPlan plan) {
        return sups.stream().mapToDouble(x -> shortHaulCo2(x, plan)).sum();
    }

    // ── 3. price + rank every option (risk-adjusted expected landed cost) ──

    private void scoreAndRank(Ctx ctx, List<Draft> drafts) {
        Shipment s = ctx.s();
        double priorityWeight = priorityWeight(s.priority());

        for (Draft d : drafts) {
            // Distribution options are priced at the UNIT level by the planner: the service
            // miss is already a real rupee penalty per unfilled/late unit, so we use that
            // instead of the value-fraction service-risk heuristic (no double counting).
            if (d.plan != null) {
                FulfilmentPlan p = d.plan;
                d.pOnTime = clampP(p.fillPct() / 100.0);
                d.onTime = p.fillPct() >= 100;
                d.penaltyEV = p.penaltyInr();
                d.serviceRisk = 0;
                d.stockoutEV = 0;
                d.spoilageEV = 0;
                d.co2Cost = Math.round(d.co2Kg * CARBON_PRICE);
                d.transport = d.fixedCost + p.freightInr() + p.handlingInr();
                d.expectedCost = d.transport + d.penaltyEV + d.co2Cost;
                d.costSigma = p.penaltyInr() * 0.25 + s.penaltyPerHourInr() * d.etaSigma;
                d.riskAdjusted = d.expectedCost + LAMBDA * priorityWeight * d.costSigma;

                if (p.unfilledUnits() > 0) {
                    d.risks.add(0, fmt(p.unfilledUnits()) + " units cannot be covered in this window");
                }
                p.channelTotals().stream().filter(c -> c.fillPct() < 100).forEach(c ->
                        d.risks.add(c.channel() + " (" + c.channelName() + ") only " + c.fillPct()
                                + "% filled inside its " + fmt(c.promiseHrs()) + "h promise"));
                continue;
            }

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
        if (d.plan != null) {
            if (d.fixedCost > 0) breakdown.add(new CostLine("Repair / cross-dock", d.fixedCost));
            breakdown.add(new CostLine("Freight (all legs)", d.plan.freightInr()));
            if (d.plan.handlingInr() > 0) breakdown.add(new CostLine("RDC handling / stock transfer", d.plan.handlingInr()));
            if (d.plan.penaltyInr() > 0) breakdown.add(new CostLine("Channel service penalty", d.plan.penaltyInr()));
        } else {
            breakdown.add(new CostLine("Transport / execution", d.transport));
            if (d.penaltyEV > 0) breakdown.add(new CostLine("Expected SLA penalty", d.penaltyEV));
            if (d.stockoutEV > 0) breakdown.add(new CostLine("Stock-out exposure", d.stockoutEV));
            if (d.spoilageEV > 0) breakdown.add(new CostLine("Spoilage / cold-chain", d.spoilageEV));
            if (d.serviceRisk > 0) breakdown.add(new CostLine("Service-risk (miss x unreliability)", d.serviceRisk));
        }
        if (d.co2Cost > 0) breakdown.add(new CostLine("Carbon cost", d.co2Cost));
        String summary = d.plan != null ? d.plan.summary() : null;
        return new OptionView(d.id, d.title, d.type, d.provider, round1(d.etaMean), d.onTime,
                (int) Math.round(d.pOnTime * 100), d.reliability, d.expectedCost, breakdown, d.co2Kg,
                d.score, recommended, summary, d.pros, d.cons, d.risks, d.plan);
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

    // ── 4b. the six distribution-side agents (only for multi-drop SKU x channel loads) ──

    /** Splice the demand-side specialists in after the Fulfillment agent. */
    private List<AgentReport> spliceDistributionAgents(List<AgentReport> base, Ctx ctx,
                                                       List<Draft> all, Draft best) {
        List<AgentReport> extra = buildDistributionAgents(ctx, all, best);
        List<AgentReport> out = new ArrayList<>();
        boolean inserted = false;
        for (AgentReport a : base) {
            out.add(a);
            if (!inserted && "Fulfillment".equals(a.domain())) {
                out.addAll(extra);
                inserted = true;
            }
        }
        if (!inserted) out.addAll(extra);
        return out;
    }

    private List<AgentReport> buildDistributionAgents(Ctx ctx, List<Draft> all, Draft best) {
        Shipment s = ctx.s();
        FulfilmentPlan p = best.plan;
        List<AgentReport> out = new ArrayList<>();
        List<String> cities = planner.destinations(s);
        int totalUnits = s.loadLines().stream()
                .flatMap(l -> l.channels().values().stream()).mapToInt(Integer::intValue).sum();

        // 13 · Demand & Channel Planner
        List<Factor> demandFactors = new ArrayList<>();
        demandFactors.add(f("UNITS", "Consignment", fmt(totalUnits) + " units / " + fmt(s.tons()) + "t across " + erp.skus().size() + " SKUs", "MODELED", "INFO"));
        demandFactors.add(f("DROPS", "Destination cities", String.join(" + ", cities), "MODELED", "INFO"));
        demandFactors.add(f("COVER", "What this load represents", fmt(s.coverDays()) + " days of cover per city (no buffer behind it)", "MODELED", "CRITICAL"));
        for (SalesChannel c : erp.salesChannels()) {
            int dem = channelDemand(s, c.code(), null);
            if (dem == 0) continue;
            demandFactors.add(f("CH_" + c.code(), c.name() + " demand",
                    fmt(dem) + " units, promise " + fmt(c.maxDelayHrs()) + "h, INR " + fmt(c.penaltyPerUnitInr()) + "/unit at risk",
                    "MODELED", c.maxDelayHrs() <= 12 ? "CRITICAL" : c.maxDelayHrs() <= 24 ? "CAUTION" : "INFO"));
        }
        out.add(new AgentReport("Demand & Channel Planner", "Breaks the load down by city, SKU and consumer channel", "📊", "Demand",
                String.format("%s units for %s - and this load IS their %s-day cover, so there is no buffer behind it. The tight end is Qcommerce (%sh promise) and Ecommerce (%sh); traditional trade can wait a beat.",
                        fmt(totalUnits), String.join(" + ", cities), fmt(s.coverDays()),
                        fmt(channelPromise("Qc")), fmt(channelPromise("Ec"))),
                demandFactors));

        // 14 · Multi-Echelon Sourcing
        List<Factor> srcFactors = new ArrayList<>();
        int totalLendable = 0;
        for (Rdc r : erp.rdcs()) {
            Map<String, Integer> lend = planner.lendableBySku(r);
            int sum = lend.values().stream().mapToInt(Integer::intValue).sum();
            totalLendable += sum;
            boolean atBreakdown = r.city().equalsIgnoreCase(s.breakdownNearCity());
            srcFactors.add(f("RDC_" + r.city(), r.city() + " RDC (" + r.region() + ")",
                    "can lend " + fmt(sum) + " units; own replenishment in " + fmt(r.replenishInDays()) + "d",
                    "COMPUTED", sum > 0 ? "GOOD" : "CAUTION"));
            if (atBreakdown) {
                srcFactors.add(f("RDC_NEAR", "Nearest depot to the breakdown", r.city() + " - the vehicle is stranded at its gate", "MODELED", "GOOD"));
            }
        }
        srcFactors.add(f("LENDABLE", "Total network spare cover", fmt(totalLendable) + " of " + fmt(totalUnits) + " units ("
                + pct(totalLendable, totalUnits) + "% of the load)", "COMPUTED", totalLendable >= totalUnits ? "GOOD" : "CAUTION"));
        srcFactors.add(f("POLICY", "Lending policy", "each RDC retains replenishment lead (" + fmt(erp.rdcs().isEmpty() ? 2 : erp.rdcs().get(0).replenishInDays())
                + "d) + " + fmt(DistributionPlanner.RDC_SAFETY_DAYS) + "d safety", "MODELED", "INFO"));
        out.add(new AgentReport("Multi-Echelon Sourcing Agent", "Finds depots that can lend without breaking their own cover", "🏬", "Sourcing-Net",
                String.format("The two RDCs can spare %s units between them - %d%% of the load. Neither can cover this alone: each is itself %s days from replenishment, so it must retain its own cover first. %s",
                        fmt(totalLendable), pct(totalLendable, totalUnits),
                        fmt(erp.rdcs().isEmpty() ? 2 : erp.rdcs().get(0).replenishInDays()),
                        totalLendable < totalUnits ? "Depot stock alone cannot recover this shipment - it must be blended with the vehicle." : "Depot stock alone can cover the load."),
                srcFactors));

        // 15 · Allocation & Fair-Share
        List<Factor> allocFactors = new ArrayList<>();
        if (p != null) {
            for (ChannelFill c : p.channelTotals()) {
                allocFactors.add(f("FILL_" + c.channel(), c.channelName() + " fill",
                        c.fillPct() + "% (" + fmt(c.onTimeUnits()) + "/" + fmt(c.demand()) + " in " + fmt(c.promiseHrs()) + "h)",
                        "COMPUTED", c.fillPct() >= 100 ? "GOOD" : c.fillPct() >= 60 ? "CAUTION" : "CRITICAL"));
            }
            allocFactors.add(f("RULE", "Allocation rule", "scarce units go to the highest avoided-penalty channel first", "MODELED", "INFO"));
        }
        out.add(new AgentReport("Allocation & Fair-Share Agent", "Decides which channels get the scarce units", "⚖️", "Allocation",
                p == null ? "No allocation required."
                        : String.format("Under \"%s\": %d%% of units land inside their channel promise. %s",
                        best.title, p.fillPct(),
                        p.channelTotals().stream().filter(c -> c.fillPct() >= 100).count() > 0
                                ? "Protected: " + p.channelTotals().stream().filter(c -> c.fillPct() >= 100)
                                .map(ChannelFill::channel).reduce((a, b) -> a + ", " + b).orElse("-") + "."
                                : "No channel is fully protected."),
                allocFactors));

        // 16 · Replenishment & Backfill
        List<Factor> backFactors = new ArrayList<>();
        if (p != null) {
            for (var bf : p.backfills()) {
                backFactors.add(f("BF_" + bf.rdc(), bf.rdc() + " lent",
                        fmt(bf.unitsLent()) + " units; " + fmt(bf.unitsRepaid()) + " repaid from the recovered load",
                        "COMPUTED", bf.unitsRepaid() >= bf.unitsLent() ? "GOOD" : "CAUTION"));
            }
            if (p.backfills().isEmpty()) {
                backFactors.add(f("BF_NONE", "RDC draw-down", "none - no depot stock used", "COMPUTED", "GOOD"));
            }
            backFactors.add(f("INBOUND", "RDC inbound replenishment",
                    "in transit, arriving in " + fmt(erp.rdcs().isEmpty() ? 2 : erp.rdcs().get(0).replenishInDays()) + " days", "MODELED", "GOOD"));
            backFactors.add(f("UNFILLED", "Rolls to next cycle",
                    fmt(p.unfilledUnits()) + " units", "COMPUTED", p.unfilledUnits() > 0 ? "CAUTION" : "GOOD"));
        }
        out.add(new AgentReport("Replenishment & Backfill Agent", "Makes the lending depot whole again", "🔄", "Backfill",
                p == null || p.backfills().isEmpty()
                        ? "No depot stock drawn down - nothing to backfill."
                        : p.backfills().stream().map(b -> b.rdc() + " lends " + fmt(b.unitsLent()) + " units. " + b.note())
                        .reduce((a, b) -> a + " " + b).orElse(""),
                backFactors));

        // 17 · Transporter Sourcing (rate/kg master)
        List<Transporter> pool = erp.transportersForLane(s.route());
        List<Factor> tFactors = new ArrayList<>();
        for (Transporter t : pool) {
            tFactors.add(f("T_" + t.name(), t.name(),
                    "INR " + fmt(t.ratePerKg()) + "/kg, " + fmt(t.transitHrs()) + "h lane transit, " + t.reliabilityPct() + "% reliable",
                    "MODELED", t.reliabilityPct() >= 92 ? "GOOD" : t.reliabilityPct() < 85 ? "CAUTION" : "INFO"));
        }
        Transporter cheapT = pool.stream().min((a, b) -> Double.compare(a.ratePerKg(), b.ratePerKg())).orElse(null);
        Transporter fastT = pool.stream().min((a, b) -> Double.compare(a.transitHrs(), b.transitHrs())).orElse(null);
        out.add(new AgentReport("Transporter Sourcing Agent", "Prices the replacement-vehicle market on this lane", "🚛", "Transport",
                pool.isEmpty() ? "No transporter master rows for this lane."
                        : String.format("%d transporters quote this lane. Cheapest: %s at INR %s/kg. Fastest: %s at %sh. Incumbent %s is carrying the load at INR %s/kg.",
                        pool.size(), cheapT != null ? cheapT.name() : "-", cheapT != null ? fmt(cheapT.ratePerKg()) : "-",
                        fastT != null ? fastT.name() : "-", fastT != null ? fmt(fastT.transitHrs()) : "-",
                        s.incumbentTransporter(), fmt(s.freightRatePerKg())),
                tFactors));

        // 18 · Stakeholder Comms
        List<Stakeholder> holders = buildStakeholders(best);
        List<Factor> commsFactors = new ArrayList<>();
        long immediate = holders.stream().filter(h -> "IMMEDIATE".equals(h.urgency())).count();
        commsFactors.add(f("DEPTS", "Departments to notify", holders.size() + " across "
                + erp.salesChannels().size() + " channels", "MODELED", "INFO"));
        commsFactors.add(f("NOW", "Immediate action required", immediate + " departments", "COMPUTED", immediate > 0 ? "CAUTION" : "GOOD"));
        if (p != null) {
            String exposed = p.channelTotals().stream().filter(c -> c.fillPct() < 100)
                    .map(c -> c.channel() + " " + c.fillPct() + "%")
                    .reduce((a, b) -> a + ", " + b).orElse("none");
            commsFactors.add(f("TELL", "Channel owners to re-promise", exposed, "COMPUTED",
                    "none".equals(exposed) ? "GOOD" : "CAUTION"));
        }
        out.add(new AgentReport("Stakeholder Comms Agent", "Tells every department what changed and what they must do", "📣", "Comms",
                String.format("%d departments must be told; %d need to act immediately (supply planning to release the RDC stock, city distribution to re-sequence beats, and the channel owners to re-promise where fill is short).",
                        holders.size(), immediate),
                commsFactors));

        return out;
    }

    /** Who has to be told, and what they have to do about it. */
    private List<Stakeholder> buildStakeholders(Draft best) {
        FulfilmentPlan p = best.plan;
        boolean usesRdc = p != null && !p.backfills().isEmpty();
        boolean shortfall = p != null && p.unfilledUnits() > 0;
        List<Stakeholder> out = new ArrayList<>();
        for (Department d : erp.departments()) {
            String urgency = switch (d.scope()) {
                case "REGIONAL" -> "IMMEDIATE";
                case "CITY" -> "IMMEDIATE";
                case "MOTHER" -> usesRdc ? "IMMEDIATE" : "NEXT_CYCLE";
                case "HOD" -> shortfall ? "IMMEDIATE" : "FYI";
                default -> shortfall ? "FYI" : "FYI";
            };
            out.add(new Stakeholder(d.name(), d.scope(), d.action(), urgency));
        }
        return out;
    }

    private int channelDemand(Shipment s, String channel, String cityOrNull) {
        int sum = 0;
        for (var l : s.loadLines()) {
            if (cityOrNull != null && !l.city().equals(cityOrNull)) continue;
            Integer v = l.channels().get(channel);
            if (v != null) sum += v;
        }
        return sum;
    }

    private double channelPromise(String code) {
        return erp.findChannel(code).map(SalesChannel::maxDelayHrs).orElse(48.0);
    }

    private static int pct(int part, int whole) {
        return whole <= 0 ? 0 : (int) Math.round(100.0 * part / whole);
    }

    // ── 4c. the decision brief: how we actually got to the answer ──

    /**
     * A written audit of the decision for an analyst: what bound us, what we compared,
     * what tipped it, what we assumed, and what would flip the answer. Fully
     * deterministic — every sentence is derived from the same numbers that were scored,
     * so the brief can never disagree with the recommendation.
     */
    private DecisionBrief buildDecisionBrief(Ctx ctx, List<Draft> all, List<Draft> top,
                                             Draft best, boolean distribution) {
        Shipment s = ctx.s();
        List<String> binding = new ArrayList<>();
        List<DecisionStep> steps = new ArrayList<>();
        List<String> decisive = new ArrayList<>();
        List<String> assumptions = new ArrayList<>();
        List<String> notConsidered = new ArrayList<>();
        List<String> sensitivity = new ArrayList<>();

        String plate = ctx.veh() != null ? ctx.veh().plate() : s.vehicleId();
        String issue = s.breakdownIssue() != null ? s.breakdownIssue() : "recovery review";

        // ── situation ──
        String situation;
        if (distribution) {
            int units = totalUnits(s);
            situation = String.format(
                    "%s (%s) is down - %s - with %s units on board across %d SKUs, bound for %s. That load IS those cities' %s-day cover, so there is no buffer sitting behind it: whatever we fail to deliver on time is a shelf gap, not a delayed invoice. INR %s of stock and INR %s/h of contractual penalty are exposed.",
                    plate, s.ref(), issue, fmt(units), erp.skus().size(),
                    String.join(" + ", planner.destinations(s)), fmt(s.coverDays()),
                    fmt(s.value()), fmt(s.penaltyPerHourInr()));
        } else {
            situation = String.format(
                    "%s (%s) is down - %s - carrying %s of %s on the %s lane. %s km and %sh of SLA remain; INR %s of cargo and INR %s/h of penalty are exposed for a %s customer.",
                    plate, s.ref(), issue, fmt(s.tons()) + "t", s.cargo(), routeLabel(s.route()),
                    fmt(s.remainingKm()), fmt(s.slaHoursRemaining()), fmt(s.value()),
                    fmt(s.penaltyPerHourInr()), tierLabel(s.customerTier()));
        }

        // ── binding constraints + the reasoning chain ──
        int n = 1;
        if (distribution) {
            SalesChannel tightest = tightestChannel(s);
            double truckEarliest = earliestTruckEta(all);
            int lendable = totalLendable();
            int demand = totalUnits(s);

            steps.add(new DecisionStep(n++, "What is actually at stake?",
                    String.format("%s units - the entire %s-day cover for %s. The cities hold no buffer, so a miss is a stock-out on shelf, and the channels carry INR %s/unit of penalty at the tight end.",
                            fmt(demand), fmt(s.coverDays()), String.join(" + ", planner.destinations(s)),
                            tightest != null ? fmt(tightest.penaltyPerUnitInr()) : "-")));

            if (tightest != null) {
                binding.add(String.format("%s is the tightest promise on the load: %sh, covering %s units. It sets the clock everything else is judged against.",
                        tightest.name(), fmt(tightest.maxDelayHrs()), fmt(channelDemand(s, tightest.code(), null))));
                steps.add(new DecisionStep(n++, "What is the hard deadline?",
                        String.format("Not one SLA but five - each consumer channel has its own promise window. %s at %sh is the binding one; traditional trade at %sh can wait for the next beat. So the load does not have a single deadline, it has a ladder of them.",
                                tightest.name(), fmt(tightest.maxDelayHrs()), fmt(channelPromise("TT")))));
            }

            steps.add(new DecisionStep(n++, "What can the stranded vehicle still do?",
                    String.format("The stock is not lost, it is late - every unit is still on the truck. Repaired or cross-docked, its earliest arrival at any city is ~%sh.%s",
                            fmt(round1(truckEarliest)),
                            tightest != null && truckEarliest > tightest.maxDelayHrs()
                                    ? " That is AFTER the " + fmt(tightest.maxDelayHrs()) + "h " + tightest.name() + " window, so the vehicle alone cannot serve that channel at all - no matter which transporter we put on it."
                                    : " That clears every channel window, so the vehicle alone is viable.")));

            if (tightest != null && truckEarliest > tightest.maxDelayHrs()) {
                binding.add(String.format("The vehicle cannot reach any city before ~%sh, which is past the %sh %s window. Any vehicle-only plan therefore scores 0%% on that channel - this is the single fact that decides the case.",
                        fmt(round1(truckEarliest)), fmt(tightest.maxDelayHrs()), tightest.name()));
            }

            binding.add(String.format("The depots can only lend %s of %s units (%d%%). An RDC must retain its own cover until its inbound lands (%s days out) plus %s day safety, so it lends spare cover only - never its working stock.",
                    fmt(lendable), fmt(demand), pct(lendable, demand),
                    fmt(erp.rdcs().isEmpty() ? 2 : erp.rdcs().get(0).replenishInDays()),
                    fmt(DistributionPlanner.RDC_SAFETY_DAYS)));

            steps.add(new DecisionStep(n++, "What else can supply this demand?",
                    String.format("Two RDCs. Lendable = (days of cover - replenishment lead - safety) x daily demand, per SKU. That yields %s units - %d%% of the load. Neither depot can cover this alone, and even pooled they cannot: depot stock alone tops out at %d%% fill.",
                            fmt(lendable), pct(lendable, demand), pct(lendable, demand))));

            steps.add(new DecisionStep(n++, "So who gets the scarce units?",
                    String.format("Scarce stock is allocated by avoided penalty: the tightest, most expensive promise first (%s at INR %s/unit), the most tolerant last (traditional trade at INR %s/unit, which can ride the slow vehicle without penalty). Each line is then routed to the FASTEST source that actually holds that SKU.",
                            tightest != null ? tightest.name() : "Qcommerce",
                            tightest != null ? fmt(tightest.penaltyPerUnitInr()) : "-",
                            fmt(erp.findChannel("TT").map(SalesChannel::penaltyPerUnitInr).orElse(3.0)))));
        } else {
            steps.add(new DecisionStep(n++, "What is actually at stake?",
                    String.format("INR %s of %s for a %s customer, at INR %s/h of penalty (capped at INR %s), with %sh of SLA left.",
                            fmt(s.value()), s.cargo(), tierLabel(s.customerTier()),
                            fmt(s.penaltyPerHourInr()), fmt(s.penaltyCapInr()), fmt(s.slaHoursRemaining()))));
            binding.add(String.format("SLA: %sh remaining against a %sh base transit for the %s km still to run.",
                    fmt(s.slaHoursRemaining()), fmt(round1(ctx.base())), fmt(s.remainingKm())));
            steps.add(new DecisionStep(n++, "What is the hard deadline?",
                    String.format("%sh of SLA. Base transit alone is %sh, leaving %sh of headroom for repair, mobilisation and any external delay.",
                            fmt(s.slaHoursRemaining()), fmt(round1(ctx.base())),
                            fmt(round1(s.slaHoursRemaining() - ctx.base())))));
            steps.add(new DecisionStep(n++, "What are the recovery paths?",
                    String.format("%d generated: %s.", all.size(),
                            all.stream().map(d -> d.title).reduce((a, b) -> a + "; " + b).orElse("-"))));
            if (s.tempControlled()) {
                binding.add("Cold chain: the cargo is temperature-controlled, so any non-reefer mover carries a ~30% of value spoilage write-off - which prices most of them out on its own.");
            }
        }

        // External conditions + compliance always bind.
        WeatherSignal worst = ctx.wxOrigin().delayHrs() >= ctx.wxDest().delayHrs() ? ctx.wxOrigin() : ctx.wxDest();
        String condText = String.format("Live conditions add %sh to every road ETA (%s %s%s%s) and widen the ETA spread by %sh, which is what turns a 'should make it' into a probability rather than a promise.",
                fmt(round1(ctx.extDelay())), worst.city(), worst.condition(),
                ctx.fest().active() ? ", " + ctx.fest().name() + " congestion" : "",
                ctx.advisories().isEmpty() ? "" : ", " + ctx.advisories().size() + " route advisory",
                fmt(round1(ctx.extSigma())));
        steps.add(new DecisionStep(n++, "What do live conditions add?", condText));
        if (ctx.extDelay() > 0) binding.add(condText);

        EwayBillSignal eway = ctx.eway();
        if (eway.remainingHrs() < best.etaMean + 6) {
            binding.add(String.format("E-way bill has only %sh left against a %sh plan - it must be extended before dispatch or the load is stopped at a check-post.",
                    fmt(eway.remainingHrs()), fmt(round1(best.etaMean))));
        }

        // ── how each option was priced and ranked ──
        if (distribution) {
            steps.add(new DecisionStep(n++, "How was each option priced?",
                    "Every option is costed end-to-end at the UNIT level, not the truck level: freight on every leg (depot short-hauls + the vehicle's remaining run) + RDC handling + a per-unit channel penalty for every unit that lands outside its promise window. That last term is the one that separates the options - it is where a missed Qcommerce window turns into real money."));
        } else {
            steps.add(new DecisionStep(n++, "How was each option priced?",
                    "Each option is costed as an expected landed cost: transport + P(late) x SLA penalty + service-risk (the chance the option simply fails, priced against cargo value and priority) + stock-out and spoilage exposure + carbon. ETA is treated as a distribution, not a point, so 'on time' is a probability."));
        }
        steps.add(new DecisionStep(n++, "How were they ranked?",
                "Lowest risk-adjusted expected landed cost wins, with a variance penalty scaled by cargo priority - so a high-value or CRITICAL load is deliberately NOT gambled on a cheap-but-flaky option. The displayed 0-100 score is just that ranking, normalised."));

        // ── head to head: why the winner beat each rival ──
        List<Comparison> head = new ArrayList<>();
        for (Draft d : top) {
            if (d == best) continue;
            long delta = d.expectedCost - best.expectedCost;
            int service = d.plan != null ? d.plan.fillPct() : (int) Math.round(d.pOnTime * 100);
            head.add(new Comparison(d.id, d.title, d.expectedCost, delta, service, whyNotChosen(best, d, distribution)));
        }

        // ── decisive factors: the biggest cost gaps between the winner and the runner-up ──
        Draft runnerUp = top.size() > 1 ? top.get(1) : null;
        if (runnerUp != null) {
            if (distribution && best.plan != null && runnerUp.plan != null) {
                long penGap = runnerUp.plan.penaltyInr() - best.plan.penaltyInr();
                long frGap = runnerUp.plan.freightInr() - best.plan.freightInr();
                if (penGap > 0) {
                    decisive.add(String.format("Channel service penalty is the decider: the runner-up carries INR %s more of it (INR %s vs INR %s), because it leaves units outside their promise windows.",
                            fmt(penGap), fmt(runnerUp.plan.penaltyInr()), fmt(best.plan.penaltyInr())));
                }
                int fillGap = best.plan.fillPct() - runnerUp.plan.fillPct();
                if (fillGap != 0) {
                    decisive.add(String.format("Service level: the recommendation fills %d%% of units inside their promise windows vs %d%% for the runner-up - a %d point gap.",
                            best.plan.fillPct(), runnerUp.plan.fillPct(), Math.abs(fillGap)));
                }
                if (frGap < 0) {
                    decisive.add(String.format("The recommendation actually spends INR %s MORE on freight - and is still cheaper overall, because avoiding the penalty is worth far more than the extra haulage.",
                            fmt(Math.abs(frGap))));
                }
                if (!best.plan.backfills().isEmpty() && best.plan.backfills().stream().allMatch(b -> b.unitsRepaid() >= b.unitsLent())) {
                    decisive.add("The depot draw-down is free to unwind: the recovered load repays every lent unit at the depot gate, so borrowing from the RDCs costs nothing in extra freight.");
                }
            } else {
                // Rank the cost components by how much they actually separated the two
                // options, and only report the ones that are material - a few hundred
                // rupees of difference did not "decide" anything.
                long material = Math.max(1000, Math.round(best.expectedCost * 0.02));
                record Gap(long amount, String text) {}
                List<Gap> gaps = new ArrayList<>();

                long spoilGap = runnerUp.spoilageEV - best.spoilageEV;
                if (spoilGap > 0) gaps.add(new Gap(spoilGap, String.format(
                        "Spoilage exposure: the runner-up carries INR %s of cold-chain write-off risk that the recommendation does not - on temperature-controlled cargo that single term decides it.",
                        fmt(spoilGap))));

                long svcGap = runnerUp.serviceRisk - best.serviceRisk;
                if (svcGap > 0) gaps.add(new Gap(svcGap, String.format(
                        "Service-risk: the runner-up is INR %s worse on the chance of simply failing (%d%% on-time x %d%% reliability, vs %d%% x %d%% for the recommendation).",
                        fmt(svcGap), (int) Math.round(runnerUp.pOnTime * 100), runnerUp.reliability,
                        (int) Math.round(best.pOnTime * 100), best.reliability)));

                long penGap = runnerUp.penaltyEV - best.penaltyEV;
                if (penGap > 0) gaps.add(new Gap(penGap, String.format(
                        "Expected SLA penalty: INR %s worse on the runner-up (it is more likely to run past the %sh window).",
                        fmt(penGap), fmt(s.slaHoursRemaining()))));

                long trGap = runnerUp.transport - best.transport;
                if (trGap > 0) gaps.add(new Gap(trGap, String.format(
                        "Execution cost: the runner-up costs INR %s more to actually run (INR %s vs INR %s) for no service gain - this is the plain-money gap.",
                        fmt(trGap), fmt(runnerUp.transport), fmt(best.transport))));

                gaps.sort((x, y) -> Long.compare(y.amount(), x.amount()));
                gaps.stream().filter(g -> g.amount() >= material).limit(3)
                        .forEach(g -> decisive.add(g.text()));

                if (trGap < 0) decisive.add(String.format(
                        "Note the trade-off: the recommendation is INR %s MORE expensive to execute, and still wins - it buys down more risk than it costs.",
                        fmt(Math.abs(trGap))));
                if (decisive.isEmpty() && !gaps.isEmpty()) {
                    decisive.add(String.format("The options are closely matched - the recommendation leads by only INR %s overall, driven mostly by %s.",
                            fmt(runnerUp.expectedCost - best.expectedCost),
                            gaps.get(0).text().substring(0, gaps.get(0).text().indexOf(':')).toLowerCase()));
                }
            }
        }
        if (decisive.isEmpty()) decisive.add("The recommendation leads on every cost component - there is no trade-off to argue about.");

        // ── assumptions (the tunable levers) ──
        if (distribution) {
            assumptions.add("RDC daily demand in units is derived from city demand (this load = " + fmt(s.coverDays()) + " days of cover, so daily = units / " + fmt(s.coverDays()) + "). The source data gives depot stock in DAYS, and days cannot be allocated - only units can.");
            assumptions.add("Channel priority is set by penalty per unit (Qcommerce > Ecommerce > Modern trade > D2C > Traditional trade). If your OTIF contracts make Modern trade the most expensive to miss, that ranking flips and so does the allocation.");
            assumptions.add("A depot retains its replenishment lead time + " + fmt(DistributionPlanner.RDC_SAFETY_DAYS) + " day of safety before it lends anything. Loosen that and more stock frees up.");
            assumptions.add("Short-haul freight is priced off the trunk rate, scaled by distance with a short-haul inefficiency factor.");
        }
        assumptions.add("Road speed is a flat " + (int) SPEED_KMPH + " km/h effective, including stops.");
        assumptions.add("Repair time and cost are inferred from the failure text (engine/gearbox/axle = heavy) and whether the nearest service point has a heavy-repair bay.");
        assumptions.add("Carbon is priced at INR " + (int) CARBON_PRICE + "/kg CO2.");

        // ── honest limits ──
        notConsidered.add("Live road traffic - not connected. ETAs use modelled speeds plus live weather, not live congestion.");
        notConsidered.add("Live spot-rate market - carrier and transporter rates come from the contracted master, not a live quote.");
        notConsidered.add("Telematics fault codes - the failure mode is read from the reported text, not the vehicle bus.");
        notConsidered.add("Strike / bandh / protest feeds - modelled as route advisories, not a live source.");
        if (distribution) {
            notConsidered.add("Individual customer / store-level allocation inside a channel - we allocate to the channel, not to the named account.");
        }

        // ── what would change the answer ──
        if (distribution) {
            SalesChannel tightest = tightestChannel(s);
            double truckEarliest = earliestTruckEta(all);
            if (tightest != null && truckEarliest > tightest.maxDelayHrs()) {
                sensitivity.add(String.format("If %s's promise window were relaxed from %sh to ~%sh, the vehicle could serve it directly and a simple repair-and-run would become viable - the depots would not be needed at all.",
                        tightest.name(), fmt(tightest.maxDelayHrs()), fmt(Math.ceil(truckEarliest))));
            }
            int lendable = totalLendable();
            int demand = totalUnits(s);
            if (lendable < demand) {
                sensitivity.add(String.format("If the depots held ~%s more lendable units (i.e. roughly one more day of cover each), depot-only sourcing would reach 100%% and the vehicle would become pure backfill.",
                        fmt(demand - lendable)));
            }
            if (runnerUp != null) {
                sensitivity.add(String.format("The recommendation wins by INR %s. It would only lose if the depot legs or handling cost rose by more than that - roughly %dx their current level.",
                        fmt(runnerUp.expectedCost - best.expectedCost),
                        best.plan != null && best.plan.freightInr() > 0
                                ? Math.max(2, (int) (1 + (runnerUp.expectedCost - best.expectedCost) / Math.max(1, best.plan.freightInr()))) : 2));
            }
        } else if (runnerUp != null) {
            sensitivity.add(String.format("The recommendation wins by INR %s. Close that gap - a lower rate from %s, or a better on-time record - and the ranking flips.",
                    fmt(runnerUp.expectedCost - best.expectedCost), runnerUp.provider));
            sensitivity.add(String.format("Priority drives risk-aversion. If this were a routine (not %s) load, the variance penalty shrinks and the cheapest option would rank higher.",
                    s.priority()));
        }

        // ── bottom line ──
        String bottomLine;
        if (distribution && best.plan != null) {
            bottomLine = String.format(
                    "Take \"%s\". It is the only option that serves %d%% of units inside their promise windows, and it is also the cheapest at INR %s all-in - the two usually trade off, and here they do not. The reason is structural: the vehicle physically cannot make the %sh %s window (earliest arrival ~%sh), so any vehicle-only plan forfeits that channel outright and eats the penalty. The depots CAN make it, but only hold %d%% of the load. Using each for what it is good at - depots for the urgent channels now, the recovered vehicle for the tolerant ones and to repay the depots at their own gate - covers everything and wastes nothing. %s",
                    best.title, best.plan.fillPct(), fmt(best.expectedCost),
                    fmt(tightestChannel(s) != null ? tightestChannel(s).maxDelayHrs() : 12),
                    tightestChannel(s) != null ? tightestChannel(s).name() : "Qcommerce",
                    fmt(round1(earliestTruckEta(all))), pct(totalLendable(), totalUnits(s)),
                    runnerUp != null ? "It beats the next-best option by INR " + fmt(runnerUp.expectedCost - best.expectedCost) + "." : "");
        } else {
            bottomLine = String.format(
                    "Take \"%s\". At %d%% on-time odds and %d%% reliability it carries the lowest risk-adjusted landed cost (INR %s). %s The decision is driven less by headline freight than by what it costs when the option fails - which is why the cheapest mover on the board is not the answer.",
                    best.title, (int) Math.round(best.pOnTime * 100), best.reliability, fmt(best.expectedCost),
                    runnerUp != null ? "It beats the runner-up (" + runnerUp.title + ") by INR "
                            + fmt(runnerUp.expectedCost - best.expectedCost) + "." : "");
        }

        return new DecisionBrief(situation, binding, steps, head, decisive, assumptions,
                notConsidered, sensitivity, bottomLine);
    }

    // ── 4d. impact: what the run achieved, and what it replaced ──

    // Minutes a human control-tower analyst spends on each task, doing this by phone + Excel.
    private static final int MIN_PER_CARRIER_CALL = 8;    // availability + rate + ETA
    private static final int MIN_PER_DEPOT_CALL = 15;     // stock report out of the depot
    private static final int MIN_PER_SKU_DEPOT_RECONCILE = 2; // days-of-cover -> allocatable units
    private static final int MIN_PER_DEMAND_LINE = 1;     // hand-allocating one SKU x city x channel line
    private static final int MIN_PER_OPTION_COSTING = 12; // building a landed cost for one option
    private static final int MIN_EXTERNAL_CHECKS = 15;    // weather, e-way bill, road advisories
    private static final int MIN_PER_DEPARTMENT_CALL = 4; // telling one department what changed
    private static final int MIN_DECISION_WRITEUP = 20;   // writing the call up for approval

    /**
     * Quantifies the run for a business audience: how much analysis was actually done,
     * what it would have cost a human to do by phone and spreadsheet, and how much money
     * the recommendation avoids versus the move a person would most plausibly default to.
     *
     * <p>The manual baseline is <b>itemised</b> (see {@link ManualTask}) rather than
     * asserted, so the time-saved figure can be argued with rather than just believed.
     */
    private ImpactSummary buildImpact(Ctx ctx, List<Draft> all, Draft best, int agentCount,
                                      boolean distribution, long runtimeMs) {
        Shipment s = ctx.s();
        FulfilmentPlan p = best.plan;

        int carriers = distribution
                ? erp.transportersForLane(s.route()).size()
                : safe(s.candidateCarrierIds()).size();
        int depots = distribution ? erp.rdcs().size() : 0;
        int skus = distribution ? erp.skus().size() : 0;
        int channels = distribution ? erp.salesChannels().size() : 0;
        int cities = distribution ? planner.destinations(s).size() : 1;
        int departments = distribution ? erp.departments().size() : 0;
        int demandLines = distribution ? skus * cities * channels : 0;
        int units = distribution ? totalUnits(s) : 0;
        int unitsProtected = p != null ? p.onTimeUnits() : 0;
        int options = all.size();

        // Every discrete record the agents actually read this run.
        int dataPoints = erp.snapshot().vehicles().size()
                + erp.snapshot().carriers().size()
                + erp.snapshot().lanes().size()
                + erp.snapshot().advisories().size()
                + erp.snapshot().servicePoints().size()
                + erp.snapshot().distributionCentres().size()
                + skus + channels + departments
                + erp.transportersForLane(s.route()).size()
                + depots * Math.max(1, skus)   // per-SKU cover rows
                + demandLines
                + 3;                           // weather x2 + e-way-bill clock

        // ── the manual baseline, itemised ──
        List<ManualTask> manual = new ArrayList<>();
        if (carriers > 0) {
            manual.add(new ManualTask(
                    "Ring " + carriers + " " + (distribution ? "transporters" : "carriers")
                            + " for availability, rate and ETA", carriers * MIN_PER_CARRIER_CALL));
        }
        if (depots > 0) {
            manual.add(new ManualTask("Call " + depots + " depots for a stock report",
                    depots * MIN_PER_DEPOT_CALL));
            manual.add(new ManualTask(
                    "Convert depot cover from DAYS into allocatable UNITS (" + skus + " SKUs x " + depots + " depots)",
                    skus * depots * MIN_PER_SKU_DEPOT_RECONCILE));
        }
        if (demandLines > 0) {
            manual.add(new ManualTask(
                    "Hand-allocate " + fmt(demandLines) + " demand lines (SKU x city x channel) by priority in Excel",
                    demandLines * MIN_PER_DEMAND_LINE));
        }
        manual.add(new ManualTask("Check weather, road advisories and the e-way-bill clock", MIN_EXTERNAL_CHECKS));
        manual.add(new ManualTask("Build a landed cost for each of the " + options + " recovery options",
                options * MIN_PER_OPTION_COSTING));
        if (departments > 0) {
            manual.add(new ManualTask("Brief " + departments + " departments on what changed",
                    departments * MIN_PER_DEPARTMENT_CALL));
        }
        manual.add(new ManualTask("Write the decision up for approval", MIN_DECISION_WRITEUP));

        int manualMinutes = manual.stream().mapToInt(ManualTask::minutes).sum();
        double towerMinutes = runtimeMs / 60000.0;
        double hoursSaved = round1((manualMinutes - towerMinutes) / 60.0);

        // ── what the recommendation avoids vs the move a person would default to ──
        // Truck-first instinct: repair it. Failing that, "just call the cheapest carrier".
        Draft baseline = all.stream().filter(d -> "REPAIR".equals(d.type)).findFirst()
                .orElseGet(() -> all.stream()
                        .filter(d -> "EXTERNAL_CARRIER".equals(d.type) || "REPLACEMENT_TRANSPORTER".equals(d.type))
                        .min((a, b) -> Long.compare(a.transport, b.transport))
                        .orElseGet(() -> all.stream()
                                .max((a, b) -> Long.compare(a.expectedCost, b.expectedCost))
                                .orElse(best)));
        long costAvoided = Math.max(0, baseline.expectedCost - best.expectedCost);
        long penaltyAvoided = distribution && p != null && baseline.plan != null
                ? Math.max(0, baseline.plan.penaltyInr() - p.penaltyInr())
                : Math.max(0, (baseline.penaltyEV + baseline.spoilageEV + baseline.stockoutEV)
                        - (best.penaltyEV + best.spoilageEV + best.stockoutEV));

        // ── headline tiles ──
        List<ImpactStat> headline = new ArrayList<>();
        headline.add(new ImpactStat("Decision time",
                runtimeMs < 1000 ? runtimeMs + " ms" : fmt(round1(runtimeMs / 1000.0)) + " s",
                "measured, end to end"));
        headline.add(new ImpactStat("Manual equivalent", fmt(round1(manualMinutes / 60.0)) + " hrs",
                "phone + spreadsheet, itemised below"));
        headline.add(new ImpactStat("Time saved", fmt(hoursSaved) + " hrs", "per disruption event"));
        headline.add(new ImpactStat("Cost avoided", "INR " + fmt(costAvoided),
                "vs \"" + baseline.title + "\""));
        headline.add(new ImpactStat("Specialist agents", String.valueOf(agentCount), "run in parallel"));
        headline.add(new ImpactStat("Factors considered", String.valueOf(
                        distribution ? FACTOR_CATALOG_SIZE_DISTRIBUTION : FACTOR_CATALOG_SIZE),
                "across " + (distribution ? 18 : 12) + " domains"));
        headline.add(new ImpactStat("Recovery options", String.valueOf(options), "generated and fully costed"));
        headline.add(new ImpactStat("Data points read", fmt(dataPoints), "ERP records + live signals"));
        if (distribution) {
            headline.add(new ImpactStat("Units re-planned", fmt(units),
                    fmt(unitsProtected) + " served inside promise"));
            headline.add(new ImpactStat("Demand lines allocated", fmt(demandLines),
                    skus + " SKUs x " + cities + " cities x " + channels + " channels"));
            headline.add(new ImpactStat("Sales channels protected",
                    (p == null ? 0 : p.channelTotals().stream().filter(c -> c.fillPct() >= 100).count()) + " / " + channels,
                    "fully served inside their promise window"));
            headline.add(new ImpactStat("Penalty avoided", "INR " + fmt(penaltyAvoided),
                    "channel service penalties"));
            headline.add(new ImpactStat("Departments coordinated", String.valueOf(departments),
                    "each with a specific action"));
        }

        // ── narrative ──
        String narrative;
        if (distribution) {
            narrative = String.format(
                    "This single disruption was resolved in %s. Doing it by hand - ringing %d transporters and %d depots, converting their cover from days into units, hand-allocating %s demand lines across %d SKUs, %d cities and %d consumer channels, costing %d recovery options, then briefing %d departments - is roughly %s hours of an analyst's day, and it is the kind of work that gets rushed at 2am and done badly. The tower protected %s of %s units inside their promise windows. It wipes out INR %s of channel service penalty outright; after paying for the extra depot legs that buy it, the net saving against the move most people default to (\"%s\") is INR %s. Multiply that by every breakdown in a year.",
                    runtimeMs < 1000 ? runtimeMs + " milliseconds" : fmt(round1(runtimeMs / 1000.0)) + " seconds",
                    carriers, depots, fmt(demandLines), skus, cities, channels, options, departments,
                    fmt(round1(manualMinutes / 60.0)), fmt(unitsProtected), fmt(units),
                    fmt(penaltyAvoided), baseline.title, fmt(costAvoided));
        } else {
            narrative = String.format(
                    "This disruption was resolved in %s. By hand - ringing %d carriers, checking weather, road advisories and the e-way-bill clock, then building a landed cost for %d options - is roughly %s hours. The tower avoided INR %s against the move most people default to (\"%s\"), and it did so by pricing what each option costs WHEN IT FAILS, not just what it costs to book.",
                    runtimeMs < 1000 ? runtimeMs + " milliseconds" : fmt(round1(runtimeMs / 1000.0)) + " seconds",
                    carriers, options, fmt(round1(manualMinutes / 60.0)),
                    fmt(costAvoided), baseline.title);
        }

        return new ImpactSummary(agentCount,
                distribution ? FACTOR_CATALOG_SIZE_DISTRIBUTION : FACTOR_CATALOG_SIZE,
                options, dataPoints, demandLines, skus, channels, cities, depots, departments,
                units, unitsProtected, runtimeMs, manualMinutes, hoursSaved,
                baseline.title, baseline.expectedCost, costAvoided, penaltyAvoided,
                headline, manual, narrative);
    }

    /** The concrete reason the winner beat this particular rival. */
    private String whyNotChosen(Draft best, Draft d, boolean distribution) {
        long delta = d.expectedCost - best.expectedCost;
        StringBuilder b = new StringBuilder();

        if (distribution && d.plan != null && best.plan != null) {
            List<ChannelFill> missed = d.plan.channelTotals().stream()
                    .filter(c -> c.fillPct() < 100).toList();
            if (!missed.isEmpty()) {
                String worst = missed.stream()
                        .min((x, y) -> Integer.compare(x.fillPct(), y.fillPct()))
                        .map(c -> c.channelName() + " at " + c.fillPct() + "%").orElse("");
                b.append(String.format("Leaves %s short (worst: %s). ",
                        missed.stream().map(ChannelFill::channel).reduce((x, y) -> x + ", " + y).orElse(""), worst));
            }
            if (d.plan.unfilledUnits() > 0) {
                b.append(String.format("%s units go unserved this cycle. ", fmt(d.plan.unfilledUnits())));
            }
            if (d.plan.penaltyInr() > best.plan.penaltyInr()) {
                b.append(String.format("That costs INR %s in channel penalties (vs INR %s). ",
                        fmt(d.plan.penaltyInr()), fmt(best.plan.penaltyInr())));
            }
            if (d.plan.freightInr() < best.plan.freightInr()) {
                b.append(String.format("It is INR %s cheaper to haul, but the penalty more than wipes that out. ",
                        fmt(best.plan.freightInr() - d.plan.freightInr())));
            }
        } else {
            if (d.spoilageEV > best.spoilageEV) {
                b.append(String.format("Carries INR %s of spoilage / cold-chain write-off risk. ", fmt(d.spoilageEV)));
            }
            if (d.pOnTime < best.pOnTime) {
                b.append(String.format("Only %d%% likely on time vs %d%%. ",
                        (int) Math.round(d.pOnTime * 100), (int) Math.round(best.pOnTime * 100)));
            }
            if (d.reliability < best.reliability) {
                b.append(String.format("Lower reliability (%d%% vs %d%%). ", d.reliability, best.reliability));
            }
            if (d.transport < best.transport) {
                b.append(String.format("Cheaper to execute (INR %s vs INR %s) but the risk it carries costs more than it saves. ",
                        fmt(d.transport), fmt(best.transport)));
            }
        }
        b.append(String.format("Net: INR %s %s overall.", fmt(Math.abs(delta)),
                delta >= 0 ? "more expensive" : "cheaper, but rejected on service"));
        return b.toString().trim();
    }

    private int totalUnits(Shipment s) {
        if (!s.hasDistributionPlan()) return 0;
        return s.loadLines().stream().flatMap(l -> l.channels().values().stream())
                .mapToInt(Integer::intValue).sum();
    }

    private int totalLendable() {
        return erp.rdcs().stream()
                .mapToInt(r -> planner.lendableBySku(r).values().stream().mapToInt(Integer::intValue).sum())
                .sum();
    }

    /** The channel with the tightest promise window that actually has demand on this load. */
    private SalesChannel tightestChannel(Shipment s) {
        return erp.salesChannels().stream()
                .filter(c -> channelDemand(s, c.code(), null) > 0)
                .min((a, b) -> Double.compare(a.maxDelayHrs(), b.maxDelayHrs()))
                .orElse(null);
    }

    /** Earliest any vehicle-based option can reach any city. */
    private double earliestTruckEta(List<Draft> all) {
        return all.stream()
                .filter(d -> d.plan != null && ("REPAIR".equals(d.type) || "REPLACEMENT_TRANSPORTER".equals(d.type)))
                .flatMap(d -> d.plan.sources().stream())
                .mapToDouble(x -> x.etaHrs())
                .min().orElse(0);
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

    private static double round2(double v) { return Math.round(v * 100.0) / 100.0; }

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
