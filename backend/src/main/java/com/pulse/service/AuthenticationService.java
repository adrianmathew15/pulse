package com.pulse.service;

import com.pulse.config.SecurityConfig;
import com.pulse.dto.LoginRequest;
import com.pulse.dto.LoginResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Service
public class AuthenticationService {
    private final AuthenticationManager authenticationManager;
    private final JwtEncoder jwtEncoder;
    private final Clock clock;
    private final Duration expiration;

    @Autowired
    public AuthenticationService(AuthenticationManager authenticationManager,
                                 JwtEncoder jwtEncoder,
                                 @Value("${pulse.security.jwt-expiration-ms:900000}") long expirationMs) {
        this(authenticationManager, jwtEncoder, Clock.systemUTC(), expirationMs);
    }

    AuthenticationService(AuthenticationManager authenticationManager, JwtEncoder jwtEncoder,
                          Clock clock, long expirationMs) {
        if (expirationMs < 1) {
            throw new IllegalArgumentException("JWT expiration must be positive");
        }
        this.authenticationManager = authenticationManager;
        this.jwtEncoder = jwtEncoder;
        this.clock = clock;
        this.expiration = Duration.ofMillis(expirationMs);
    }

    public LoginResponse login(LoginRequest request) {
        var authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(
                        request.username().trim(), request.password()));
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(expiration);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(SecurityConfig.JWT_ISSUER)
                .subject(authentication.getName())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new LoginResponse(token, expiration.toMillis());
    }
}
