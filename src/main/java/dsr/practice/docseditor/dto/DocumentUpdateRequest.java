package dsr.practice.docseditor.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DocumentUpdateRequest {
    @NotBlank
    private String content;
    private LocalDateTime lastKnownUpdate;
    private String clientId;
    private UUID userId;
}
