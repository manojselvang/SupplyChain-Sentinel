package com.supplychainsentinel.mapper;

import com.supplychainsentinel.dto.ArtifactVerificationResponse;
import com.supplychainsentinel.entity.ArtifactVerification;

public final class ArtifactVerificationMapper {

    private ArtifactVerificationMapper() { }

    public static ArtifactVerificationResponse toDto(ArtifactVerification av) {
        return new ArtifactVerificationResponse(
                av.getId(),
                av.getApplication().getId(),
                av.getApplication().getName(),
                av.getPipelineRun() != null ? av.getPipelineRun().getId() : null,
                av.getArtifactReference(),
                av.getArtifactDigest(),
                av.getVerificationTool(),
                av.getVerificationMode(),
                av.getVerificationStatus(),
                av.getSignerIdentity(),
                av.getOidcIssuer(),
                av.getPublicKeyFingerprint(),
                av.getCosignVersion(),
                av.getVerifiedAt(),
                av.getReportedAt(),
                av.getCreatedAt()
        );
    }
}
