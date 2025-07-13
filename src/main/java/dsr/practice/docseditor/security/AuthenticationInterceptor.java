package dsr.practice.docseditor.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class AuthenticationInterceptor implements ChannelInterceptor {
    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        
        if (accessor == null) {
            log.warn("WebSocket: StompHeaderAccessor is null");
            return message;
        }

        if (accessor.getUser() != null) {
            log.debug("WebSocket: User already authenticated as {}", accessor.getUser().getName());
            if (accessor.getUser() instanceof Authentication) {
                SecurityContextHolder.getContext().setAuthentication((Authentication) accessor.getUser());
            }
            return message;
        }

        if (accessor.getCommand() == null) {
            log.warn("WebSocket: Command is null");
            return message;
        }

        log.debug("WebSocket {} command received", accessor.getCommand());
        
        String token = extractToken(accessor);
        if (token != null) {
            try {
                log.debug("WebSocket: Token found, attempting authentication");
                Authentication auth = jwtTokenProvider.getAuthentication(token);
                if (auth != null) {
                    SecurityContextHolder.getContext().setAuthentication(auth);
                    accessor.setUser(auth);
                    
                    if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                        log.debug("WebSocket CONNECT: аутентификация установлена для {}", auth.getName());
                    } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
                        log.debug("WebSocket SUBSCRIBE: {} подписался на {}", 
                                auth.getName(), accessor.getDestination());
                    } else if (StompCommand.SEND.equals(accessor.getCommand())) {
                        log.debug("WebSocket SEND: {} отправляет сообщение на {}", 
                                auth.getName(), accessor.getDestination());
                    }
                } else {
                    log.warn("WebSocket: Authentication failed, token is invalid");
                }
            } catch (Exception e) {
                log.error("Ошибка аутентификации WebSocket: {}", e.getMessage());
            }
        } else if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            log.warn("WebSocket CONNECT без токена авторизации");
            accessor.getMessageHeaders().forEach((key, value) -> {
                log.debug("WebSocket header: {} = {}", key, value);
            });
            
            List<String> nativeHeaders = accessor.getNativeHeader("Authorization");
            if (nativeHeaders != null) {
                log.debug("Authorization headers: {}", nativeHeaders);
            } else {
                log.debug("No Authorization headers found");
            }
        }
        
        return message;
    }
    
    private String extractToken(StompHeaderAccessor accessor) {
        String authorization = accessor.getFirstNativeHeader("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) {
            log.debug("Token found in first native header: {}", authorization.substring(0, 15) + "...");
            return authorization.substring(7);
        }

        List<String> authorizationParams = accessor.getNativeHeader("Authorization");
        if (authorizationParams != null && !authorizationParams.isEmpty()) {
            String authParam = authorizationParams.getFirst();
            if (authParam != null && authParam.startsWith("Bearer ")) {
                log.debug("Token found in authorization params: {}", authParam.substring(0, 15) + "...");
                return authParam.substring(7);
            }
        }
        
        log.debug("No token found in headers");
        return null;
    }
} 