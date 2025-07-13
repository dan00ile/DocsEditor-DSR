package dsr.practice.docseditor.service;

import dsr.practice.docseditor.dto.CreateDocumentRequest;
import dsr.practice.docseditor.dto.DocumentUpdateRequest;
import dsr.practice.docseditor.dto.SaveVersionRequest;
import dsr.practice.docseditor.exception.AccessDeniedException;
import dsr.practice.docseditor.exception.DocumentNotFoundException;
import dsr.practice.docseditor.exception.DuplicateVersionNameException;
import dsr.practice.docseditor.model.Document;
import dsr.practice.docseditor.model.DocumentVersion;
import dsr.practice.docseditor.model.User;
import dsr.practice.docseditor.repository.DocumentRepository;
import dsr.practice.docseditor.repository.DocumentVersionRepository;
import dsr.practice.docseditor.repository.UserRepository;
import dsr.practice.docseditor.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import dsr.practice.docseditor.dto.EditOperation;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentService {
    private final DocumentRepository documentRepository;
    private final DocumentVersionRepository documentVersionRepository;
    private final UserRepository userRepository;
    private final SecurityUtils securityUtils;
    private final CollaborationService collaborationService;

    @Transactional
    public Document createDocument(CreateDocumentRequest request, UUID currentUserId) {
        Document newDocument = Document.builder()
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .title(request.getTitle() == null ? "New Document" : request.getTitle())
                .createdBy(currentUserId)
                .content(request.getContent() == null ? "Hello!" : request.getContent())
                .versionCounter(0)
                .build();
        return documentRepository.save(newDocument);
    }

    @Transactional(readOnly = true)
    public Document findDocument(UUID id) {
        Document document = documentRepository.findDocumentById(id);
        if (document == null) {
            throw new DocumentNotFoundException("Document not found with ID: " + id);
        }
        return document;
    }
    
    @Transactional(readOnly = true)
    public Document getDocumentWithActiveUsers(UUID id, UUID userId) {
        Document document = findDocument(id);

        validateUserAccess(document, userId);

        document.setActiveUsers(collaborationService.getActiveUsersList(id));
        
        return document;
    }

    @Transactional
    public DocumentVersion saveDocumentVersion(UUID documentId, Document document,
                                               SaveVersionRequest request, UUID currentUserId) {
        validateUserAccess(document, currentUserId);

        boolean versionExists = documentVersionRepository.existsByDocumentIdAndVersionName(
                documentId, request.getVersionName());
                
        if (versionExists) {
            throw new DuplicateVersionNameException("Version with name '" + request.getVersionName() + "' already exists");
        }
        
        DocumentVersion version = DocumentVersion.builder()
                .documentId(documentId)
                .versionName(request.getVersionName())
                .content(document.getContent())
                .createdBy(currentUserId)
                .createdAt(LocalDateTime.now())
                .build();

        version = documentVersionRepository.save(version);

        incrementDocumentVersion(documentId);

        return version;
    }

    @Transactional(readOnly = true)
    public List<DocumentVersion> findDocumentVersions(UUID documentId) {
        Document document = findDocument(documentId);

        UUID userId = securityUtils.getCurrentUserIdOrThrow();
        validateUserAccess(document, userId);

        List<DocumentVersion> versions = documentVersionRepository.getDocumentVersionsByDocumentId(documentId);

        enrichVersionsWithAuthorInfo(versions);
        
        return versions;
    }
    
    private void enrichVersionsWithAuthorInfo(List<DocumentVersion> versions) {
        List<UUID> authorIds = versions.stream()
                .map(DocumentVersion::getCreatedBy)
                .distinct()
                .collect(Collectors.toList());

        List<User> authors = userRepository.findAllById(authorIds);

        java.util.Map<UUID, String> userIdToName = authors.stream()
                .collect(Collectors.toMap(User::getId, User::getUsername));

        versions.forEach(version -> {
            String authorName = userIdToName.getOrDefault(version.getCreatedBy(), "Unknown");
            version.setAuthorName(authorName);
        });
    }

    @Transactional
    public void incrementDocumentVersion(UUID documentId) {
        Document document = findDocument(documentId);
        
        if (document.getVersionCounter() == null) {
            document.setVersionCounter(1);
        } else {
            document.setVersionCounter(document.getVersionCounter() + 1);
        }
        
        document.setUpdatedAt(LocalDateTime.now());
        documentRepository.save(document);
    }

    @Transactional
    public Document restoreDocumentVersion(UUID documentId, UUID versionId) {
        Document document = findDocument(documentId);
        DocumentVersion documentVersion = documentVersionRepository.getDocumentVersionById(versionId);
        
        if (documentVersion == null) {
            throw new DocumentNotFoundException("Version not found with ID: " + versionId);
        }

        if (!documentVersion.getDocumentId().equals(documentId)) {
            throw new AccessDeniedException("Version does not belong to the specified document");
        }

        UUID userId = securityUtils.getCurrentUserIdOrThrow();
        validateUserAccess(document, userId);

        document.setContent(documentVersion.getContent());
        document.setUpdatedAt(LocalDateTime.now());
        documentRepository.save(document);
        
        return document;
    }

    @Transactional
    public Document updateDocumentContent(UUID documentId, DocumentUpdateRequest request) {
        Document document = findDocument(documentId);

        UUID userId = securityUtils.getCurrentUserIdOrThrow();
        validateUserAccess(document, userId);

        if (request.getOperations() != null && !request.getOperations().isEmpty()) {
            String newContent = document.getContent();
            
            for (EditOperation operation : request.getOperations()) {
                if ("insert".equals(operation.getType())) {
                    if (operation.getPosition() >= 0 && operation.getPosition() <= newContent.length()) {
                        newContent = newContent.substring(0, operation.getPosition()) + 
                                    operation.getCharacter() + 
                                    newContent.substring(operation.getPosition());
                    }
                } else if ("delete".equals(operation.getType())) {
                    if (operation.getPosition() >= 0 && operation.getPosition() < newContent.length()) {
                        newContent = newContent.substring(0, operation.getPosition()) + 
                                    newContent.substring(operation.getPosition() + 1);
                    }
                } else if ("replace".equals(operation.getType())) {
                    newContent = operation.getCharacter();
                }
            }
            
            document.setContent(newContent);
        }
        
        document.setUpdatedAt(LocalDateTime.now());
        return documentRepository.save(document);
    }
    
    @Transactional
    public Document updateDocumentTitle(UUID documentId, String title, UUID userId) {
        Document document = findDocument(documentId);

        validateUserAccess(document, userId);
        
        document.setTitle(title);
        document.setUpdatedAt(LocalDateTime.now());
        return documentRepository.save(document);
    }

    @Transactional
    public void deleteDocument(UUID documentId, UUID userId) {
        Document document = findDocument(documentId);

        validateUserAccess(document, userId);

        if (!document.getCreatedBy().equals(userId)) {
            throw new AccessDeniedException("Only the document owner can delete the document");
        }

        documentVersionRepository.deleteAllByDocumentId(documentId);

        documentRepository.deleteById(documentId);

        collaborationService.handleDocumentDeleted(documentId);
    }

    @Transactional(readOnly = true)
    public List<Document> getCurrentUserDocuments() {
        UUID currentUserId = securityUtils.getCurrentUserIdOrThrow();

        return documentRepository.findByCreatedBy(currentUserId);
    }

    @Transactional(readOnly = true)
    public boolean hasAccessToDocument(UUID documentId) {
        return securityUtils.getCurrentUserId()
                .map(userId -> documentRepository.existsByIdAndCreatedBy(documentId, userId))
                .orElse(false);
    }

    private void validateUserAccess(Document document, UUID userId) {
        // TODO: давать доступ пользователям по почте
    }
}
