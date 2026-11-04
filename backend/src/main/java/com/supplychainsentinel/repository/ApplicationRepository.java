package com.supplychainsentinel.repository;

import com.supplychainsentinel.entity.InventoryApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

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
}
