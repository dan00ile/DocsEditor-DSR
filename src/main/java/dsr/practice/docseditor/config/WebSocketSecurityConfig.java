package dsr.practice.docseditor.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.security.config.annotation.web.messaging.MessageSecurityMetadataSourceRegistry;
import org.springframework.security.config.annotation.web.socket.AbstractSecurityWebSocketMessageBrokerConfigurer;

@Configuration
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE + 98)
public class WebSocketSecurityConfig extends AbstractSecurityWebSocketMessageBrokerConfigurer {

    @Override
    protected void configureInbound(MessageSecurityMetadataSourceRegistry messages) {
        messages
                .simpTypeMatchers(SimpMessageType.CONNECT, SimpMessageType.HEARTBEAT,
                        SimpMessageType.UNSUBSCRIBE, SimpMessageType.DISCONNECT).permitAll()
                .simpSubscribeDestMatchers("/topic/**", "/queue/**", "/user/**").authenticated()
                .simpDestMatchers("/app/**").authenticated()
                .anyMessage().authenticated();
    }

    @Override
    protected boolean sameOriginDisabled() {
        return true;
    }
}