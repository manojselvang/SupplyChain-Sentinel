package com.supplychainsentinel.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request body for creating a new application.
 */
public record CreateApplicationRequest(

        @NotBlank(message = "Application key is required")
        @Size(max = 100, message = "Application key must be at most 100 characters")
        String applicationKey,

        @NotBlank(message = "Name is required")
        @Size(max = 255, message = "Name must be at most 255 characters")
        String name,

        @Size(max = 10000, message = "Description must be at most 10000 characters")
        String description,

        @Size(max = 255)
        String businessUnit,

        @Size(max = 255)
        String businessOwner,

        @Size(max = 255)
        String technicalOwner,

        @NotBlank(message = "Criticality is required")
        @Pattern(regexp = "CRITICAL|HIGH|MEDIUM|LOW",
                message = "Criticality must be one of: CRITICAL, HIGH, MEDIUM, LOW")
        String criticality,

        @NotBlank(message = "Lifecycle status is required")
        @Pattern(regexp = "PLANNING|ACTIVE|DEPRECATED|RETIRED",
                message = "Lifecycle status must be one of: PLANNING, ACTIVE, DEPRECATED, RETIRED")
        String lifecycleStatus,

        @NotBlank(message = "Data classification is required")
        @Pattern(regexp = "PUBLIC|INTERNAL|CONFIDENTIAL|RESTRICTED",
                message = "Data classification must be one of: PUBLIC, INTERNAL, CONFIDENTIAL, RESTRICTED")
        String dataClassification,

        @Size(max = 500)
        String repositoryUrl,

        @Size(max = 100)
        String primaryLanguage,

        @NotBlank(message = "Hosting environment is required")
        @Pattern(regexp = "ON_PREMISES|AWS|AZURE|GCP|HYBRID",
                message = "Hosting environment must be one of: ON_PREMISES, AWS, AZURE, GCP, HYBRID")
        String hostingEnvironment,

        Boolean internetFacing
) {}
