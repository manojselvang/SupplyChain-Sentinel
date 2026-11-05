package com.supplychainsentinel.mapper;

import com.supplychainsentinel.dto.SecurityFindingResponse;
import com.supplychainsentinel.entity.SecurityFinding;

public final class SecurityFindingMapper {

    private SecurityFindingMapper() { }

    public static SecurityFindingResponse toDto(SecurityFinding f) {
        return new SecurityFindingResponse(
                f.getId(),
                f.getApplication().getId(),
                f.getApplication().getName(),
                f.getPipelineRun() != null ? f.getPipelineRun().getId() : null,
                f.getScanner(),
                f.getVulnerabilityId(),
                f.getRuleId(),
                f.getTitle(),
                f.getDescription(),
                f.getSeverity(),
                f.getAffectedPackage(),
                f.getInstalledVersion(),
                f.getFixedVersion(),
                f.getAffectedLocation(),
                f.getRemediationUrl(),
                f.getStatus(),
                f.getFirstSeen(),
                f.getLastSeen(),
                f.getUpdatedAt()
        );
    }
}
