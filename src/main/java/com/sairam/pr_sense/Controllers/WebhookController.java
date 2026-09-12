package com.sairam.pr_sense.Controllers;

import com.sairam.pr_sense.DTO.InstallationEvent;
import tools.jackson.databind.ObjectMapper;
import com.sairam.pr_sense.DTO.PullRequestEvent;
import com.sairam.pr_sense.Services.PullRequestReviewOrchestrator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;

@RestController
public class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

    /** Actions worth reviewing. Everything else (labeled, closed, edited…) is ignored. */
    private static final Set<String> REVIEWABLE_ACTIONS =
            Set.of("opened", "reopened", "synchronize", "ready_for_review");

    private final ObjectMapper objectMapper;
    private final PullRequestReviewOrchestrator orchestrator;
    private final String webhookSecret;

    public WebhookController(ObjectMapper objectMapper,
                             PullRequestReviewOrchestrator orchestrator,
                             @Value("${github.webhook.secret}") String webhookSecret) {
        this.objectMapper = objectMapper;
        this.orchestrator = orchestrator;
        this.webhookSecret = webhookSecret;
    }

    // ---- Health check endpoint for Render ----
    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of(
                "status", "ok",
                "service", "pr-sense"
        );
    }

    @PostMapping("/webhook")
    public ResponseEntity<String> handleWebhook(
            @RequestBody byte[] rawBody,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
            @RequestHeader(value = "X-GitHub-Event", required = false) String eventType,
            @RequestHeader(value = "X-GitHub-Delivery", required = false) String deliveryId) throws Exception {

        if (signature == null || !isValidSignature(rawBody, signature)) {
            log.warn("Rejected webhook {} — bad signature", deliveryId);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("invalid signature");
        }

        if ("installation".equals(eventType)) {
            InstallationEvent installEvent = objectMapper.readValue(rawBody, InstallationEvent.class);
            log.info("event=installation action={} id={} account={} type={}",
                    installEvent.getAction(),
                    installEvent.getInstallation().getId(),
                    installEvent.getInstallation().getAccount().getLogin(),
                    installEvent.getInstallation().getAccount().getType());
            return ResponseEntity.accepted().body("installation recorded");
        }

        if (!"pull_request".equals(eventType)) {
            return ResponseEntity.accepted().body("ignored: " + eventType);
        }

        PullRequestEvent event = objectMapper.readValue(rawBody, PullRequestEvent.class);

        if (!REVIEWABLE_ACTIONS.contains(event.getAction())) {
            return ResponseEntity.accepted().body("ignored action: " + event.getAction());
        }
        if (event.getPullRequest() == null || event.getRepository() == null
                || event.getInstallation() == null) {
            return ResponseEntity.accepted().body("ignored: incomplete payload");
        }

        log.info("Queuing review for {}#{} ({})",
                event.getRepository().getFullName(), event.getPullRequest().getNumber(), deliveryId);

        orchestrator.handle(event);                 // returns immediately

        return ResponseEntity.accepted().body("queued");
    }

    private boolean isValidSignature(byte[] payload, String signatureHeader) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));

            String computed = "sha256=" + HexFormat.of().formatHex(mac.doFinal(payload));

            return MessageDigest.isEqual(
                    computed.getBytes(StandardCharsets.UTF_8),
                    signatureHeader.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.error("Signature verification error", e);
            return false;
        }
    }
}