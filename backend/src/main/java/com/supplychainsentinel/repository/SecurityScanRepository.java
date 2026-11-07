package com.supplychainsentinel.repository;

import com.supplychainsentinel.entity.SecurityScan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface SecurityScanRepository extends JpaRepository<SecurityScan, UUID>,
        JpaSpecificationExecutor<SecurityScan> { }
