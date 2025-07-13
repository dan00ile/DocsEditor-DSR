package dsr.practice.docseditor.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class EditOperation {
    private UUID documentId;
    private int position;
    private String type;
    private String character;
    private String clientId;
    private long clientTimestamp;
    private Long serverTimestamp;
    private UUID userId;
} 