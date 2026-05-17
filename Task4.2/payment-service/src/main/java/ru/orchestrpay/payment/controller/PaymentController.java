package ru.orchestrpay.payment.controller;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.response.ProcessInstanceEvent;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/payments")
public class PaymentController {

    private static final String BPMN_PROCESS_ID = "orchestrpay-payment-process";

    private final ZeebeClient zeebeClient;

    public PaymentController(ZeebeClient zeebeClient) {
        this.zeebeClient = zeebeClient;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "UP");
        response.put("service", "payment-service");
        response.put("time", OffsetDateTime.now().toString());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/start")
    public ResponseEntity<StartPaymentResponse> startPayment(
            @RequestParam(name = "scenario", defaultValue = "allow") String scenario,
            @RequestParam(name = "amount", defaultValue = "1250.00") BigDecimal amount,
            @RequestParam(name = "customerId", defaultValue = "customer-001") String customerId,
            @RequestParam(name = "counterpartyId", defaultValue = "merchant-001") String counterpartyId
    ) {
        String normalizedScenario = scenario.trim().toLowerCase();
        String paymentId = "pay-" + UUID.randomUUID();
        String sagaId = "saga-" + UUID.randomUUID();

        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("paymentId", paymentId);
        variables.put("sagaId", sagaId);
        variables.put("scenario", normalizedScenario);
        variables.put("amount", amount);
        variables.put("customerId", customerId);
        variables.put("counterpartyId", counterpartyId);
        variables.put("manualReviewTimeout", resolveManualReviewTimeout(normalizedScenario));
        variables.put("manualDecision", resolveManualDecision(normalizedScenario));
        variables.put("createdAt", OffsetDateTime.now().toString());

        ProcessInstanceEvent event = zeebeClient
                .newCreateInstanceCommand()
                .bpmnProcessId(BPMN_PROCESS_ID)
                .latestVersion()
                .variables(variables)
                .send()
                .join();

        StartPaymentResponse response = new StartPaymentResponse(
                paymentId,
                sagaId,
                event.getProcessInstanceKey(),
                BPMN_PROCESS_ID,
                normalizedScenario,
                amount,
                customerId,
                counterpartyId,
                variables.get("manualReviewTimeout").toString()
        );

        return ResponseEntity.ok(response);
    }

    private String resolveManualReviewTimeout(String scenario) {
        if ("manual-cutoff-fast".equals(scenario)) {
            return "PT30S";
        }
        return "PT20M";
    }

    private String resolveManualDecision(String scenario) {
        if ("manual-deny".equals(scenario)) {
            return "DENY";
        }
        return "ALLOW";
    }

    public record StartPaymentResponse(
            String paymentId,
            String sagaId,
            long processInstanceKey,
            String bpmnProcessId,
            String scenario,
            BigDecimal amount,
            String customerId,
            String counterpartyId,
            String manualReviewTimeout
    ) {
    }
}