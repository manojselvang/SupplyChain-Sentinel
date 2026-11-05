package com.supplychainsentinel.repository;

import com.supplychainsentinel.dto.DistributionEntry;
import com.supplychainsentinel.entity.SecurityFinding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SecurityFindingRepository extends JpaRepository<SecurityFinding, UUID>,
        JpaSpecificationExecutor<SecurityFinding> {

    Optional<SecurityFinding> findByApplicationIdAndFingerprint(UUID applicationId, String fingerprint);

    // ── Dashboard aggregation ────────────────────────────────

    @Query("SELECT COUNT(f) FROM SecurityFinding f WHERE f.status = 'OPEN'")
    long countOpenFindings();

    @Query("SELECT COUNT(f) FROM SecurityFinding f WHERE f.status = 'OPEN' AND f.severity = 'CRITICAL'")
    long countOpenCritical();

    @Query("SELECT COUNT(f) FROM SecurityFinding f WHERE f.status = 'OPEN' AND f.severity = 'HIGH'")
    long countOpenHigh();

    @Query("SELECT new com.supplychainsentinel.dto.DistributionEntry(f.severity, COUNT(f)) " +
           "FROM SecurityFinding f WHERE f.status = 'OPEN' GROUP BY f.severity")
    List<DistributionEntry> openFindingsBySeverity();
}
