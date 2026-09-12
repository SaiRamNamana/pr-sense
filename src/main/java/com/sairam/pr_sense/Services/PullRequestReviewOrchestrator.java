package com.sairam.pr_sense.Services;

import com.sairam.pr_sense.DTO.PullRequestEvent;
import com.sairam.pr_sense.DTO.ReviewComment;
import com.sairam.pr_sense.DTO.ReviewResult;
import com.sairam.pr_sense.model.ReviewedCommit;
import com.sairam.pr_sense.repository.ReviewedCommitRepository;
import com.sairam.pr_sense.util.DiffParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class PullRequestReviewOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(PullRequestReviewOrchestrator.class);

    private final GithubAuthService gitHubAuthService;
    private final ReviewService reviewService;
    private final GithubCommentService commentService;
    private final ReviewedCommitRepository reviewedCommitRepository;
    private final InstallationTokenCache tokenCache;

    public PullRequestReviewOrchestrator(GithubAuthService gitHubAuthService,
                                         ReviewService reviewService,
                                         GithubCommentService commentService,
                                         ReviewedCommitRepository reviewedCommitRepository, InstallationTokenCache tokenCache) {
        this.gitHubAuthService = gitHubAuthService;
        this.reviewService = reviewService;
        this.commentService = commentService;
        this.reviewedCommitRepository = reviewedCommitRepository;
        this.tokenCache = tokenCache;
    }

    @Async("reviewExecutor")
    public void handle(PullRequestEvent event) {
        String repo = event.getRepository().getFullName();
        int prNumber = event.getPullRequest().getNumber();
        String sha = event.getPullRequest().getHead().getSha();

        // Insert-first: the unique constraint is the guard. If two webhooks
        // race, the loser hits DataIntegrityViolationException and bails.
        try {
            reviewedCommitRepository.save(new ReviewedCommit(repo, prNumber, sha));
        } catch (DataIntegrityViolationException e) {
            log.info("Already reviewed {}#{} at {}, skipping", repo, prNumber, sha);
            return;
        }

        log.info("Starting review of {}#{} at {}", repo, prNumber, sha);

        try {
            String token = tokenCache.getToken(event.getInstallation().getId());

            String diff = gitHubAuthService.fetchPullRequestDiff(repo, prNumber, token);
            if (diff == null || diff.isBlank()) {
                log.info("Empty diff for {}#{}, skipping", repo, prNumber);
                return;
            }

            Map<String, Set<Integer>> commentable = DiffParser.commentableLines(diff);
            ReviewResult result = reviewService.reviewDiff(diff, commentable);

            List<Map<String, Object>> inline = result.comments().stream()
                    .map(c -> Map.<String, Object>of(
                            "path", c.path(),
                            "line", c.line(),
                            "side", "RIGHT",
                            "body", "**[%s · %s]** %s"
                                    .formatted(c.category(), c.severity(), c.comment())))
                    .toList();

            commentService.postReview(repo, prNumber, token, buildSummary(result), inline);

        } catch (Exception e) {
            log.error("Review failed for {}#{} at {}", repo, prNumber, sha, e);
            // Row stays in the table — we reviewed this SHA and it failed. Better to
            // not retry automatically than to loop on a broken PR. If you want retries,
            // delete the row here and add a backoff policy elsewhere.
        }
    }

    private String buildSummary(ReviewResult result) {
        StringBuilder sb = new StringBuilder("## 🤖 PR Sense review\n\n");
        sb.append(result.summary() == null || result.summary().isBlank()
                ? "No significant issues found." : result.summary());

        if (!result.comments().isEmpty()) {
            sb.append("\n\n### Findings\n");
            for (ReviewComment c : result.comments()) {
                sb.append("- `").append(c.path()).append(':').append(c.line()).append("` ")
                        .append("**[").append(c.category()).append(" · ").append(c.severity()).append("]** ")
                        .append(c.comment()).append('\n');
            }
        }
        sb.append("\n<sub>Automated review — verify before acting.</sub>");
        return sb.toString();
    }
}