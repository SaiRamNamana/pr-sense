package com.sairam.pr_sense.Services;

import tools.jackson.databind.ObjectMapper;
import com.sairam.pr_sense.DTO.ReviewComment;
import com.sairam.pr_sense.DTO.ReviewResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
public class ReviewService {

    private static final Logger log = LoggerFactory.getLogger(ReviewService.class);
    private static final int MAX_DIFF_CHARS = 60_000;

    private static final String SYSTEM_INSTRUCTION = """
            You are a senior software engineer performing a pull request review.
            You only report real, actionable problems. You never invent issues to fill a quota.
            """;

    private static final String PROMPT = """
            Review the unified diff below.

            LINE NUMBER RULES — this is critical:
            - Report line numbers from the NEW version of the file, i.e. the number in the
              `@@ -a,b +c,d @@` header, incremented by 1 for every added ('+') line and every
              context (' ') line you pass. Removed ('-') lines never advance the counter.
            - The reported line MUST be a line that appears in the diff.

            REPORTING RULES:
            - Use the file path exactly as it appears after the `b/` prefix.
            - At most one comment per (path, line) pair.
            - At most 15 comments, most severe first.
            - Each comment: one or two sentences, specific and actionable.
            - Skip generated files, lockfiles and vendored code.
            - If the diff is clean, return an empty comments array and a summary saying so.

            Focus on: bugs/logic errors, security concerns, style/readability, missing edge cases.

            Diff:
            %s
            """;

    private static final Map<String, Object> RESPONSE_SCHEMA = Map.of(
            "type", "OBJECT",
            "properties", Map.of(
                    "summary", Map.of(
                            "type", "STRING",
                            "description", "Two or three sentence overall assessment of the PR"),
                    "comments", Map.of(
                            "type", "ARRAY",
                            "items", Map.of(
                                    "type", "OBJECT",
                                    "properties", Map.of(
                                            "path", Map.of("type", "STRING",
                                                    "description", "New-file path, without the b/ prefix"),
                                            "line", Map.of("type", "INTEGER",
                                                    "description", "Line number in the NEW version of the file"),
                                            "category", Map.of("type", "STRING",
                                                    "enum", List.of("BUG", "SECURITY", "STYLE", "EDGE_CASE")),
                                            "severity", Map.of("type", "STRING",
                                                    "enum", List.of("high", "medium", "low")),
                                            "comment", Map.of("type", "STRING")
                                    ),
                                    "required", List.of("path", "line", "category", "severity", "comment"),
                                    "propertyOrdering", List.of("path", "line", "category", "severity", "comment")
                            )
                    )
            ),
            "required", List.of("summary", "comments")
    );

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${gemini.api.key}")
    private String apiKey;

    @Value("${gemini.model}")
    private String model;

    public ReviewService(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    public ReviewResult reviewDiff(String diff, Map<String, Set<Integer>> commentable) {
        String trimmed = diff.length() > MAX_DIFF_CHARS
                ? diff.substring(0, MAX_DIFF_CHARS) + "\n... [diff truncated]"
                : diff;

        Map<String, Object> requestBody = Map.of(
                "systemInstruction", Map.of("parts", List.of(Map.of("text", SYSTEM_INSTRUCTION))),
                "contents", List.of(Map.of("parts", List.of(Map.of("text", PROMPT.formatted(trimmed))))),
                "generationConfig", Map.of(
                        "temperature", 0.2,
                        "responseMimeType", "application/json",
                        "responseSchema", RESPONSE_SCHEMA
                )
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-goog-api-key", apiKey);   // keeps the key out of the URL / logs

        String url = "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent"
                .formatted(model);

        Map<?, ?> response = restTemplate.postForObject(
                url, new HttpEntity<>(requestBody, headers), Map.class);

        String json = extractText(response);
        log.info("Raw Gemini response ({} chars): {}", json.length(), json);

        try {
            ReviewResult parsed = objectMapper.readValue(json, ReviewResult.class);
            List<ReviewComment> valid = validate(parsed.comments(), commentable);
            log.info("Gemini returned {} comments, {} usable after validation",
                    parsed.comments().size(), valid.size());
            return new ReviewResult(parsed.summary(), valid);
        } catch (Exception e) {
            throw new IllegalStateException("Could not parse Gemini review JSON: " + json, e);
        }
    }

    /** Drops comments on files that aren't in the diff and snaps line numbers to the nearest real line. */
    private List<ReviewComment> validate(List<ReviewComment> raw, Map<String, Set<Integer>> commentable) {
        List<ReviewComment> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (ReviewComment c : raw) {
            if (c.path() == null || c.comment() == null || c.comment().isBlank()) continue;

            Integer line = snap(c.path(), c.line(), commentable);
            if (line == null) {
                log.debug("Dropping comment for unknown path {}", c.path());
                continue;
            }
            if (!seen.add(c.path() + ":" + line)) continue;   // GitHub 422s on duplicates

            out.add(new ReviewComment(c.path(), line, c.category(), c.severity(), c.comment()));
        }
        return out;
    }

    private Integer snap(String path, int line, Map<String, Set<Integer>> commentable) {
        Set<Integer> lines = commentable.get(path);
        if (lines == null || lines.isEmpty()) return null;
        if (lines.contains(line)) return line;

        Integer best = null;
        for (Integer l : lines) {
            if (best == null || Math.abs(l - line) < Math.abs(best - line)) best = l;
        }
        return best;
    }

    private String extractText(Map<?, ?> response) {
        if (response == null) throw new IllegalStateException("Empty response from Gemini");

        List<?> candidates = (List<?>) response.get("candidates");
        if (candidates == null || candidates.isEmpty()) {
            throw new IllegalStateException("Gemini returned no candidates (blocked?): " + response);
        }
        Map<?, ?> content = (Map<?, ?>) ((Map<?, ?>) candidates.get(0)).get("content");
        List<?> parts = (List<?>) content.get("parts");
        return (String) ((Map<?, ?>) parts.get(0)).get("text");
    }
}