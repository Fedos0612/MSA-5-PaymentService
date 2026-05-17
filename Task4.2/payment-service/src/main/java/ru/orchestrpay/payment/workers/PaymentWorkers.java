package ru.orchestrpay.payment.workers;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.worker.JobClient;
import io.camunda.zeebe.client.api.worker.JobWorker;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class PaymentWorkers {

    private static final Logger log = LoggerFactory.getLogger(PaymentWorkers.class);

    private final ZeebeClient zeebeClient;
    private final List<JobWorker> workers = new ArrayList<>();

    public PaymentWorkers(ZeebeClient zeebeClient) {
        this.zeebeClient = zeebeClient;
    }

    @PostConstruct
    public void startWorkers() {
        workers.add(openWorker("create-payment", this::createPayment));
        workers.add(openWorker("debit-customer", this::debitCustomer));
        workers.add(openWorker("fraud-check", this::fraudCheck));
        workers.add(openWorker("apply-cutoff-allow", this::applyCutoffAllow));
        workers.add(openWorker("transfer-to-counterparty", this::transferToCounterparty));
        workers.add(openWorker("block-payment", this::blockPayment));
        workers.add(openWorker("refund-customer", this::refundCustomer));
        workers.add(openWorker("notify-success", this::notifySuccess));
        workers.add(openWorker("notify-decline", this::notifyDecline));
        workers.add(openWorker("send-security-alert", this::sendSecurityAlert));

        log.info("✅ All Zeebe workers started");
    }

    @PreDestroy
    public void stopWorkers() {
        for (JobWorker worker : workers) {
            worker.close();
        }
        log.info("🛑 All Zeebe workers stopped");
    }

    private JobWorker openWorker(String type, WorkerHandler handler) {
        return zeebeClient
                .newWorker()
                .jobType(type)
                .handler((jobClient, job) -> {
                    try {
                        handler.handle(jobClient, job);
                    } catch (Exception exception) {
                        log.error("❌ worker={} failed, jobKey={}, error={}",
                                type,
                                job.getKey(),
                                exception.getMessage(),
                                exception
                        );

                        jobClient
                                .newFailCommand(job.getKey())
                                .retries(Math.max(job.getRetries() - 1, 0))
                                .errorMessage(exception.getMessage())
                                .send()
                                .join();
                    }
                })
                .name(type + "-worker")
                .maxJobsActive(10)
                .open();
    }

    private void createPayment(JobClient jobClient, ActivatedJob job) {
        Map<String, Object> variables = job.getVariablesAsMap();
        String paymentId = getString(variables, "paymentId");
        String sagaId = getString(variables, "sagaId");
        String scenario = getString(variables, "scenario");

        log.info("▶ worker=create-payment started paymentId={}, sagaId={}, scenario={}",
                paymentId,
                sagaId,
                scenario
        );

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("paymentStatus", "CREATED");
        result.put("createPaymentCompletedAt", OffsetDateTime.now().toString());

        complete(jobClient, job, result);

        log.info("✅ worker=create-payment completed paymentId={}", paymentId);
    }

    private void debitCustomer(JobClient jobClient, ActivatedJob job) {
        Map<String, Object> variables = job.getVariablesAsMap();
        String paymentId = getString(variables, "paymentId");
        String scenario = getString(variables, "scenario");

        log.info("▶ worker=debit-customer started paymentId={}, amount={}, customerId={}",
                paymentId,
                variables.get("amount"),
                variables.get("customerId")
        );

        String debitResult = "debit-fail".equals(scenario) ? "FAILED" : "SUCCESS";

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("debitResult", debitResult);
        result.put("paymentStatus", "SUCCESS".equals(debitResult) ? "DEBITED" : "DEBIT_FAILED");
        result.put("debitCompletedAt", OffsetDateTime.now().toString());

        complete(jobClient, job, result);

        log.info("✅ worker=debit-customer completed paymentId={}, debitResult={}",
                paymentId,
                debitResult
        );
    }

    private void fraudCheck(JobClient jobClient, ActivatedJob job) {
        Map<String, Object> variables = job.getVariablesAsMap();
        String paymentId = getString(variables, "paymentId");
        String scenario = getString(variables, "scenario");

        log.info("▶ worker=fraud-check started paymentId={}, scenario={}", paymentId, scenario);

        String fraudDecision = switch (scenario) {
            case "deny" -> "DENY";
            case "manual-allow", "manual-deny", "manual-cutoff-fast" -> "MANUAL_REVIEW";
            default -> "ALLOW";
        };

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("fraudDecision", fraudDecision);
        result.put("fraudCheckedAt", OffsetDateTime.now().toString());

        complete(jobClient, job, result);

        log.info("✅ worker=fraud-check completed paymentId={}, fraudDecision={}",
                paymentId,
                fraudDecision
        );
    }

    private void applyCutoffAllow(JobClient jobClient, ActivatedJob job) {
        Map<String, Object> variables = job.getVariablesAsMap();
        String paymentId = getString(variables, "paymentId");

        log.info("▶ worker=apply-cutoff-allow started paymentId={}", paymentId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("cutoffApplied", true);
        result.put("approvalReason", "CUTOFF_TIMEOUT");
        result.put("manualDecision", "ALLOW");
        result.put("paymentStatus", "APPROVED_BY_CUTOFF");
        result.put("cutoffAppliedAt", OffsetDateTime.now().toString());

        complete(jobClient, job, result);

        log.info("✅ worker=apply-cutoff-allow completed paymentId={}", paymentId);
    }

    private void transferToCounterparty(JobClient jobClient, ActivatedJob job) {
        Map<String, Object> variables = job.getVariablesAsMap();
        String paymentId = getString(variables, "paymentId");
        String scenario = getString(variables, "scenario");

        log.info("▶ worker=transfer-to-counterparty started paymentId={}, counterpartyId={}, amount={}",
                paymentId,
                variables.get("counterpartyId"),
                variables.get("amount")
        );

        String transferResult = "transfer-fail".equals(scenario) ? "FAILED" : "SUCCESS";

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("transferResult", transferResult);
        result.put("paymentStatus", "SUCCESS".equals(transferResult) ? "TRANSFERRED" : "TRANSFER_FAILED");
        result.put("transferCompletedAt", OffsetDateTime.now().toString());

        complete(jobClient, job, result);

        log.info("✅ worker=transfer-to-counterparty completed paymentId={}, transferResult={}",
                paymentId,
                transferResult
        );
    }

    private void blockPayment(JobClient jobClient, ActivatedJob job) {
        Map<String, Object> variables = job.getVariablesAsMap();
        String paymentId = getString(variables, "paymentId");

        log.info("▶ worker=block-payment started paymentId={}", paymentId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("paymentStatus", "BLOCKED");
        result.put("blockedAt", OffsetDateTime.now().toString());

        complete(jobClient, job, result);

        log.info("✅ worker=block-payment completed paymentId={}", paymentId);
    }

    private void refundCustomer(JobClient jobClient, ActivatedJob job) {
        Map<String, Object> variables = job.getVariablesAsMap();
        String paymentId = getString(variables, "paymentId");

        log.info("▶ worker=refund-customer started paymentId={}, customerId={}, amount={}",
                paymentId,
                variables.get("customerId"),
                variables.get("amount")
        );

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("refundResult", "SUCCESS");
        result.put("paymentStatus", "REFUNDED");
        result.put("refundCompletedAt", OffsetDateTime.now().toString());

        complete(jobClient, job, result);

        log.info("✅ worker=refund-customer completed paymentId={}", paymentId);
    }

    private void notifySuccess(JobClient jobClient, ActivatedJob job) {
        Map<String, Object> variables = job.getVariablesAsMap();
        String paymentId = getString(variables, "paymentId");

        log.info("▶ worker=notify-success started paymentId={}", paymentId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("clientNotification", "SUCCESS_NOTIFICATION_SENT");
        result.put("paymentStatus", "SUCCEEDED");
        result.put("successNotificationSentAt", OffsetDateTime.now().toString());

        complete(jobClient, job, result);

        log.info("✅ worker=notify-success completed paymentId={}", paymentId);
    }

    private void notifyDecline(JobClient jobClient, ActivatedJob job) {
        Map<String, Object> variables = job.getVariablesAsMap();
        String paymentId = getString(variables, "paymentId");

        log.info("▶ worker=notify-decline started paymentId={}", paymentId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("clientNotification", "DECLINE_OR_REFUND_NOTIFICATION_SENT");
        result.put("declineNotificationSentAt", OffsetDateTime.now().toString());

        complete(jobClient, job, result);

        log.info("✅ worker=notify-decline completed paymentId={}", paymentId);
    }

    private void sendSecurityAlert(JobClient jobClient, ActivatedJob job) {
        Map<String, Object> variables = job.getVariablesAsMap();
        String paymentId = getString(variables, "paymentId");

        log.info("▶ worker=send-security-alert started paymentId={}", paymentId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("securityAlert", "SENT");
        result.put("securityAlertSentAt", OffsetDateTime.now().toString());

        complete(jobClient, job, result);

        log.info("✅ worker=send-security-alert completed paymentId={}", paymentId);
    }

    private void complete(JobClient jobClient, ActivatedJob job, Map<String, Object> variables) {
        jobClient
                .newCompleteCommand(job.getKey())
                .variables(variables)
                .send()
                .join();
    }

    private String getString(Map<String, Object> variables, String key) {
        Object value = variables.get(key);
        return value == null ? "" : value.toString();
    }

    @FunctionalInterface
    private interface WorkerHandler {
        void handle(JobClient jobClient, ActivatedJob job);
    }
}