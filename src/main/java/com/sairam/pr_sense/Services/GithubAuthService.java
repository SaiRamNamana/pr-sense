package com.sairam.pr_sense.Services;

import io.jsonwebtoken.Jwts;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.Security;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

@Service
public class GithubAuthService {

    private static final Logger log = LoggerFactory.getLogger(GithubAuthService.class);

    @Value("${github.app.id}")
    private String appId;

    @Value("${github.app.private-key}")
    private String privateKeySource;

    private final RestTemplate restTemplate;

    public GithubAuthService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public String generateAppJwt() throws Exception {
        PrivateKey privateKey = loadPrivateKey();
        Instant now = Instant.now();

        return Jwts.builder()
                .issuedAt(Date.from(now.minusSeconds(60)))
                .expiration(Date.from(now.plusSeconds(600)))
                .issuer(appId)
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();
    }

    public String getInstallationAccessToken(long installationId) throws Exception {
        String jwt = generateAppJwt();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwt);
        headers.set("Accept", "application/vnd.github+json");
        headers.set("X-GitHub-Api-Version", "2022-11-28");

        HttpEntity<Void> request = new HttpEntity<>(headers);

        ResponseEntity<Map> response = restTemplate.exchange(
                "https://api.github.com/app/installations/" + installationId + "/access_tokens",
                HttpMethod.POST,
                request,
                Map.class
        );

        return (String) response.getBody().get("token");
    }

    private PrivateKey loadPrivateKey() throws Exception {
        Security.addProvider(new BouncyCastleProvider());

        String value = privateKeySource == null ? "" : privateKeySource.trim();

        // Strip surrounding quotes if present
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            value = value.substring(1, value.length() - 1);
        }

        String pem;
        if (value.startsWith("-----BEGIN")) {
            // Inline PEM: turn literal \n into real newlines
            pem = value.replace("\\n", "\n");
            log.info("Loading private key from inline PEM, length={}", pem.length());
        } else if (!value.isBlank() && Files.exists(Path.of(value))) {
            // Treat as a file path
            pem = Files.readString(Path.of(value));
            log.info("Loading private key from file {}, length={}", value, pem.length());
        } else {
            throw new IllegalStateException(
                    "GITHUB_APP_PRIVATE_KEY is neither a valid PEM nor an existing file. "
                            + "value starts with: "
                            + value.substring(0, Math.min(60, value.length())));
        }

        try (PEMParser pemParser = new PEMParser(new StringReader(pem))) {
            Object parsed = pemParser.readObject();

            if (parsed == null) {
                throw new IllegalStateException(
                        "PEMParser returned null. length=" + pem.length()
                                + " first80=[" + pem.substring(0, Math.min(80, pem.length())) + "]");
            }

            JcaPEMKeyConverter converter = new JcaPEMKeyConverter().setProvider("BC");

            if (parsed instanceof PEMKeyPair) {
                KeyPair keyPair = converter.getKeyPair((PEMKeyPair) parsed);
                return keyPair.getPrivate();
            } else if (parsed instanceof org.bouncycastle.asn1.pkcs.PrivateKeyInfo) {
                return converter.getPrivateKey((org.bouncycastle.asn1.pkcs.PrivateKeyInfo) parsed);
            } else {
                throw new IllegalStateException("Unrecognized private key format: " + parsed.getClass());
            }
        }
    }

    public String fetchPullRequestDiff(String repoFullName, int prNumber, String installationToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(installationToken);
        headers.set("Accept", "application/vnd.github.v3.diff");
        headers.set("X-GitHub-Api-Version", "2022-11-28");

        HttpEntity<Void> request = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(
                "https://api.github.com/repos/" + repoFullName + "/pulls/" + prNumber,
                HttpMethod.GET,
                request,
                String.class
        );

        return response.getBody();
    }
}