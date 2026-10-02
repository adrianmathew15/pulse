package com.pulse.service;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EndpointDestinationValidatorTest {
    @Test
    void acceptsPublicHttpAndHttpsDestinations() throws Exception {
        var validator = validator(Map.of("public.example", addresses("8.8.8.8", "2606:4700:4700::1111")));

        assertThat(validator.validate("https://public.example/health").getHost())
                .isEqualTo("public.example");
        assertThat(validator.validate("http://8.8.8.8").getScheme()).isEqualTo("http");
    }

    @Test
    void blocksLoopbackPrivateLinkLocalMetadataAndDockerNames() {
        var validator = validator(Map.of());

        assertBlocked(validator, "http://127.0.0.1");
        assertBlocked(validator, "http://127.42.12.9");
        assertBlocked(validator, "http://0.0.0.0");
        assertBlocked(validator, "http://10.0.0.1");
        assertBlocked(validator, "http://172.16.1.2");
        assertBlocked(validator, "http://192.168.1.2");
        assertBlocked(validator, "http://169.254.169.254/latest/meta-data");
        assertBlocked(validator, "http://[::1]");
        assertBlocked(validator, "http://[fc00::1]");
        assertBlocked(validator, "http://[fe80::1]");
        assertBlocked(validator, "http://postgres:5432");
        assertBlocked(validator, "http://metadata.google.internal");
    }

    @Test
    void blocksDnsRebindingWhenAnyResolvedAddressIsPrivate() throws Exception {
        var validator = validator(Map.of("mixed.example", addresses("8.8.8.8", "192.168.1.8")));
        assertBlocked(validator, "https://mixed.example");
    }

    @Test
    void rejectsUnsupportedSchemesAndCredentials() {
        var validator = validator(Map.of());
        assertThatThrownBy(() -> validator.validate("file:///etc/passwd"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> validator.validate("https://user:pass@public.example"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> validator.validate("not a URL"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private EndpointDestinationValidator validator(Map<String, InetAddress[]> records) {
        HostResolver resolver = host -> {
            InetAddress[] resolved = records.get(host);
            if (resolved != null) return resolved;
            try {
                return addresses(host);
            } catch (Exception exception) {
                throw new UnknownHostException(host);
            }
        };
        return new EndpointDestinationValidator(resolver);
    }

    private static InetAddress[] addresses(String... values) throws Exception {
        InetAddress[] result = new InetAddress[values.length];
        for (int index = 0; index < values.length; index++) {
            result[index] = InetAddress.getByName(values[index]);
        }
        return result;
    }

    private void assertBlocked(EndpointDestinationValidator validator, String endpoint) {
        assertThatThrownBy(() -> validator.validate(endpoint))
                .isInstanceOf(EndpointNotAllowedException.class)
                .hasMessage("Endpoint destination is not allowed");
    }
}
