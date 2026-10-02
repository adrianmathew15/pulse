package com.pulse.dto;

public record LoginResponse(String token, long expiresIn) {
}
