package com.pulse.websocket;

import com.pulse.dto.AlertEventResponse;

public record AlertChangedEvent(AlertEventResponse payload) {
}
