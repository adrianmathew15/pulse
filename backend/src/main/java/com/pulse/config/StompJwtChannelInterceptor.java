package com.pulse.config;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;

@Component
public class StompJwtChannelInterceptor implements ChannelInterceptor {
    private final JwtDecoder jwtDecoder;

    public StompJwtChannelInterceptor(JwtDecoder jwtDecoder) {
        this.jwtDecoder = jwtDecoder;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) throw denied();
        StompCommand command = accessor.getCommand();
        if (command == StompCommand.CONNECT || command == StompCommand.STOMP) {
            accessor.setUser(authenticate(accessor.getFirstNativeHeader("Authorization")));
        } else if (command != null && command != StompCommand.DISCONNECT) {
            if (!(accessor.getUser() instanceof Authentication authentication)
                    || !authentication.isAuthenticated()) throw denied();
        }
        return message;
    }

    private JwtAuthenticationToken authenticate(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) throw denied();
        String token = authorization.substring(7).trim();
        if (token.isEmpty()) throw denied();
        try {
            var jwt = jwtDecoder.decode(token);
            return new JwtAuthenticationToken(jwt, java.util.List.of(), jwt.getSubject());
        } catch (JwtException exception) {
            throw denied();
        }
    }

    private AccessDeniedException denied() {
        return new AccessDeniedException("Authentication is required");
    }
}
