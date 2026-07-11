package com.cscen.forum.web;

import com.cscen.forum.security.CurrentUser;
import com.cscen.forum.service.AgentOrchestrator;
import com.cscen.forum.service.AgentOrchestrator.AgentRun;
import com.cscen.forum.service.erp.ErpPort;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * AI-agent "control tower": read the ERP snapshot, then run the multi-agent
 * decision engine on a disrupted shipment to get ranked, evidenced recovery options.
 */
@RestController
@RequestMapping("/api/agents")
public class AgentController {

    private final ErpPort erp;
    private final AgentOrchestrator orchestrator;
    private final CurrentUser currentUser;

    public AgentController(ErpPort erp, AgentOrchestrator orchestrator, CurrentUser currentUser) {
        this.erp = erp;
        this.orchestrator = orchestrator;
        this.currentUser = currentUser;
    }

    /** The ERP/SAP snapshot the agents read from + the tower's live-analysis mode. */
    @GetMapping("/erp")
    public Map<String, Object> erpSnapshot(HttpServletRequest http) {
        currentUser.requireUser(http);
        return Map.of("erp", erp.snapshot(),
                "aiEnabled", orchestrator.isAiEnabled(),
                "aiProvider", orchestrator.aiProvider());
    }

    /** Intake payload: a shipment to recover + an optional free-text description of what happened. */
    public record RunRequest(String shipmentId, String disruption) {}

    /** Run the control tower on a shipment and return ranked recovery options. */
    @PostMapping("/run")
    public AgentRun run(@RequestBody RunRequest request, HttpServletRequest http) {
        currentUser.requireActiveUser(http);
        if (request == null || request.shipmentId() == null || request.shipmentId().isBlank()) {
            throw ApiException.badRequest("shipmentId is required");
        }
        try {
            return orchestrator.run(request.shipmentId().trim(), request.disruption());
        } catch (IllegalArgumentException e) {
            throw ApiException.notFound(e.getMessage());
        }
    }
}
