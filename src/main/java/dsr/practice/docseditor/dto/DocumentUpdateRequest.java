package dsr.practice.docseditor.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
public class DocumentUpdateRequest {
    private List<EditOperation> operations;
    private LocalDateTime lastKnownUpdate;
    private String clientId;
    private UUID userId;
}
