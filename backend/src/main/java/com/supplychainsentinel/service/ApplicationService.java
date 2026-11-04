package com.supplychainsentinel.service;

import com.supplychainsentinel.dto.ApplicationResponse;
import com.supplychainsentinel.dto.CreateApplicationRequest;
import com.supplychainsentinel.dto.UpdateApplicationRequest;
import com.supplychainsentinel.entity.InventoryApplication;
import com.supplychainsentinel.exception.AuthException;
import com.supplychainsentinel.exception.ResourceNotFoundException;
import com.supplychainsentinel.mapper.ApplicationMapper;
import com.supplychainsentinel.repository.ApplicationRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.UUID;

/**
 * Application inventory business logic.
 *
 * <ul>
 *   <li>ADMIN — full CRUD</li>
 *   <li>SECURITY_ENGINEER — create, read, update (no delete)</li>
 *   <li>DEVELOPER / RELEASE_MANAGER / AUDITOR — read-only</li>
 *   <li>DELETE — ADMIN only</li>
 * </ul>
 *
 * RBAC is enforced at the controller level via @PreAuthorize.
 */
@Service
public class ApplicationService {

    private final ApplicationRepository applicationRepository;

    public ApplicationService(ApplicationRepository applicationRepository) {
        this.applicationRepository = applicationRepository;
    }

    @Transactional
    public ApplicationResponse createApplication(CreateApplicationRequest request) {
        if (applicationRepository.existsByApplicationKey(request.applicationKey())) {
            throw new AuthException(
                    "Application key '" + request.applicationKey() + "' already exists",
                    HttpStatus.CONFLICT);
        }

        InventoryApplication app = new InventoryApplication();
        app.setApplicationKey(request.applicationKey());
        app.setName(request.name());
        app.setDescription(request.description());
        app.setBusinessUnit(request.businessUnit());
        app.setBusinessOwner(request.businessOwner());
        app.setTechnicalOwner(request.technicalOwner());
        app.setCriticality(request.criticality());
        app.setLifecycleStatus(request.lifecycleStatus());
        app.setDataClassification(request.dataClassification());
        app.setRepositoryUrl(request.repositoryUrl());
        app.setPrimaryLanguage(request.primaryLanguage());
        app.setHostingEnvironment(request.hostingEnvironment());
        app.setInternetFacing(request.internetFacing() != null && request.internetFacing());

        applicationRepository.save(app);
        // Flush so @CreationTimestamp / @UpdateTimestamp are populated before
        // the DTO is serialised (Jackson is configured with non_null inclusion)
        applicationRepository.flush();

        return ApplicationMapper.toDto(app);
    }

    @Transactional(readOnly = true)
    public ApplicationResponse getApplicationById(UUID id) {
        InventoryApplication app = applicationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found: " + id));
        return ApplicationMapper.toDto(app);
    }

    @Transactional(readOnly = true)
    public Page<ApplicationResponse> listApplications(
            String search, String criticality, String lifecycleStatus, Pageable pageable) {

        Specification<InventoryApplication> spec = (root, query, cb) -> {
            ArrayList<Predicate> predicates = new ArrayList<>();

            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), pattern),
                        cb.like(cb.lower(root.get("applicationKey")), pattern)
                ));
            }
            if (criticality != null) {
                predicates.add(cb.equal(root.get("criticality"), criticality));
            }
            if (lifecycleStatus != null) {
                predicates.add(cb.equal(root.get("lifecycleStatus"), lifecycleStatus));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return applicationRepository.findAll(spec, pageable)
                .map(ApplicationMapper::toDto);
    }

    @Transactional
    public ApplicationResponse updateApplication(UUID id, UpdateApplicationRequest request) {
        InventoryApplication app = applicationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found: " + id));

        if (request.name() != null) app.setName(request.name());
        if (request.description() != null) app.setDescription(request.description());
        if (request.businessUnit() != null) app.setBusinessUnit(request.businessUnit());
        if (request.businessOwner() != null) app.setBusinessOwner(request.businessOwner());
        if (request.technicalOwner() != null) app.setTechnicalOwner(request.technicalOwner());
        if (request.criticality() != null) app.setCriticality(request.criticality());
        if (request.lifecycleStatus() != null) app.setLifecycleStatus(request.lifecycleStatus());
        if (request.dataClassification() != null) app.setDataClassification(request.dataClassification());
        if (request.repositoryUrl() != null) app.setRepositoryUrl(request.repositoryUrl());
        if (request.primaryLanguage() != null) app.setPrimaryLanguage(request.primaryLanguage());
        if (request.hostingEnvironment() != null) app.setHostingEnvironment(request.hostingEnvironment());
        if (request.internetFacing() != null) app.setInternetFacing(request.internetFacing());

        applicationRepository.save(app);
        applicationRepository.flush();
        return ApplicationMapper.toDto(app);
    }

    @Transactional
    public void deleteApplication(UUID id) {
        if (!applicationRepository.existsById(id)) {
            throw new ResourceNotFoundException("Application not found: " + id);
        }
        applicationRepository.deleteById(id);
    }
}
