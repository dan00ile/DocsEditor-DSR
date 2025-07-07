package dsr.practice.docseditor.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class SaveVersionRequest {
    private String versionName;
}
