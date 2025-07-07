package dsr.practice.docseditor.exception;

public class DuplicateVersionNameException extends RuntimeException {
    public DuplicateVersionNameException(String message) {
        super(message);
    }
    
    public DuplicateVersionNameException(String message, Throwable cause) {
        super(message, cause);
    }
} 