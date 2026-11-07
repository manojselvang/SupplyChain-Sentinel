package com.supplychainsentinel.repository;

import com.supplychainsentinel.entity.ReleaseApproval;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface ReleaseApprovalRepository extends JpaRepository<ReleaseApproval, UUID> {
    List<ReleaseApproval> findByAssessmentId(UUID assessmentId);
    boolean existsByAssessmentIdAndApproverId(UUID assessmentId, UUID approverId);
}
