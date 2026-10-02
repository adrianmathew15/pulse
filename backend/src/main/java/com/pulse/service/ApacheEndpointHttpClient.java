package com.pulse.service;

import jakarta.annotation.PreDestroy;
import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

@Component
class ApacheEndpointHttpClient implements EndpointHttpClient {
    private final CloseableHttpClient client;

    ApacheEndpointHttpClient(EndpointDestinationValidator validator,
                             @Value("${pulse.health.timeout-ms:3000}") long timeoutMs) {
        if (timeoutMs < 1) throw new IllegalArgumentException("Health-check timeout must be positive");
        Timeout timeout = Timeout.ofMilliseconds(timeoutMs);
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(timeout)
                .setConnectTimeout(timeout)
                .setResponseTimeout(timeout)
                .build();
        DnsResolver dnsResolver = new DnsResolver() {
            @Override
            public InetAddress[] resolve(String host) throws UnknownHostException {
                try {
                    return validator.resolveAndValidate(host);
                } catch (EndpointResolutionException exception) {
                    UnknownHostException unknownHost = new UnknownHostException("Endpoint host could not be resolved");
                    unknownHost.initCause(exception);
                    throw unknownHost;
                }
            }

            @Override
            public String resolveCanonicalHostname(String host) throws UnknownHostException {
                resolve(host);
                return host;
            }
        };
        var connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
                .setDnsResolver(dnsResolver)
                .build();
        this.client = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(requestConfig)
                .disableRedirectHandling()
                .build();
    }

    @Override
    public EndpointHttpResponse get(URI uri) throws IOException {
        return client.execute(new HttpGet(uri), response -> {
            var location = response.getFirstHeader("Location");
            return new EndpointHttpResponse(response.getCode(), location == null ? null : location.getValue());
        });
    }

    @PreDestroy
    void close() throws IOException {
        client.close();
    }
}
