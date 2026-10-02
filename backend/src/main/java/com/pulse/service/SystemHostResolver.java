package com.pulse.service;

import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;

@Component
class SystemHostResolver implements HostResolver {
    @Override
    public InetAddress[] resolve(String hostname) throws UnknownHostException {
        return InetAddress.getAllByName(hostname);
    }
}
