package com.cscen.forum.service.erp;

import java.util.List;
import java.util.Optional;

/**
 * The read seam onto the enterprise system-of-record (fleet, carriers, live
 * shipments, DC stock, lanes). Today this is backed by {@link SimulatedErpAdapter}
 * over a curated dataset; a real {@code SapS4Adapter} (OData / BAPI) can implement
 * the same interface later with <b>zero change</b> to the agents or the decision
 * engine above it. That swap-ability is the whole point of the port.
 */
public interface ErpPort {

    // ── master + transactional records (plain data carriers) ──

    record City(String name, double lat, double lon) {}

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

    record Shipment(String id, String ref, String cargo, double tons, String origin, String destination,
                    String route, double remainingKm, double slaHoursRemaining, long value, String priority,
                    String vehicleId, String breakdownIssue, NearbySpare nearbySpare, List<String> candidateCarrierIds,
                    String customerTier, long penaltyPerHourInr, long penaltyCapInr, boolean perishable,
                    boolean tempControlled, double shelfLifeHrs, boolean hazmat, double eWayBillIssuedHoursAgo,
                    String skuCriticality) {}

    record Erp(String company, List<City> cities, List<Vehicle> vehicles, List<Carrier> carriers,
               List<Depot> depots, List<Lane> lanes, List<ServicePoint> servicePoints,
               List<DistributionCentre> distributionCentres, List<Advisory> advisories, List<Shipment> shipments) {}

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
}
