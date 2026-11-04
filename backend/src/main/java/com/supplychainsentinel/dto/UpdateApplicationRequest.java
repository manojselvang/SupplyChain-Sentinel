package com.supplychainsentinel.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request body for updating an application. All fields optional —
 * only provided fields are updated.
 */
public record UpdateApplicationRequest(

        @Size(max = 255)
        String name,

        @Size(max = 10000)
        String description,

        @Size(max = 255)
        String businessUnit,

        @Size(max = 255)
        String businessOwner,

        @Size(max = 255)
        String technicalOwner,

        @Pattern(regexp = "CRITICAL|HIGH|MEDIUM|LOW",
                message = "Criticality must be one of: CRITICAL, HIGH, MEDIUM, LOW")
        String criticality,

        @Pattern(regexp = "PLANNING|ACTIVE|DEPRECATED|RETIRED",
                message = "Lifecycle status must be one of: PLANNING, ACTIVE, DEPRECATED, RETIRED")
        String lifecycleStatus,

        @Pattern(regexp = "PUBLIC|INTERNAL|CONFIDENTIAL|RESTRICTED",
                message = "Data classification must be one of: PUBLIC, INTERNAL, CONFIDENTIAL, RESTRICTED")
        String dataClassification,

        @Size(max = 500)
        String repositoryUrl,

        @Size(max = 100)
        String primaryLanguage,

        @Pattern(regexp = "ON_PREMISES|AWS|AZURE|GCP|HYBRID",
                message = "Hosting environment must be one of: ON_PREMISES, AWS, AZURE, GCP, HYBRID")
        String hostingEnvironment,

        Boolean internetFacing
) {}
