package dsr.practice.docseditor.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import dsr.practice.docseditor.dto.ApiError;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.SignatureException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;

@Slf4j
@RequiredArgsConstructor
@Order(1)
public class JwtExceptionHandler extends OncePerRequestFilter {

    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            filterChain.doFilter(request, response);
        } catch (ExpiredJwtException e) {
            log.error("JWT token expired: {}", e.getMessage());
            setErrorResponse(request, response, HttpStatus.UNAUTHORIZED, e, "Срок действия токена истек", "ExpiredJwtException");
        } catch (SignatureException e) {
            log.error("Invalid JWT signature: {}", e.getMessage());
            setErrorResponse(request, response, HttpStatus.UNAUTHORIZED, e, "Неверная подпись токена", "SignatureException");
        } catch (MalformedJwtException e) {
            log.error("Invalid JWT token: {}", e.getMessage());
            setErrorResponse(request, response, HttpStatus.UNAUTHORIZED, e, "Неверный формат токена", "MalformedJwtException");
        } catch (UnsupportedJwtException e) {
            log.error("Unsupported JWT token: {}", e.getMessage());
            setErrorResponse(request, response, HttpStatus.UNAUTHORIZED, e, "Неподдерживаемый токен", "UnsupportedJwtException");
        } catch (IllegalArgumentException e) {
            log.error("JWT claims string is empty: {}", e.getMessage());
            setErrorResponse(request, response, HttpStatus.UNAUTHORIZED, e, "Недопустимые аргументы JWT", "IllegalArgumentException");
        }
    }

    private void setErrorResponse(
            HttpServletRequest request,
            HttpServletResponse response,
            HttpStatus status,
            Exception ex,
            String message,
            String errorType
    ) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        ApiError apiError = ApiError.builder()
                .status(status.value())
                .message(message)
                .error(errorType)
                .path(request.getRequestURI())
                .timestamp(LocalDateTime.now())
                .build();

        response.getWriter().write(objectMapper.writeValueAsString(apiError));
    }
}