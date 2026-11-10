package com.supplychainsentinel.repository;

import com.supplychainsentinel.entity.ReleaseAssessment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ReleaseAssessmentRepository extends JpaRepository<ReleaseAssessment, UUID>,
        JpaSpecificationExecutor<ReleaseAssessment> {

    /**
     * Auto-correlation: find the most recently assessed record for a given application
     * whose {@code artifactDigest} matches the supplied digest (case-insensitive).
     * Used when the caller does not provide an explicit {@code releaseAssessmentId}.
     */
    @Query("SELECT a FROM ReleaseAssessment a " +
           "WHERE a.application.id = :applicationId " +
           "AND LOWER(a.artifactDigest) = LOWER(:digest) " +
           "ORDER BY a.assessedAt DESC")
    Optional<ReleaseAssessment> findTopByApplicationIdAndArtifactDigestIgnoreCase(
            @Param("applicationId") UUID applicationId,
            @Param("digest") String digest);
}
