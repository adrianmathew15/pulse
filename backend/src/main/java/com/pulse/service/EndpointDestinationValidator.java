package com.pulse.service;

import org.springframework.stereotype.Component;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Set;

@Component
public class EndpointDestinationValidator {
    private static final Set<String> BLOCKED_HOSTNAMES = Set.of(
            "localhost", "localhost.localdomain", "metadata", "metadata.google.internal",
            "instance-data", "instance-data.ec2.internal"
    );

    private final HostResolver resolver;

    public EndpointDestinationValidator(HostResolver resolver) {
        this.resolver = resolver;
    }

    public URI validate(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            throw new IllegalArgumentException("Endpoint is not configured");
        }
        final URI uri;
        try {
            uri = URI.create(endpoint.trim());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Endpoint must be a valid absolute HTTP or HTTPS URL");
        }
        validate(uri);
        return uri;
    }

    public void validate(URI uri) {
        String scheme = uri.getScheme();
        if (!("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                || uri.getHost() == null || uri.getUserInfo() != null) {
            throw new IllegalArgumentException("Endpoint must be an absolute HTTP or HTTPS URL");
        }
        resolveAndValidate(normalizeHost(uri.getHost()));
    }

    InetAddress[] resolveAndValidate(String hostname) {
        String normalized = normalizeHost(hostname);
        if (isInternalHostname(normalized)) throw blocked();
        final InetAddress[] addresses;
        try {
            addresses = resolver.resolve(normalized);
        } catch (UnknownHostException exception) {
            throw new EndpointResolutionException("Endpoint host could not be resolved", exception);
        }
        if (addresses.length == 0) {
            throw new EndpointResolutionException("Endpoint host could not be resolved",
                    new UnknownHostException(normalized));
        }
        for (InetAddress address : addresses) {
            if (!isPublic(address)) throw blocked();
        }
        return addresses.clone();
    }

    private boolean isInternalHostname(String hostname) {
        if (hostname.isBlank() || BLOCKED_HOSTNAMES.contains(hostname)) return true;
        if (!hostname.contains(":") && !hostname.contains(".")) return true;
        return hostname.endsWith(".localhost") || hostname.endsWith(".local")
                || hostname.endsWith(".internal") || hostname.endsWith(".lan")
                || hostname.endsWith(".home") || hostname.endsWith(".corp");
    }

    private boolean isPublic(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress()
                || address.isLinkLocalAddress() || address.isSiteLocalAddress()
                || address.isMulticastAddress()) return false;
        byte[] bytes = address.getAddress();
        if (address instanceof Inet4Address) return isPublicIpv4(bytes);
        if (address instanceof Inet6Address) {
            int first = bytes[0] & 0xff;
            if ((first & 0xfe) == 0xfc) return false;
            if (isIpv4Mapped(bytes)) {
                return isPublicIpv4(new byte[]{bytes[12], bytes[13], bytes[14], bytes[15]});
            }
        }
        return true;
    }

    private boolean isPublicIpv4(byte[] bytes) {
        int first = bytes[0] & 0xff;
        int second = bytes[1] & 0xff;
        if (first == 0 || first == 10 || first == 127) return false;
        if (first == 100 && second >= 64 && second <= 127) return false;
        if (first == 169 && second == 254) return false;
        if (first == 172 && second >= 16 && second <= 31) return false;
        if (first == 192 && (second == 0 || second == 168)) return false;
        if (first == 198 && (second == 18 || second == 19)) return false;
        return first < 224;
    }

    private boolean isIpv4Mapped(byte[] bytes) {
        if (bytes.length != 16 || bytes[10] != (byte) 0xff || bytes[11] != (byte) 0xff) return false;
        for (int index = 0; index < 10; index++) {
            if (bytes[index] != 0) return false;
        }
        return true;
    }

    private String normalizeHost(String hostname) {
        String normalized = hostname == null ? "" : hostname.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("[") && normalized.endsWith("]")) {
            return normalized.substring(1, normalized.length() - 1);
        }
        return normalized.endsWith(".") ? normalized.substring(0, normalized.length() - 1) : normalized;
    }

    private EndpointNotAllowedException blocked() {
        return new EndpointNotAllowedException("Endpoint destination is not allowed");
    }
}
