package dsr.practice.docseditor.exception;

import dsr.practice.docseditor.dto.ApiError;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> handleResponseStatusException(ResponseStatusException ex) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        
        ApiError apiError = ApiError.builder()
                .status(status.value())
                .message(ex.getReason())
                .error(status.getReasonPhrase())
                .timestamp(LocalDateTime.now())
                .build();
        
        return new ResponseEntity<>(apiError, status);
    }
    
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleHttpMessageNotReadable(HttpMessageNotReadableException ex) {
        String message = "Ошибка в формате JSON запроса";
        if (ex.getMessage().contains("Unrecognized field")) {
            message = "Неизвестное поле в запросе. Проверьте структуру запроса.";
        }
        
        ApiError apiError = ApiError.builder()
                .status(HttpStatus.BAD_REQUEST.value())
                .message(message)
                .error("BAD_REQUEST")
                .timestamp(LocalDateTime.now())
                .build();
        
        return new ResponseEntity<>(apiError, HttpStatus.BAD_REQUEST);
    }
    
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiError> handleBadCredentialsException(BadCredentialsException ex) {
        ApiError apiError = ApiError.builder()
                .status(HttpStatus.UNAUTHORIZED.value())
                .message(ex.getMessage())
                .error("UNAUTHORIZED")
                .timestamp(LocalDateTime.now())
                .build();
        
        return new ResponseEntity<>(apiError, HttpStatus.UNAUTHORIZED);
    }
    
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDeniedException(AccessDeniedException ex) {
        ApiError apiError = ApiError.builder()
                .status(HttpStatus.FORBIDDEN.value())
                .message(ex.getMessage())
                .error("FORBIDDEN")
                .timestamp(LocalDateTime.now())
                .build();
        
        return new ResponseEntity<>(apiError, HttpStatus.FORBIDDEN);
    }
    
    @ExceptionHandler(DocumentNotFoundException.class)
    public ResponseEntity<ApiError> handleDocumentNotFoundException(DocumentNotFoundException ex) {
        ApiError apiError = ApiError.builder()
                .status(HttpStatus.NOT_FOUND.value())
                .message(ex.getMessage())
                .error("NOT_FOUND")
                .timestamp(LocalDateTime.now())
                .build();
        
        return new ResponseEntity<>(apiError, HttpStatus.NOT_FOUND);
    }
    
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidationExceptions(MethodArgumentNotValidException ex) {
        List<Map<String, String>> errors = new ArrayList<>();
        
        ex.getBindingResult().getFieldErrors().forEach(error -> {
            Map<String, String> errorDetails = new HashMap<>();
            errorDetails.put("field", error.getField());
            errorDetails.put("message", error.getDefaultMessage());
            errors.add(errorDetails);
        });
        
        ApiError apiError = ApiError.builder()
                .status(HttpStatus.BAD_REQUEST.value())
                .message("Ошибка валидации")
                .error("BAD_REQUEST")
                .timestamp(LocalDateTime.now())
                .errors(errors)
                .build();
        
        return new ResponseEntity<>(apiError, HttpStatus.BAD_REQUEST);
    }
    
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGenericException(Exception ex) {
        log.error("Необработанное исключение", ex);
        
        ApiError apiError = ApiError.builder()
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .message("Внутренняя ошибка сервера")
                .error("INTERNAL_SERVER_ERROR")
                .timestamp(LocalDateTime.now())
                .build();
        
        return new ResponseEntity<>(apiError, HttpStatus.INTERNAL_SERVER_ERROR);
    }
} 