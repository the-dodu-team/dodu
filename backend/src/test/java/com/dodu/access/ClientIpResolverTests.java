package com.dodu.access;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ClientIpResolverTests {
    @Test
    void ignoresForwardedHeadersFromUntrustedDirectClient() {
        ClientIpResolver resolver = new ClientIpResolver("10.0.0.0/8");

        assertEquals("192.168.1.20", resolver.resolve("192.168.1.20", "8.8.8.8", null));
    }

    @Test
    void usesFirstForwardedAddressOnlyFromTrustedProxy() {
        ClientIpResolver resolver = new ClientIpResolver("10.0.0.0/8");

        assertEquals("8.8.8.8", resolver.resolve("10.2.3.4", "8.8.8.8, 10.2.3.5", null));
    }

    @Test
    void parsesForwardedHeaderAndSupportsIpv6() {
        ClientIpResolver resolver = new ClientIpResolver("2001:db8::/32");

        assertEquals("2001:db8:1::5", resolver.resolve("2001:db8::1", null,
                "for=\"[2001:db8:1::5]\";proto=https"));
    }
}
