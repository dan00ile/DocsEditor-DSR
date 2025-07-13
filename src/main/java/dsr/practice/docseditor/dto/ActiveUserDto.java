package dsr.practice.docseditor.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ActiveUserDto implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    
    private UUID userId;
    private String username;
    private Integer cursorPosition;
    private Boolean isTyping;
    private String color;
    private LocalDateTime lastActive;
    private Boolean isActive;
    
    @Builder.Default
    private LocalDateTime connectedAt = LocalDateTime.now();
} 