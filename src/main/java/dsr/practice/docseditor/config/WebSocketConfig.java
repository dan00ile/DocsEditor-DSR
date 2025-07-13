package dsr.practice.docseditor.config;

import dsr.practice.docseditor.security.AuthenticationInterceptor;
import dsr.practice.docseditor.security.JwtTokenProvider;
import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.security.core.Authentication;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import java.security.Principal;
import java.util.Map;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE + 99)
@Slf4j
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtTokenProvider jwtTokenProvider;
    private final AuthenticationInterceptor authenticationInterceptor;
    
    @Bean
    public ThreadPoolTaskScheduler webSocketTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("websocket-heartbeat-thread-");
        scheduler.initialize();
        return scheduler;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue")
               .setTaskScheduler(webSocketTaskScheduler())
               .setHeartbeatValue(new long[] {10000, 10000});
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .addInterceptors(new HandshakeInterceptor() {
                    @Override
                    public boolean beforeHandshake(ServerHttpRequest request,
                                                  ServerHttpResponse response,
                                                  WebSocketHandler wsHandler, 
                                                  Map<String, Object> attributes) {
                        try {
                            log.debug("WebSocket handshake начат: {}", request.getURI());

                            if (request.getURI().getQuery() != null) {
                                String query = request.getURI().getQuery();
                                for (String param : query.split("&")) {
                                    if (param.startsWith("token=")) {
                                        String token = param.substring(6);
                                        log.debug("Получен токен из URL параметра: {}", token.substring(0, Math.min(10, token.length())) + "...");
                                        attributes.put("token", token);

                                        try {
                                            Authentication auth = jwtTokenProvider.getAuthentication(token);
                                            if (auth != null) {
                                                log.debug("Аутентификация успешна для пользователя: {}", auth.getName());
                                                attributes.put("principal", auth);
                                                return true;
                                            } else {
                                                log.warn("Токен действителен, но аутентификация не удалась");
                                            }
                                        } catch (Exception e) {
                                            log.error("Ошибка аутентификации по токену из URL: {}", e.getMessage());
                                        }
                                    }
                                }
                            }

                            String authHeader = request.getHeaders().getFirst("Authorization");
                            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                                String token = authHeader.substring(7);
                                log.debug("Получен токен из заголовка Authorization: {}", token.substring(0, Math.min(10, token.length())) + "...");
                                attributes.put("token", token);

                                try {
                                    Authentication auth = jwtTokenProvider.getAuthentication(token);
                                    if (auth != null) {
                                        log.debug("Аутентификация успешна для пользователя: {}", auth.getName());
                                        attributes.put("principal", auth);
                                        return true;
                                    } else {
                                        log.warn("Токен действителен, но аутентификация не удалась");
                                    }
                                } catch (Exception e) {
                                    log.error("Ошибка аутентификации по токену из заголовка: {}", e.getMessage());
                                }
                            }
                            
                            log.warn("Токен авторизации не найден при WebSocket handshake");
                            return true;
                        } catch (Exception e) {
                            log.error("Ошибка при обработке WebSocket handshake: {}", e.getMessage());
                            return true;
                        }
                    }

                    @Override
                    public void afterHandshake(ServerHttpRequest request,
                                               ServerHttpResponse response,
                                               WebSocketHandler wsHandler,
                                               @Nullable Exception ex) {
                        if (ex != null) {
                            log.error("Ошибка после WebSocket handshake: {}", ex.getMessage());
                        } else {
                            log.debug("WebSocket handshake успешно завершен");
                        }
                    }
                })
                .setHandshakeHandler(new DefaultHandshakeHandler() {
                    @Override
                    protected Principal determineUser(ServerHttpRequest request,
                                                    WebSocketHandler wsHandler, 
                                                    Map<String, Object> attributes) {
                        if (attributes.containsKey("principal")) {
                            log.debug("Используем principal из атрибутов handshake");
                            return (Principal) attributes.get("principal");
                        }
                        log.debug("Principal не найден в атрибутах handshake");
                        return super.determineUser(request, wsHandler, attributes);
                    }
                })
                .withSockJS(); 
    }
    
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(authenticationInterceptor);

        registration.taskExecutor()
            .corePoolSize(5)
            .maxPoolSize(10)
            .queueCapacity(100);
    }
    
    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        registration.taskExecutor()
            .corePoolSize(5)
            .maxPoolSize(10)
            .queueCapacity(100);
    }
    
    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.setMessageSizeLimit(128 * 1024);
        registration.setSendBufferSizeLimit(512 * 1024);
        registration.setSendTimeLimit(20000);
    }
} 