package com.supplychainsentinel.repository;

import com.supplychainsentinel.entity.DeploymentRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface DeploymentRecordRepository extends JpaRepository<DeploymentRecord, UUID>,
        JpaSpecificationExecutor<DeploymentRecord> {

    Optional<DeploymentRecord> findByArgocdApplicationAndExternalEventId(String argocdApplication, String externalEventId);

    @Query("SELECT COUNT(d) FROM DeploymentRecord d")
    long countAllDeployments();

    @Query("SELECT COUNT(d) FROM DeploymentRecord d WHERE d.syncStatus = 'SYNCED' AND d.healthStatus = 'HEALTHY'")
    long countSuccessful();

    @Query("SELECT COUNT(d) FROM DeploymentRecord d WHERE d.syncStatus = 'ERROR' OR d.healthStatus IN ('DEGRADED','MISSING')")
    long countFailed();

    @Query("SELECT COUNT(d) FROM DeploymentRecord d WHERE d.healthStatus = 'DEGRADED'")
    long countDegraded();
}
