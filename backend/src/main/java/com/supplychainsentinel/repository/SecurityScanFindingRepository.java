package com.supplychainsentinel.repository;

import com.supplychainsentinel.entity.SecurityScanFinding;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SecurityScanFindingRepository extends JpaRepository<SecurityScanFinding, UUID> {

    List<SecurityScanFinding> findByFindingId(UUID findingId);

    boolean existsByScanIdAndFindingId(UUID scanId, UUID findingId);

    long countByFindingId(UUID findingId);
}
