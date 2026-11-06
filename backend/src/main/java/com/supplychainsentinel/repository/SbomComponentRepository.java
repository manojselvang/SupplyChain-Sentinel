package com.supplychainsentinel.repository;

import com.supplychainsentinel.entity.SbomComponent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface SbomComponentRepository extends JpaRepository<SbomComponent, UUID>,
        JpaSpecificationExecutor<SbomComponent> {
}
