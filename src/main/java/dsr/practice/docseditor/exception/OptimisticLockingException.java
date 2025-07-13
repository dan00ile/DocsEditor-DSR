package dsr.practice.docseditor.exception;

public class OptimisticLockingException extends RuntimeException {
    public OptimisticLockingException(String message) {
        super(message);
    }
    
    public OptimisticLockingException(String message, Throwable cause) {
        super(message, cause);
    }
} 