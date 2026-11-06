package com.supplychainsentinel.mapper;

import com.supplychainsentinel.dto.SbomDocumentResponse;
import com.supplychainsentinel.entity.SbomDocument;

public final class SbomDocumentMapper {

    private SbomDocumentMapper() { }

    public static SbomDocumentResponse toDto(SbomDocument doc) {
        return new SbomDocumentResponse(
                doc.getId(),
                doc.getApplication().getId(),
                doc.getApplication().getName(),
                doc.getPipelineRun() != null ? doc.getPipelineRun().getId() : null,
                doc.getArtifactDigest(),
                doc.getFormat(),
                doc.getSpecVersion(),
                doc.getSerialNumber(),
                doc.getDocumentVersion(),
                doc.getDocumentSha256(),
                doc.getComponentCount(),
                doc.getGeneratedAt(),
                doc.getIngestedAt(),
                doc.getCreatedAt()
        );
    }
}
