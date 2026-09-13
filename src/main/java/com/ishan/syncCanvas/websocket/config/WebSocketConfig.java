package com.ishan.syncCanvas.websocket.config;

import com.ishan.syncCanvas.websocket.security.StompAuthChannelInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig
        implements WebSocketMessageBrokerConfigurer {

    private final StompAuthChannelInterceptor stompAuthChannelInterceptor;

    /** Same origin allowlist as {@link com.ishan.syncCanvas.security.config.SecurityConfig}. */
    @Value("${app.cors.allowed-origins}")
    private String allowedOrigins;

    @Override
    public void configureMessageBroker(
            MessageBrokerRegistry registry) {
        // "/queue" is required alongside "/topic": every per-user reply (video
        // roster/session/errors, presence/cursor "initial" snapshots, undo/redo
        // and sync replies, chat errors) is sent via convertAndSendToUser, which
        // Spring's UserDestinationMessageHandler rewrites to a physical
        // "/queue/..." destination before handing it to this broker. Without
        // "/queue" registered here, the broker silently drops every one of
        // those messages — no exception, no log, just no delivery.
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {

        registry.addEndpoint("/ws")
                .setAllowedOrigins(allowedOrigins.split(","))
                .withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompAuthChannelInterceptor);
    }
}