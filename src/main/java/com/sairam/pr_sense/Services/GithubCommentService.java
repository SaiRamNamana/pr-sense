package com.sairam.pr_sense.Services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
public class GithubCommentService {

    private static final Logger log = LoggerFactory.getLogger(GithubCommentService.class);

    private final RestTemplate restTemplate;

    public GithubCommentService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Posts one review containing N inline comments.
     * If GitHub rejects it (422 = a line isn't part of the diff), falls back to a plain PR comment.
     */
    public void postReview(String repoFullName, int prNumber, String token,
                           String summary, List<Map<String, Object>> comments) {

        String url = "https://api.github.com/repos/%s/pulls/%d/reviews"
                .formatted(repoFullName, prNumber);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("body", summary);
        body.put("event", "COMMENT");            // never APPROVE / REQUEST_CHANGES automatically
        if (!comments.isEmpty()) body.put("comments", comments);

        try {
            restTemplate.exchange(url, HttpMethod.POST,
                    new HttpEntity<>(body, jsonHeaders(token)), Map.class);
            log.info("Posted review with {} inline comments on {}#{}",
                    comments.size(), repoFullName, prNumber);
        } catch (HttpClientErrorException e) {
            log.warn("Inline review rejected ({}): {}. Falling back to a single comment.",
                    e.getStatusCode(), e.getResponseBodyAsString());
            postIssueComment(repoFullName, prNumber, token,
                    summary + "\n\n" + renderAsText(comments));
        }
    }

    public void postIssueComment(String repoFullName, int prNumber, String token, String text) {
        String url = "https://api.github.com/repos/%s/issues/%d/comments"
                .formatted(repoFullName, prNumber);
        restTemplate.exchange(url, HttpMethod.POST,
                new HttpEntity<>(Map.of("body", text), jsonHeaders(token)), Map.class);
        log.info("Posted fallback comment on {}#{}", repoFullName, prNumber);
    }

    private HttpHeaders jsonHeaders(String token) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        h.set("Accept", "application/vnd.github+json");
        h.set("X-GitHub-Api-Version", "2022-11-28");
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    private String renderAsText(List<Map<String, Object>> comments) {
        StringBuilder sb = new StringBuilder("### Findings\n");
        for (Map<String, Object> c : comments) {
            sb.append("- `").append(c.get("path")).append(':').append(c.get("line")).append("` ")
                    .append(c.get("body")).append('\n');
        }
        return sb.toString();
    }
}