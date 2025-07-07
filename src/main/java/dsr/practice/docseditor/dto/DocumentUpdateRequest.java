package dsr.practice.docseditor.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DocumentUpdateRequest {
    @NotBlank
    private String content;
    @NotNull
    private LocalDateTime lastKnownUpdate;
    private String clientId;
}
