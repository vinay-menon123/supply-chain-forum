package com.cscen.forum.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Vendor sourcing analysis: a buyer describes what they need (e.g. "pallet racks for a
 * warehouse") and a region, and this returns where to buy it and a rough market price.
 *
 * <p><b>What's real vs estimated (we never overclaim):</b>
 * <ul>
 *   <li><b>Marketplace links</b> are <i>deterministic and live</i> — real search URLs into
 *       IndiaMART, TradeIndia, Amazon Business, Alibaba and Google Shopping for the exact
 *       requirement + region. These always work and let the buyer verify listings themselves.</li>
 *   <li><b>Price band, vendors, MOQ and lead times</b> are an <b>AI market estimate</b> from the
 *       model's knowledge, not a live scrape (B2B marketplaces are anti-bot / ToS-gated). Clearly
 *       labelled as indicative, with a templated fallback so the feature works even with no AI key.</li>
 * </ul>
 * Same discipline as the AI control tower: structure is deterministic, the LLM only estimates,
 * and every run degrades gracefully.
 */
@Service
public class SourcingService {

    private static final Logger log = LoggerFactory.getLogger(SourcingService.class);

    private final AgentAiClient ai;
    private final ObjectMapper mapper;

    public SourcingService(AgentAiClient ai, ObjectMapper mapper) {
        this.ai = ai;
        this.mapper = mapper;
    }

    // ── output DTOs ──

    public record PriceBand(long low, long typical, long high, String unit, String currency) {}

    public record VendorSource(String name, String type, String regionCoverage, String priceRange,
                               String moq, String leadTime, String note, String confidence) {}

    public record MarketplaceLink(String marketplace, String url, String note) {}

    public record SourcingReport(String requirement, String region, String category, String specSummary,
                                 PriceBand priceBand, List<VendorSource> vendors,
                                 List<MarketplaceLink> marketplaceLinks, List<String> costDrivers,
                                 List<String> buyingTips, String summary, boolean aiPowered,
                                 String aiProvider, String dataBasis) {}

    public boolean isAiEnabled() { return ai.isEnabled(); }

    public String aiProvider() { return ai.provider(); }

    // ── entry point ──

    public SourcingReport analyze(String requirement, String region, String quantity, String unitHint) {
        String req = requirement == null ? "" : requirement.trim();
        String reg = (region == null || region.isBlank()) ? "India" : region.trim();
        if (req.isBlank()) {
            throw new IllegalArgumentException("Describe what you need to source");
        }

        // The verifiable part: real marketplace search deep-links (always available).
        List<MarketplaceLink> links = marketplaceLinks(req, reg);

        // The estimate part: AI where available, templated fallback otherwise.
        Optional<SourcingReport> estimate = ai.isEnabled()
                ? aiEstimate(req, reg, quantity, unitHint, links)
                : Optional.empty();
        return estimate.orElseGet(() -> templatedReport(req, reg, links));
    }

    // ── 1. deterministic marketplace deep-links ──

    private List<MarketplaceLink> marketplaceLinks(String requirement, String region) {
        boolean regional = region != null && !region.isBlank() && !region.equalsIgnoreCase("India");
        String q = requirement + (regional ? " " + region : "");
        String e = enc(q);
        List<MarketplaceLink> out = new ArrayList<>();
        out.add(new MarketplaceLink("IndiaMART",
                "https://dir.indiamart.com/search.mp?ss=" + e,
                "India's largest B2B marketplace - manufacturers & suppliers with quotes"));
        out.add(new MarketplaceLink("TradeIndia",
                "https://www.tradeindia.com/search.html?keyword=" + e,
                "B2B directory of Indian manufacturers and exporters"));
        out.add(new MarketplaceLink("Amazon Business",
                "https://www.amazon.in/s?k=" + e + "&rh=p_n_feature_seven_browse-bin",
                "GST-invoiced business buying with fast delivery for standard items"));
        out.add(new MarketplaceLink("Alibaba",
                "https://www.alibaba.com/trade/search?SearchText=" + e,
                "Global suppliers - useful for import price benchmarking"));
        out.add(new MarketplaceLink("Google Shopping",
                "https://www.google.com/search?tbm=shop&q=" + e,
                "Cross-site price comparison for off-the-shelf products"));
        return out;
    }

    // ── 2. AI market estimate ──

    private Optional<SourcingReport> aiEstimate(String requirement, String region, String quantity,
                                                String unitHint, List<MarketplaceLink> links) {
        String system = """
                You are a B2B procurement sourcing analyst for the Indian market. A buyer gives a
                requirement and a region. Produce a REALISTIC market estimate to help them source it:
                a price band in INR, likely vendor/supplier options, and practical buying guidance.
                Use realistic Indian market pricing. You may name well-known real manufacturers where
                genuinely relevant, but NEVER invent phone numbers, emails, exact URLs or fake company
                names. Prices are indicative estimates, not live quotes. Return ONLY minified JSON, no
                markdown, with EXACTLY this shape:
                {"category":"short product category",
                 "specSummary":"1-2 sentences on typical specs/variants that drive price",
                 "priceBand":{"low":<int INR>,"typical":<int INR>,"high":<int INR>,"unit":"per <unit>","currency":"INR"},
                 "vendors":[{"name":"...","type":"Manufacturer|Distributor|Marketplace seller|Fabricator",
                    "regionCoverage":"...","priceRange":"INR ... per <unit>","moq":"...","leadTime":"...",
                    "note":"one line","confidence":"HIGH|MEDIUM|LOW"}],
                 "costDrivers":["what moves the price"],
                 "buyingTips":["practical negotiation / spec advice"],
                 "summary":"2-3 sentence bottom line for the buyer"}
                Give 4-6 vendors mixing named manufacturers and generic supplier types. 3-5 cost drivers
                and 3-5 buying tips.
                """;
        try {
            StringBuilder user = new StringBuilder();
            user.append("REQUIREMENT: ").append(requirement).append("\nREGION: ").append(region);
            if (quantity != null && !quantity.isBlank()) user.append("\nQUANTITY: ").append(quantity.trim());
            if (unitHint != null && !unitHint.isBlank()) user.append("\nUNIT: ").append(unitHint.trim());
            user.append("\nEstimate the Indian market for this. Remember: indicative estimate, JSON only.");

            Optional<String> out = ai.complete(system, user.toString(), 1600);
            if (out.isEmpty()) return Optional.empty();
            String json = out.get().trim();
            if (json.startsWith("```")) json = json.replaceAll("^```[a-zA-Z]*\\s*", "").replaceAll("\\s*```$", "");
            JsonNode n = mapper.readTree(json);

            String category = text(n, "category", requirement);
            String specSummary = text(n, "specSummary", "");
            JsonNode pb = n.path("priceBand");
            PriceBand band = new PriceBand(
                    pb.path("low").asLong(0), pb.path("typical").asLong(0), pb.path("high").asLong(0),
                    text(pb, "unit", unitHint != null && !unitHint.isBlank() ? "per " + unitHint : "per unit"),
                    text(pb, "currency", "INR"));

            List<VendorSource> vendors = new ArrayList<>();
            for (JsonNode v : n.path("vendors")) {
                String name = text(v, "name", "");
                if (name.isBlank()) continue;
                vendors.add(new VendorSource(name, text(v, "type", "Supplier"),
                        text(v, "regionCoverage", region), text(v, "priceRange", ""),
                        text(v, "moq", ""), text(v, "leadTime", ""), text(v, "note", ""),
                        normConfidence(text(v, "confidence", "MEDIUM"))));
            }
            if (vendors.isEmpty()) return Optional.empty();

            List<String> drivers = strings(n.path("costDrivers"));
            List<String> tips = strings(n.path("buyingTips"));
            String summary = text(n, "summary", "");

            return Optional.of(new SourcingReport(requirement, region, category, specSummary, band,
                    vendors, links, drivers, tips, summary, true, ai.provider(),
                    "AI market estimate (from model knowledge; indicative, not a live scrape). "
                            + "Verify current prices and stock via the marketplace links."));
        } catch (Exception e) {
            log.warn("Sourcing AI estimate failed, using templated fallback: {}", e.getMessage());
            return Optional.empty();
        }
    }

    // ── 3. templated fallback (no AI / on error) ──

    private SourcingReport templatedReport(String requirement, String region, List<MarketplaceLink> links) {
        List<VendorSource> vendors = List.of(
                new VendorSource("IndiaMART suppliers", "Marketplace sellers", region,
                        "Request quotes", "Varies by seller", "1-3 weeks typical",
                        "Multiple suppliers respond with quotes to a single enquiry", "MEDIUM"),
                new VendorSource("TradeIndia manufacturers", "Manufacturers", region,
                        "Request quotes", "Often MOQ-based", "2-4 weeks typical",
                        "Direct-from-manufacturer, better for bulk and custom specs", "MEDIUM"),
                new VendorSource("Amazon Business", "Marketplace sellers", "Pan-India",
                        "Listed prices", "1 unit", "2-7 days",
                        "Fastest for standard, off-the-shelf items with GST invoicing", "MEDIUM"));
        return new SourcingReport(requirement, region, requirement,
                "Estimate unavailable without an AI key - use the marketplace links to check live listings and request quotes.",
                new PriceBand(0, 0, 0, "per unit", "INR"), vendors, links,
                List.of("Specification and material grade", "Order quantity / MOQ", "Region and delivery distance",
                        "Customisation vs standard product"),
                List.of("Send the same enquiry to 3-4 suppliers on IndiaMART/TradeIndia and compare",
                        "Ask for the landed price (incl. GST, freight and installation) not just ex-works",
                        "For bulk, negotiate on MOQ and payment terms, not just unit price"),
                "Use the marketplace search links below to see live suppliers and prices for \"" + requirement
                        + "\" in " + region + ", and request quotes from a few to benchmark.",
                false, ai.provider(),
                "AI estimate unavailable - showing verified marketplace search links only.");
    }

    // ── helpers ──

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static String text(JsonNode n, String field, String fallback) {
        String v = n.path(field).asText("").trim();
        return v.isBlank() ? fallback : v;
    }

    private static List<String> strings(JsonNode arr) {
        List<String> out = new ArrayList<>();
        if (arr != null && arr.isArray()) {
            for (JsonNode x : arr) {
                String s = x.asText("").trim();
                if (!s.isBlank()) out.add(s);
            }
        }
        return out;
    }

    private static String normConfidence(String c) {
        String u = c == null ? "" : c.trim().toUpperCase();
        return switch (u) {
            case "HIGH", "MEDIUM", "LOW" -> u;
            default -> "MEDIUM";
        };
    }
}
