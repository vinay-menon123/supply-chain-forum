package com.cscen.forum.service.erp;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.List;
import java.util.Optional;

/**
 * {@link ErpPort} backed by a curated simulated dataset in
 * {@code resources/erp/erp-mock.json} (fleet, carriers, live shipments, DC stock,
 * lanes, service points), loaded once at startup. Stands in for a real SAP/ERP
 * integration — see {@link ErpPort} for the swap-in seam.
 */
@Service
public class SimulatedErpAdapter implements ErpPort {

    private static final Logger log = LoggerFactory.getLogger(SimulatedErpAdapter.class);

    private final Erp erp;

    public SimulatedErpAdapter(ObjectMapper objectMapper) {
        Erp loaded;
        try (InputStream in = getClass().getResourceAsStream("/erp/erp-mock.json")) {
            if (in == null) {
                throw new IllegalStateException("erp-mock.json not found on classpath");
            }
            loaded = objectMapper.readValue(in, Erp.class);
            log.info("Loaded simulated ERP: {} vehicles, {} carriers, {} shipments, {} lanes, {} DCs",
                    loaded.vehicles().size(), loaded.carriers().size(), loaded.shipments().size(),
                    loaded.lanes().size(), loaded.distributionCentres().size());
        } catch (Exception e) {
            log.error("Failed to load simulated ERP data: {}", e.getMessage(), e);
            loaded = new Erp("(unavailable)", List.of(), List.of(), List.of(), List.of(),
                    List.of(), List.of(), List.of(), List.of(), List.of());
        }
        this.erp = loaded;
    }

    @Override
    public Erp snapshot() {
        return erp;
    }

    @Override
    public Optional<Shipment> findShipment(String id) {
        return erp.shipments().stream().filter(s -> s.id().equals(id)).findFirst();
    }

    @Override
    public Optional<Carrier> findCarrier(String id) {
        return erp.carriers().stream().filter(c -> c.id().equals(id)).findFirst();
    }

    @Override
    public Optional<Vehicle> findVehicle(String id) {
        return erp.vehicles().stream().filter(v -> v.id().equals(id)).findFirst();
    }

    @Override
    public Optional<Lane> findLane(String route) {
        return erp.lanes().stream().filter(l -> l.route().equals(route)).findFirst();
    }

    @Override
    public Optional<City> findCity(String name) {
        return erp.cities().stream().filter(c -> c.name().equalsIgnoreCase(name)).findFirst();
    }

    @Override
    public Optional<ServicePoint> findServicePoint(String city) {
        return erp.servicePoints().stream().filter(s -> s.city().equalsIgnoreCase(city)).findFirst();
    }

    @Override
    public Optional<DistributionCentre> findDc(String city) {
        return erp.distributionCentres().stream().filter(d -> d.city().equalsIgnoreCase(city)).findFirst();
    }

    @Override
    public List<Advisory> advisoriesForRoute(String route) {
        return erp.advisories().stream().filter(a -> route.equals(a.route())).toList();
    }
}
