package com.supplychainsentinel.repository;

import com.supplychainsentinel.entity.ArtifactVerification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface ArtifactVerificationRepository extends JpaRepository<ArtifactVerification, UUID>,
        JpaSpecificationExecutor<ArtifactVerification> {

    long countByVerificationStatus(String status);

    long countByVerificationStatusAndArtifactDigest(String status, String digest);

    @org.springframework.data.jpa.repository.Query(
            "SELECT COUNT(av) FROM ArtifactVerification av WHERE av.verificationStatus = 'VERIFIED'")
    long countVerified();

    @org.springframework.data.jpa.repository.Query(
            "SELECT COUNT(av) FROM ArtifactVerification av WHERE av.verificationStatus = 'FAILED'")
    long countFailed();
}
