package com.pulse.service;

import java.net.InetAddress;
import java.net.UnknownHostException;

interface HostResolver {
    InetAddress[] resolve(String hostname) throws UnknownHostException;
}
