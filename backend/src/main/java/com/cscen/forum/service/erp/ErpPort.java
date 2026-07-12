package com.cscen.forum.service.erp;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The read seam onto the enterprise system-of-record (fleet, carriers, live
 * shipments, DC stock, lanes, and the distribution network: SKUs, sales channels,
 * RDC cover, transporter master, departments). Today this is backed by
 * {@link SimulatedErpAdapter} over a curated dataset; a real {@code SapS4Adapter}
 * (OData / BAPI) can implement the same interface later with <b>zero change</b> to
 * the agents or the decision engine above it.
 */
public interface ErpPort {

    // ── network / master data ──

    record City(String name, double lat, double lon) {}

    record Sku(String code, String name, double unitWeightKg, double unitValueInr) {}

    /** Consumer channel (Modern trade, Qcommerce, ...) with its service economics. */
    record SalesChannel(String code, String name, int priority, double maxDelayHrs,
                        double penaltyPerUnitInr, String note) {}

    /** A department that must be told what changed, and what it has to do about it. */
    record Department(int sl, String name, String scope, String action) {}

    /** Days of forward cover an RDC holds for one SKU, plus the sales rate that consumes it. */
    record RdcCover(String sku, double daysOfCover, int dailyDemandUnits) {}

    record RdcLeg(String toCity, double distanceKm, double transitHrs) {}

    /** A regional distribution centre that can lend stock to a stranded city. */
    record Rdc(String city, String region, double replenishInDays, String replenishStatus,
               String note, List<RdcCover> cover, List<RdcLeg> legs) {}

    /** Transporter master row: rate per kg + transit time on a lane. */
    record Transporter(String name, String lane, double ratePerKg, double transitHrs,
                       int reliabilityPct, String note) {}

    // ── fleet / carriers / lanes ──

    record Vehicle(String id, String plate, String type, double capacityTons, String currentCity,
                   String status, String driver, double driverHosLeftHrs, int fuelPct, int ageYears,
                   int failureRatePct, boolean reefer, String note) {}

    record Carrier(String id, String name, double costPerKm, int reliabilityPct, int avgResponseHrs,
                   double claimsRatePct, boolean spot, boolean compliant, List<String> lanes, String note) {}

    record NearbySpare(String vehicleId, String fromCity, double repositionKm) {}

    record Depot(String city, String note) {}

    record Lane(String route, double distanceKm, long tollInr, boolean ghatSection, int checkposts,
                boolean hasRail, boolean hasAir, double altRouteKm, double railTerminalHrs, double airCutoffHrs) {}

    record ServicePoint(String city, boolean heavyRepair, String note) {}

    record DistributionCentre(String city, int onHandUnits, int dailyDemandUnits,
                              String altSourceCity, double altSourceLeadHrs) {}

    record Advisory(String area, String route, String type, String severity, double delayHrs, String message) {}

    /** One line of the consignment: SKU x destination city, split across sales channels. */
    record LoadLine(String sku, String city, Map<String, Integer> channels) {}

    record Shipment(String id, String ref, String cargo, double tons, String origin, String destination,
                    String route, double remainingKm, double slaHoursRemaining, long value, String priority,
                    String vehicleId, String breakdownIssue, NearbySpare nearbySpare, List<String> candidateCarrierIds,
                    String customerTier, long penaltyPerHourInr, long penaltyCapInr, boolean perishable,
                    boolean tempControlled, double shelfLifeHrs, boolean hazmat, double eWayBillIssuedHoursAgo,
                    String skuCriticality,
                    // distribution-side fields (null/empty on simple single-drop shipments)
                    String breakdownNearCity, String incumbentTransporter, double freightRatePerKg,
                    double coverDays, List<String> destinationCities, List<LoadLine> loadLines) {

        /** True when this consignment carries a SKU x city x channel breakdown to plan against. */
        public boolean hasDistributionPlan() {
            return loadLines != null && !loadLines.isEmpty();
        }
    }

    record Erp(String company, List<City> cities, List<Sku> skus, List<SalesChannel> salesChannels,
               List<Department> departments, List<Rdc> rdcs, List<Transporter> transporters,
               List<Vehicle> vehicles, List<Carrier> carriers, List<Depot> depots, List<Lane> lanes,
               List<ServicePoint> servicePoints, List<DistributionCentre> distributionCentres,
               List<Advisory> advisories, List<Shipment> shipments) {}

    // ── queries the agents use ──

    Erp snapshot();

    Optional<Shipment> findShipment(String id);

    Optional<Carrier> findCarrier(String id);

    Optional<Vehicle> findVehicle(String id);

    Optional<Lane> findLane(String route);

    Optional<City> findCity(String name);

    Optional<ServicePoint> findServicePoint(String city);

    Optional<DistributionCentre> findDc(String city);

    List<Advisory> advisoriesForRoute(String route);

    // distribution side
    List<Sku> skus();

    Optional<Sku> findSku(String code);

    List<SalesChannel> salesChannels();

    Optional<SalesChannel> findChannel(String code);

    List<Department> departments();

    List<Rdc> rdcs();

    Optional<Rdc> findRdc(String city);

    /** Transporters quoting the given lane. */
    List<Transporter> transportersForLane(String lane);
}
