package com.cscen.forum.web;

import com.cscen.forum.security.CurrentUser;
import com.cscen.forum.service.SourcingService;
import com.cscen.forum.service.SourcingService.SourcingReport;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Vendor sourcing: describe a requirement + region, get live marketplace search links
 * and an AI market-price estimate. Decision-support only - see {@link SourcingService}
 * for what's real (the links) vs estimated (prices).
 */
@RestController
@RequestMapping("/api/sourcing")
public class SourcingController {

    private final SourcingService sourcing;
    private final CurrentUser currentUser;

    public SourcingController(SourcingService sourcing, CurrentUser currentUser) {
        this.sourcing = sourcing;
        this.currentUser = currentUser;
    }

    /** Mode + a few example requirements to prime the UI. */
    @GetMapping("/config")
    public Map<String, Object> config(HttpServletRequest http) {
        currentUser.requireUser(http);
        return Map.of(
                "aiEnabled", sourcing.isAiEnabled(),
                "aiProvider", sourcing.aiProvider(),
                "defaultRegion", "India",
                "examples", List.of(
                        "Pallet racks for a 20,000 sq ft warehouse",
                        "Reach truck / forklift (3-ton)",
                        "Corrugated shipping cartons, 12x10x8 inch",
                        "Reefer container transport, Chennai to Kochi",
                        "Barcode label printers (industrial)",
                        "Stretch wrap film for pallet wrapping"));
    }

    public record AnalyzeRequest(String requirement, String region, String quantity, String unit) {}

    /** Run the sourcing analysis for a requirement. */
    @PostMapping("/analyze")
    public SourcingReport analyze(@RequestBody AnalyzeRequest request, HttpServletRequest http) {
        currentUser.requireActiveUser(http);
        if (request == null || request.requirement() == null || request.requirement().isBlank()) {
            throw ApiException.badRequest("Describe what you need to source");
        }
        try {
            return sourcing.analyze(request.requirement(), request.region(),
                    request.quantity(), request.unit());
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest(e.getMessage());
        }
    }
}
