package com.sairam.pr_sense.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(
        name = "reviewed_commits",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_reviewed_commit",
                columnNames = {"repo_full_name", "pr_number", "commit_sha"}
        )
)
public class ReviewedCommit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "repo_full_name", nullable = false)
    private String repoFullName;

    @Column(name = "pr_number", nullable = false)
    private int prNumber;

    @Column(name = "commit_sha", nullable = false, length = 40)
    private String commitSha;

    @Column(name = "reviewed_at", nullable = false)
    private Instant reviewedAt = Instant.now();

    protected ReviewedCommit() {}   // JPA needs a no-arg constructor

    public ReviewedCommit(String repoFullName, int prNumber, String commitSha) {
        this.repoFullName = repoFullName;
        this.prNumber = prNumber;
        this.commitSha = commitSha;
    }

    public Long getId() { return id; }
    public String getRepoFullName() { return repoFullName; }
    public int getPrNumber() { return prNumber; }
    public String getCommitSha() { return commitSha; }
    public Instant getReviewedAt() { return reviewedAt; }
}