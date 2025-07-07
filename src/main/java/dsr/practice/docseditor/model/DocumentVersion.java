package dsr.practice.docseditor.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "document_versions")
@Getter
@Setter
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentVersion {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    private UUID documentId;
    private String versionName;
    private String content;
    private UUID createdBy;
    private LocalDateTime createdAt;
    
    @Transient
    private String authorName;
}
