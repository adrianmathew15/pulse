package com.pulse.config;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StompJwtChannelInterceptorTest {
    private final JwtDecoder decoder = mock(JwtDecoder.class);
    private final StompJwtChannelInterceptor interceptor = new StompJwtChannelInterceptor(decoder);

    @Test
    void authenticatesConnectWithBearerToken() {
        Jwt jwt = new Jwt("valid", Instant.now(), Instant.now().plusSeconds(60),
                Map.of("alg", "HS256"), Map.of("sub", "pulse-test"));
        when(decoder.decode("valid")).thenReturn(jwt);
        StompHeaderAccessor headers = StompHeaderAccessor.create(StompCommand.CONNECT);
        headers.setNativeHeader("Authorization", "Bearer valid");
        headers.setLeaveMutable(true);

        Message<?> result = interceptor.preSend(
                MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders()), mock(org.springframework.messaging.MessageChannel.class));

        StompHeaderAccessor resultHeaders = MessageHeaderAccessor.getAccessor(result, StompHeaderAccessor.class);
        assertThat(resultHeaders).isNotNull();
        assertThat(resultHeaders.getUser()).isInstanceOf(JwtAuthenticationToken.class);
        assertThat(resultHeaders.getUser().getName()).isEqualTo("pulse-test");
    }

    @Test
    void rejectsMissingAndInvalidConnectTokens() {
        assertThatThrownBy(() -> send(StompCommand.CONNECT, null))
                .isInstanceOf(AccessDeniedException.class);
        when(decoder.decode("bad")).thenThrow(new JwtException("invalid"));
        assertThatThrownBy(() -> send(StompCommand.CONNECT, "Bearer bad"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void rejectsUnauthenticatedSubscriptions() {
        assertThatThrownBy(() -> send(StompCommand.SUBSCRIBE, null))
                .isInstanceOf(AccessDeniedException.class);
    }

    private void send(StompCommand command, String authorization) {
        StompHeaderAccessor headers = StompHeaderAccessor.create(command);
        if (authorization != null) headers.setNativeHeader("Authorization", authorization);
        interceptor.preSend(MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders()),
                mock(org.springframework.messaging.MessageChannel.class));
    }
}
