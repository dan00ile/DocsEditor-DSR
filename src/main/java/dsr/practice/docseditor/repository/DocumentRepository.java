package dsr.practice.docseditor.repository;

import dsr.practice.docseditor.model.Document;
import dsr.practice.docseditor.model.DocumentVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DocumentRepository extends JpaRepository<Document, UUID> {
    Document findDocumentById(UUID id);
    List<Document> findByCreatedBy(UUID ownerId);
    boolean existsByIdAndCreatedBy(UUID id, UUID ownerId);

}
