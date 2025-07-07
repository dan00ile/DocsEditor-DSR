package dsr.practice.docseditor.controller;

import dsr.practice.docseditor.dto.ApiResponse;
import dsr.practice.docseditor.dto.CreateDocumentRequest;
import dsr.practice.docseditor.dto.DocumentUpdateRequest;
import dsr.practice.docseditor.dto.SaveVersionRequest;
import dsr.practice.docseditor.exception.AccessDeniedException;
import dsr.practice.docseditor.exception.DocumentNotFoundException;
import dsr.practice.docseditor.exception.DuplicateVersionNameException;
import dsr.practice.docseditor.exception.OptimisticLockingException;
import dsr.practice.docseditor.model.Document;
import dsr.practice.docseditor.model.DocumentVersion;
import dsr.practice.docseditor.repository.DocumentRepository;
import dsr.practice.docseditor.service.CollaborationService;
import dsr.practice.docseditor.service.DocumentService;
import dsr.practice.docseditor.utils.SecurityUtils;
import jakarta.validation.Valid;
import jakarta.validation.ValidationException;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/documents")
@Validated
@Slf4j
@RequiredArgsConstructor
public class DocumentController {
    private final DocumentService documentService;
    private final SecurityUtils securityUtils;
    private final CollaborationService collaborationService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<Document>>> getAllDocuments() {
        try {
            List<Document> documents = documentService.getCurrentUserDocuments();
            return ResponseEntity.ok(ApiResponse.success(documents));
        } catch (Exception e) {
            log.error("Error getting user documents", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to retrieve documents", "SERVER_ERROR"));
        }
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Document>> createDocument(
            @RequestBody @Valid CreateDocumentRequest request) {
        try {
            UUID currentUserId = securityUtils.getCurrentUserIdOrThrow();

            Document document = documentService.createDocument(request, currentUserId);
            return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(document));
        } catch (ValidationException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage(), "VALIDATION_ERROR"));
        } catch (Exception e) {
            log.error("Error creating document", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to create document", "SERVER_ERROR"));
        }
    }

    @GetMapping("/{documentId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Document>> getDocument(
            @PathVariable UUID documentId) {
        try {
            UUID userId = securityUtils.getCurrentUserIdOrThrow();

            collaborationService.registerUserActivity(documentId, userId);

            Document document = documentService.getDocumentWithActiveUsers(documentId, userId);
            return ResponseEntity.ok(ApiResponse.success(document));
        } catch (DocumentNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Document not found", "NOT_FOUND"));
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Access denied", "ACCESS_DENIED"));
        } catch (Exception e) {
            log.error("Error getting document {}", documentId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to retrieve document", "SERVER_ERROR"));
        }
    }

    @PutMapping("/{documentId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Document>> updateDocumentTitle(
            @PathVariable UUID documentId,
            @RequestParam String title) {
        try {
            UUID userId = securityUtils.getCurrentUserIdOrThrow();
            
            Document updatedDocument = documentService.updateDocumentTitle(documentId, title, userId);
            return ResponseEntity.ok(ApiResponse.success(updatedDocument));
        } catch (DocumentNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Document not found", "NOT_FOUND"));
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Access denied", "ACCESS_DENIED"));
        } catch (Exception e) {
            log.error("Error updating document title {}", documentId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to update document title", "SERVER_ERROR"));
        }
    }

    @PutMapping("/{documentId}/content")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Document>> updateDocumentContent(
            @PathVariable UUID documentId,
            @RequestBody @Valid DocumentUpdateRequest request) {
        try {
            Document updatedDocument = documentService.updateDocumentContent(
                    documentId, request);

            collaborationService.notifyDocumentUpdate(documentId, updatedDocument, request.getClientId());

            return ResponseEntity.ok(ApiResponse.success(updatedDocument));

        } catch (OptimisticLockingException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.error("Document was modified by another user", "CONFLICT"));
        } catch (DocumentNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Document not found", "NOT_FOUND"));
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Access denied", "ACCESS_DENIED"));
        } catch (Exception e) {
            log.error("Error updating document content {}", documentId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to update document content", "SERVER_ERROR"));
        }
    }

    @DeleteMapping("/{documentId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> deleteDocument(
            @PathVariable UUID documentId) {
        try {
            UUID userId = securityUtils.getCurrentUserIdOrThrow();
            
            documentService.deleteDocument(documentId, userId);
            return ResponseEntity.ok(ApiResponse.success(null));
        } catch (DocumentNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Document not found", "NOT_FOUND"));
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Access denied", "ACCESS_DENIED"));
        } catch (Exception e) {
            log.error("Error deleting document {}", documentId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to delete document", "SERVER_ERROR"));
        }
    }

    @PostMapping("/{documentId}/versions")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<DocumentVersion>> saveVersion(
            @PathVariable UUID documentId,
            @RequestBody @Valid SaveVersionRequest request) {
        try {
            UUID userId = securityUtils.getCurrentUserIdOrThrow();

            Document document = documentService.findDocument(documentId);

            DocumentVersion version = documentService.saveDocumentVersion(
                    documentId, document, request, userId);

            return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(version));
        } catch (DocumentNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Document not found", "NOT_FOUND"));
        } catch (DuplicateVersionNameException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.error("Version name already exists", "DUPLICATE_VERSION"));
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Access denied", "ACCESS_DENIED"));
        } catch (Exception e) {
            log.error("Error saving document version {}", documentId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to save document version", "SERVER_ERROR"));
        }
    }

    @GetMapping("/{documentId}/versions")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<DocumentVersion>>> getDocumentVersions(
            @PathVariable UUID documentId
    ) {
        try {
            List<DocumentVersion> versions = documentService.findDocumentVersions(documentId);
            return ResponseEntity.ok(ApiResponse.success(versions));
        } catch (DocumentNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Document not found", "NOT_FOUND"));
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Access denied", "ACCESS_DENIED"));
        } catch (Exception e) {
            log.error("Error getting document versions {}", documentId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to retrieve document versions", "SERVER_ERROR"));
        }
    }

    @PostMapping("/{documentId}/versions/{versionId}/restore")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Document>> restoreVersion(
            @PathVariable UUID documentId,
            @PathVariable UUID versionId
    ) {
        try {
            Document document = documentService.restoreDocumentVersion(documentId, versionId);

            collaborationService.notifyDocumentRestore(documentId, document);
            
            return ResponseEntity.ok(ApiResponse.success(document));
        } catch (DocumentNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Document or version not found", "NOT_FOUND"));
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Access denied", "ACCESS_DENIED"));
        } catch (Exception e) {
            log.error("Error restoring document version {} {}", documentId, versionId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to restore document version", "SERVER_ERROR"));
        }
    }
}
