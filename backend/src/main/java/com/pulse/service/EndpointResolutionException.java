package com.pulse.service;

class EndpointResolutionException extends IllegalArgumentException {
    EndpointResolutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
