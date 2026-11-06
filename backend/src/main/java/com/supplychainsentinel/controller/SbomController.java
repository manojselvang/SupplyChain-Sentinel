package com.supplychainsentinel.controller;

import com.supplychainsentinel.dto.SbomComponentResponse;
import com.supplychainsentinel.dto.SbomDocumentResponse;
import com.supplychainsentinel.service.SbomService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * SBOM read endpoints (JWT-authenticated, any role).
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "SBOM Management", description = "Read SBOM documents and component inventory")
public class SbomController {

    private final SbomService sbomService;

    public SbomController(SbomService sbomService) {
        this.sbomService = sbomService;
    }

    @GetMapping("/sboms")
    @Operation(summary = "List SBOM documents with filters and pagination")
    public ResponseEntity<Page<SbomDocumentResponse>> listSboms(
            @RequestParam(required = false) UUID applicationId,
            @RequestParam(required = false) String artifactDigest,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(sbomService.listSboms(applicationId, artifactDigest, pageable));
    }

    @GetMapping("/sboms/{id}")
    @Operation(summary = "Get a single SBOM document by ID (metadata only, no raw JSON)")
    public ResponseEntity<SbomDocumentResponse> getSbom(@PathVariable UUID id) {
        return ResponseEntity.ok(sbomService.getSbom(id));
    }

    @GetMapping("/sboms/{id}/components")
    @Operation(summary = "List components for an SBOM document with search and pagination")
    public ResponseEntity<Page<SbomComponentResponse>> getSbomComponents(
            @PathVariable UUID id,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String purl,
            @PageableDefault(size = 50, sort = "name") Pageable pageable
    ) {
        return ResponseEntity.ok(sbomService.getSbomComponents(id, name, purl, pageable));
    }

    @GetMapping("/applications/{id}/sboms")
    @Operation(summary = "Get SBOM history for an application, newest first")
    public ResponseEntity<Page<SbomDocumentResponse>> getApplicationSboms(
            @PathVariable UUID id,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(sbomService.getApplicationSboms(id, pageable));
    }
}
