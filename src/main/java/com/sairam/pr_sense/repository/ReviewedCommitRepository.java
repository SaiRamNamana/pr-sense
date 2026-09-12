package com.sairam.pr_sense.repository;

import com.sairam.pr_sense.model.ReviewedCommit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewedCommitRepository extends JpaRepository<ReviewedCommit, Long> {

    boolean existsByRepoFullNameAndPrNumberAndCommitSha(
            String repoFullName, int prNumber, String commitSha);
}