package com.supplychainsentinel.mapper;

import com.supplychainsentinel.dto.ApplicationResponse;
import com.supplychainsentinel.entity.InventoryApplication;

/**
 * Converts between {@link InventoryApplication} entities and {@link ApplicationResponse} DTOs.
 */
public final class ApplicationMapper {

    private ApplicationMapper() { }

    public static ApplicationResponse toDto(InventoryApplication app) {
        return new ApplicationResponse(
                app.getId(),
                app.getApplicationKey(),
                app.getName(),
                app.getDescription(),
                app.getBusinessUnit(),
                app.getBusinessOwner(),
                app.getTechnicalOwner(),
                app.getCriticality(),
                app.getLifecycleStatus(),
                app.getDataClassification(),
                app.getRepositoryUrl(),
                app.getPrimaryLanguage(),
                app.getHostingEnvironment(),
                app.isInternetFacing(),
                app.getCreatedAt(),
                app.getUpdatedAt()
        );
    }
}
