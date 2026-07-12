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
 * {@code resources/erp/erp-mock.json} (fleet, carriers, live shipments, lanes,
 * plus the distribution network: SKUs, sales channels, RDC cover, transporter
 * master, departments), loaded once at startup. Stands in for a real SAP/ERP
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
            log.info("Loaded simulated ERP: {} vehicles, {} carriers, {} shipments, {} lanes, {} SKUs, {} channels, {} RDCs, {} transporters",
                    loaded.vehicles().size(), loaded.carriers().size(), loaded.shipments().size(),
                    loaded.lanes().size(), size(loaded.skus()), size(loaded.salesChannels()),
                    size(loaded.rdcs()), size(loaded.transporters()));
        } catch (Exception e) {
            log.error("Failed to load simulated ERP data: {}", e.getMessage(), e);
            loaded = new Erp("(unavailable)", List.of(), List.of(), List.of(), List.of(), List.of(),
                    List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                    List.of(), List.of());
        }
        this.erp = loaded;
    }

    private static int size(List<?> l) {
        return l == null ? 0 : l.size();
    }

    private static <T> List<T> safe(List<T> l) {
        return l == null ? List.of() : l;
    }

    @Override
    public Erp snapshot() {
        return erp;
    }

    @Override
    public Optional<Shipment> findShipment(String id) {
        return safe(erp.shipments()).stream().filter(s -> s.id().equals(id)).findFirst();
    }

    @Override
    public Optional<Carrier> findCarrier(String id) {
        return safe(erp.carriers()).stream().filter(c -> c.id().equals(id)).findFirst();
    }

    @Override
    public Optional<Vehicle> findVehicle(String id) {
        return safe(erp.vehicles()).stream().filter(v -> v.id().equals(id)).findFirst();
    }

    @Override
    public Optional<Lane> findLane(String route) {
        return safe(erp.lanes()).stream().filter(l -> l.route().equals(route)).findFirst();
    }

    @Override
    public Optional<City> findCity(String name) {
        return safe(erp.cities()).stream().filter(c -> c.name().equalsIgnoreCase(name)).findFirst();
    }

    @Override
    public Optional<ServicePoint> findServicePoint(String city) {
        return safe(erp.servicePoints()).stream().filter(s -> s.city().equalsIgnoreCase(city)).findFirst();
    }

    @Override
    public Optional<DistributionCentre> findDc(String city) {
        return safe(erp.distributionCentres()).stream().filter(d -> d.city().equalsIgnoreCase(city)).findFirst();
    }

    @Override
    public List<Advisory> advisoriesForRoute(String route) {
        return safe(erp.advisories()).stream().filter(a -> route.equals(a.route())).toList();
    }

    // ── distribution side ──

    @Override
    public List<Sku> skus() {
        return safe(erp.skus());
    }

    @Override
    public Optional<Sku> findSku(String code) {
        return skus().stream().filter(s -> s.code().equalsIgnoreCase(code)).findFirst();
    }

    @Override
    public List<SalesChannel> salesChannels() {
        return safe(erp.salesChannels());
    }

    @Override
    public Optional<SalesChannel> findChannel(String code) {
        return salesChannels().stream().filter(c -> c.code().equalsIgnoreCase(code)).findFirst();
    }

    @Override
    public List<Department> departments() {
        return safe(erp.departments());
    }

    @Override
    public List<Rdc> rdcs() {
        return safe(erp.rdcs());
    }

    @Override
    public Optional<Rdc> findRdc(String city) {
        return rdcs().stream().filter(r -> r.city().equalsIgnoreCase(city)).findFirst();
    }

    @Override
    public List<Transporter> transportersForLane(String lane) {
        return safe(erp.transporters()).stream()
                .filter(t -> t.lane() == null || t.lane().equalsIgnoreCase(lane))
                .toList();
    }
}
