package com.pulse.service;

public class EndpointNotAllowedException extends IllegalArgumentException {
    public EndpointNotAllowedException(String message) {
        super(message);
    }
}
