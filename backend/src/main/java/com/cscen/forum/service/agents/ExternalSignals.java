package com.cscen.forum.service.agents;

import com.cscen.forum.service.erp.ErpPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Live external signals the External Conditions agent factors in. These are
 * genuinely live where a free source exists — they change between runs:
 * <ul>
 *   <li><b>Weather</b> — Open-Meteo current conditions per city (free, no key).</li>
 *   <li><b>Festivals</b> — a date-driven Indian festival calendar (congestion windows).</li>
 *   <li><b>E-way bill</b> — a computed validity clock (1 day / 200 km basis, India).</li>
 * </ul>
 * Every call is fail-safe: on network error / offline it returns a MODELED
 * fallback so the tower still produces a full decision.
 */
@Service
public class ExternalSignals {

    private static final Logger log = LoggerFactory.getLogger(ExternalSignals.class);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    /** source ∈ LIVE | MODELED | COMPUTED */
    public record WeatherSignal(String city, double tempC, double precipMm, String condition,
                                double delayHrs, double sigmaHrs, String source) {}

    public record FestivalSignal(String name, int daysAway, boolean active, double delayHrs, String source) {}

    public record EwayBillSignal(double validityHrs, double remainingHrs, boolean expired, String source) {}

    private record Festival(String name, LocalDate date) {}

    // A curated set of major Indian festivals with heavy road/labour impact (2026).
    private static final List<Festival> FESTIVALS = List.of(
            new Festival("Holi", LocalDate.of(2026, 3, 3)),
            new Festival("Gudi Padwa", LocalDate.of(2026, 3, 19)),
            new Festival("Ram Navami", LocalDate.of(2026, 3, 26)),
            new Festival("Independence Day", LocalDate.of(2026, 8, 15)),
            new Festival("Ganesh Chaturthi", LocalDate.of(2026, 9, 14)),
            new Festival("Dussehra", LocalDate.of(2026, 10, 20)),
            new Festival("Diwali", LocalDate.of(2026, 11, 8)),
            new Festival("Christmas", LocalDate.of(2026, 12, 25))
    );

    private final HttpClient http;
    private final ObjectMapper mapper;

    public ExternalSignals(ObjectMapper mapper) {
        this.mapper = mapper;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();
    }

    /** Live current weather for a city; MODELED fallback (no added delay) on any failure. */
    public WeatherSignal weather(ErpPort.City city) {
        if (city == null) {
            return new WeatherSignal("unknown", 0, 0, "unknown", 0, 0.4, "MODELED");
        }
        try {
            String url = "https://api.open-meteo.com/v1/forecast"
                    + "?latitude=" + city.lat() + "&longitude=" + city.lon()
                    + "&current=temperature_2m,precipitation,weather_code&timezone=auto";
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(6)).GET().build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) {
                return modeledWeather(city.name());
            }
            JsonNode cur = mapper.readTree(res.body()).path("current");
            double temp = cur.path("temperature_2m").asDouble(0);
            double precip = cur.path("precipitation").asDouble(0);
            int code = cur.path("weather_code").asInt(0);
            String condition = weatherCodeLabel(code);
            double delay = weatherDelayHrs(code, precip);
            double sigma = weatherSigmaHrs(code, precip);
            return new WeatherSignal(city.name(), round1(temp), round1(precip), condition, delay, sigma, "LIVE");
        } catch (Exception e) {
            log.debug("Open-Meteo weather lookup failed for {} ({}); using modeled fallback", city.name(), e.getMessage());
            return modeledWeather(city.name());
        }
    }

    private WeatherSignal modeledWeather(String cityName) {
        return new WeatherSignal(cityName, 0, 0, "unavailable (modeled)", 0, 0.5, "MODELED");
    }

    /** Nearest upcoming festival; active (congestion) when within 2 days either side of today. */
    public FestivalSignal nextFestival() {
        LocalDate today = LocalDate.now(IST);
        Festival nearest = null;
        long nearestAbs = Long.MAX_VALUE;
        for (Festival f : FESTIVALS) {
            long days = ChronoUnit.DAYS.between(today, f.date());
            if (days < -1) continue; // already passed
            if (Math.abs(days) < nearestAbs) {
                nearestAbs = Math.abs(days);
                nearest = f;
            }
        }
        if (nearest == null) {
            return new FestivalSignal("none upcoming", -1, false, 0, "LIVE");
        }
        int daysAway = (int) ChronoUnit.DAYS.between(today, nearest.date());
        boolean active = daysAway >= -1 && daysAway <= 2;
        double delay = active ? 4.0 : 0.0;
        return new FestivalSignal(nearest.name(), daysAway, active, delay, "LIVE");
    }

    /**
     * E-way-bill validity clock. India basis: 1 day per 200 km of consignment
     * distance. Computed live against the current time, so it moves every run.
     */
    public EwayBillSignal eWayBill(double laneDistanceKm, double issuedHoursAgo) {
        long validityDays = Math.max(1, (long) Math.ceil(laneDistanceKm / 200.0));
        double validityHrs = validityDays * 24.0;
        double remaining = round1(validityHrs - issuedHoursAgo);
        return new EwayBillSignal(validityHrs, remaining, remaining <= 0, "COMPUTED");
    }

    // ── weather-code helpers (WMO codes) ──

    private static String weatherCodeLabel(int code) {
        if (code == 0) return "clear";
        if (code <= 3) return "partly cloudy";
        if (code == 45 || code == 48) return "fog";
        if (code >= 51 && code <= 57) return "drizzle";
        if (code >= 61 && code <= 67) return "rain";
        if (code >= 71 && code <= 77) return "snow";
        if (code >= 80 && code <= 82) return "rain showers";
        if (code >= 95) return "thunderstorm";
        return "cloudy";
    }

    private static double weatherDelayHrs(int code, double precipMm) {
        double d = 0;
        if (code == 45 || code == 48) d += 2.0;              // fog
        if (code >= 95) d += 3.0;                             // thunderstorm
        else if (code >= 80 && code <= 82) d += 1.5;         // showers
        else if (code >= 61 && code <= 67) d += 1.5;         // rain
        else if (code >= 51 && code <= 57) d += 0.5;         // drizzle
        if (precipMm >= 10) d += 1.5;
        else if (precipMm >= 3) d += 0.5;
        return round1(Math.min(d, 6.0));
    }

    private static double weatherSigmaHrs(int code, double precipMm) {
        if (code >= 95 || precipMm >= 10) return 1.8;
        if (code >= 61 || precipMm >= 3) return 1.0;
        if (code == 45 || code == 48) return 1.2;
        return 0.4;
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
