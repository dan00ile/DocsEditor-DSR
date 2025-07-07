package dsr.practice.docseditor.repository;

import dsr.practice.docseditor.model.DocumentVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DocumentVersionRepository extends JpaRepository<DocumentVersion, UUID> {
    List<DocumentVersion> getDocumentVersionsByDocumentId(UUID documentId);
    DocumentVersion getDocumentVersionById(UUID id);
    boolean existsByDocumentIdAndVersionName(UUID documentId, String versionName);
    void deleteAllByDocumentId(UUID documentId);
}
