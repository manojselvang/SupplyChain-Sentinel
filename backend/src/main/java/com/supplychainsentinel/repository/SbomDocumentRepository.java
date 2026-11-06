package com.supplychainsentinel.repository;

import com.supplychainsentinel.entity.SbomDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface SbomDocumentRepository extends JpaRepository<SbomDocument, UUID>,
        JpaSpecificationExecutor<SbomDocument> {

    Optional<SbomDocument> findByApplicationIdAndPipelineRunIdAndDocumentSha256(
            UUID applicationId, UUID pipelineRunId, String documentSha256);

    long countByApplicationId(UUID applicationId);

    @org.springframework.data.jpa.repository.Query("SELECT COALESCE(SUM(s.componentCount), 0) FROM SbomDocument s")
    long sumAllComponents();
}
