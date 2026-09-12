package com.sairam.pr_sense.Services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class InstallationTokenCache {

    private static final Logger log = LoggerFactory.getLogger(InstallationTokenCache.class);

    private record CachedToken(String token, Instant expiresAt) {}

    private final Map<Long, CachedToken> cache = new ConcurrentHashMap<>();
    private final GithubAuthService authService;

    public InstallationTokenCache(GithubAuthService authService) {
        this.authService = authService;
    }

    public String getToken(long installationId) throws Exception {
        CachedToken cached = cache.get(installationId);

        if (cached != null && Instant.now().isBefore(cached.expiresAt().minusSeconds(300))) {
            return cached.token();
        }

        log.info("Fetching new token for installation {}", installationId);
        String token = authService.getInstallationAccessToken(installationId);
        cache.put(installationId, new CachedToken(token, Instant.now().plusSeconds(3300)));
        return token;
    }
}