package com.supplychainsentinel.repository;

import com.supplychainsentinel.dto.DistributionEntry;
import com.supplychainsentinel.entity.InventoryApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link InventoryApplication}.
 *
 * Extends {@link JpaSpecificationExecutor} so the service layer can build
 * dynamic search/filter queries with the Criteria API instead of JPQL
 * string interpolation (which causes PostgreSQL type-inference issues with
 * null parameters).
 */
public interface ApplicationRepository extends JpaRepository<InventoryApplication, UUID>,
        JpaSpecificationExecutor<InventoryApplication> {

    boolean existsByApplicationKey(String applicationKey);

    java.util.Optional<InventoryApplication> findByApplicationKey(String applicationKey);

    // ── Dashboard aggregation queries ─────────────────────────

    @Query("SELECT COUNT(a) FROM InventoryApplication a WHERE a.lifecycleStatus = 'ACTIVE'")
    long countActive();

    @Query("SELECT COUNT(a) FROM InventoryApplication a WHERE a.criticality = 'CRITICAL'")
    long countCritical();

    @Query("SELECT COUNT(a) FROM InventoryApplication a WHERE a.criticality = 'HIGH'")
    long countHighCriticality();

    @Query("SELECT COUNT(a) FROM InventoryApplication a WHERE a.internetFacing = true")
    long countInternetFacing();

    @Query("SELECT COUNT(a) FROM InventoryApplication a WHERE a.criticality IN ('HIGH','CRITICAL') AND a.internetFacing = true")
    long countHighCriticalInternetFacing();

    @Query("SELECT new com.supplychainsentinel.dto.DistributionEntry(a.criticality, COUNT(a)) " +
           "FROM InventoryApplication a GROUP BY a.criticality")
    List<DistributionEntry> distributionByCriticality();

    @Query("SELECT new com.supplychainsentinel.dto.DistributionEntry(a.lifecycleStatus, COUNT(a)) " +
           "FROM InventoryApplication a GROUP BY a.lifecycleStatus")
    List<DistributionEntry> distributionByLifecycleStatus();

    @Query("SELECT new com.supplychainsentinel.dto.DistributionEntry(a.hostingEnvironment, COUNT(a)) " +
           "FROM InventoryApplication a GROUP BY a.hostingEnvironment")
    List<DistributionEntry> distributionByHostingEnvironment();

    @Query("SELECT new com.supplychainsentinel.dto.DistributionEntry(a.businessUnit, COUNT(a)) " +
           "FROM InventoryApplication a WHERE a.businessUnit IS NOT NULL GROUP BY a.businessUnit")
    List<DistributionEntry> distributionByBusinessUnit();
}
