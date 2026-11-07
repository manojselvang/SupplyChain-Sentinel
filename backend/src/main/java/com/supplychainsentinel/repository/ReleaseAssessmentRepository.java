package com.supplychainsentinel.repository;

import com.supplychainsentinel.entity.ReleaseAssessment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.util.UUID;

public interface ReleaseAssessmentRepository extends JpaRepository<ReleaseAssessment, UUID>,
        JpaSpecificationExecutor<ReleaseAssessment> {}
