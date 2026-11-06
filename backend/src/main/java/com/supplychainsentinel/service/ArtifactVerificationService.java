package com.supplychainsentinel.service;

import com.supplychainsentinel.dto.ArtifactVerificationIngestionRequest;
import com.supplychainsentinel.dto.ArtifactVerificationResponse;
import com.supplychainsentinel.entity.*;
import com.supplychainsentinel.exception.AuthException;
import com.supplychainsentinel.exception.ResourceNotFoundException;
import com.supplychainsentinel.mapper.ArtifactVerificationMapper;
import com.supplychainsentinel.repository.*;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * CI-reported Cosign artifact verification ingestion and read logic.
 *
 * <p>The backend stores verification results reported by CI pipelines —
 * it does not independently verify cryptographic signatures.  All
 * verification records are labelled as CI-reported.</p>
 */
@Service
public class ArtifactVerificationService {

    private final ArtifactVerificationRepository verificationRepository;
    private final ApplicationRepository applicationRepository;
    private final PipelineRunRepository pipelineRunRepository;

    public ArtifactVerificationService(
            ArtifactVerificationRepository verificationRepository,
            ApplicationRepository applicationRepository,
            PipelineRunRepository pipelineRunRepository
    ) {
        this.verificationRepository = verificationRepository;
        this.applicationRepository = applicationRepository;
        this.pipelineRunRepository = pipelineRunRepository;
    }

    // ── Ingestion ─────────────────────────────────────────────

    @Transactional
    public ArtifactVerificationResponse ingestVerification(
            ArtifactVerificationIngestionRequest request) {

        // Resolve application
        InventoryApplication app = applicationRepository
                .findByApplicationKey(request.applicationKey())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Unknown application key: " + request.applicationKey()));

        // Resolve pipeline run
        PipelineRun pipelineRun = null;
        if (request.pipelineRunId() != null) {
            pipelineRun = pipelineRunRepository.findById(request.pipelineRunId())
                    .orElseThrow(() -> new AuthException(
                            "Unknown pipeline run ID: " + request.pipelineRunId(),
                            HttpStatus.BAD_REQUEST));
            if (!pipelineRun.getApplication().getId().equals(app.getId())) {
                throw new AuthException(
                        "Pipeline run does not belong to application " + request.applicationKey(),
                        HttpStatus.BAD_REQUEST);
            }
        }

        // Validate immutable artifact reference contains a SHA-256 digest
        String ref = request.artifactReference();
        if (!ref.contains("@sha256:")) {
            throw new IllegalArgumentException(
                    "Artifact reference must be pinned by SHA-256 digest (e.g. registry/app@sha256:...)");
        }

        // Extract digest from reference and compare with provided digest
        String refDigest = extractDigestFromReference(ref);
        if (request.artifactDigest() != null && refDigest != null
                && !request.artifactDigest().equalsIgnoreCase(refDigest)) {
            throw new IllegalArgumentException(
                    "artifactDigest does not match the digest in artifactReference");
        }

        // If pipeline run has an artifact digest, verify match
        if (pipelineRun != null && pipelineRun.getArtifactDigest() != null
                && !pipelineRun.getArtifactDigest().isBlank()
                && request.artifactDigest() != null
                && !pipelineRun.getArtifactDigest().equalsIgnoreCase(request.artifactDigest())) {
            throw new IllegalArgumentException(
                    "artifactDigest does not match the pipeline run's artifact digest");
        }

        // VERIFIED requires signer metadata
        if ("VERIFIED".equals(request.verificationStatus())) {
            if (request.signerIdentity() == null || request.signerIdentity().isBlank()) {
                throw new IllegalArgumentException(
                        "VERIFIED status requires signerIdentity");
            }
            if ("KEYLESS".equals(request.verificationMode())) {
                if (request.oidcIssuer() == null || request.oidcIssuer().isBlank()) {
                    throw new IllegalArgumentException(
                            "VERIFIED with KEYLESS mode requires oidcIssuer");
                }
            }
            if ("KEY".equals(request.verificationMode())) {
                if (request.publicKeyFingerprint() == null || request.publicKeyFingerprint().isBlank()) {
                    throw new IllegalArgumentException(
                            "VERIFIED with KEY mode requires publicKeyFingerprint");
                }
            }
        }

        // Create verification record
        ArtifactVerification av = new ArtifactVerification();
        av.setApplication(app);
        av.setPipelineRun(pipelineRun);
        av.setArtifactReference(ref);
        av.setArtifactDigest(request.artifactDigest());
        av.setVerificationTool("COSIGN");
        av.setVerificationMode(request.verificationMode());
        av.setVerificationStatus(request.verificationStatus());
        av.setSignerIdentity(request.signerIdentity());
        av.setOidcIssuer(request.oidcIssuer());
        av.setPublicKeyFingerprint(request.publicKeyFingerprint());
        av.setCosignVersion(request.cosignVersion());
        av.setVerifiedAt(request.verifiedAt());
        av.setReportedAt(request.reportedAt() != null ? request.reportedAt() : Instant.now());

        if (request.verificationEvidence() != null) {
            // Store as string, excluding any private key material
            String evidence = request.verificationEvidence().toString();
            if (evidence.length() > 10000) evidence = evidence.substring(0, 10000);
            av.setVerificationEvidence(evidence);
        }

        verificationRepository.save(av);
        verificationRepository.flush();

        return ArtifactVerificationMapper.toDto(av);
    }

    // ── Read: list ────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<ArtifactVerificationResponse> listVerifications(
            UUID applicationId, UUID pipelineRunId, String status,
            String artifactDigest, Pageable pageable) {

        Specification<ArtifactVerification> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (applicationId != null)
                predicates.add(cb.equal(root.get("application").get("id"), applicationId));
            if (pipelineRunId != null)
                predicates.add(cb.equal(root.get("pipelineRun").get("id"), pipelineRunId));
            if (status != null)
                predicates.add(cb.equal(root.get("verificationStatus"), status));
            if (artifactDigest != null)
                predicates.add(cb.equal(root.get("artifactDigest"), artifactDigest));
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return verificationRepository.findAll(spec, pageable).map(ArtifactVerificationMapper::toDto);
    }

    // ── Read: application verifications ─────────────────────────

    @Transactional(readOnly = true)
    public Page<ArtifactVerificationResponse> getApplicationVerifications(
            UUID applicationId, Pageable pageable) {
        Specification<ArtifactVerification> spec = (root, query, cb) ->
                cb.equal(root.get("application").get("id"), applicationId);
        return verificationRepository.findAll(spec, pageable).map(ArtifactVerificationMapper::toDto);
    }

    // ── Helpers ───────────────────────────────────────────────

    private static String extractDigestFromReference(String ref) {
        int idx = ref.indexOf("@sha256:");
        if (idx < 0) return null;
        String after = ref.substring(idx + 8);
        // Take the hex part (stop at any non-hex character)
        StringBuilder sb = new StringBuilder();
        for (char c : after.toCharArray()) {
            if (Character.isDigit(c) || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F')) {
                sb.append(c);
            } else break;
        }
        return sb.length() == 64 ? sb.toString() : null;
    }
}
