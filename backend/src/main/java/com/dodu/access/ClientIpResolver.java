package com.dodu.access;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Resolves the client address without trusting forwarded headers by default. */
@Component
public final class ClientIpResolver {
    private final List<TrustedNetwork> trustedNetworks;

    public ClientIpResolver(@Value("${dodu.access.trusted-proxies:}") String configuredProxies) {
        this.trustedNetworks = parse(configuredProxies);
    }

    public String resolve(String directAddress, String forwardedFor, String forwarded) {
        if (!isTrusted(directAddress)) {
            return directAddress;
        }
        String candidate = firstForwardedAddress(forwardedFor, forwarded);
        return candidate == null ? directAddress : candidate;
    }

    private boolean isTrusted(String address) {
        return trustedNetworks.stream().anyMatch(network -> network.contains(address));
    }

    private static String firstForwardedAddress(String forwardedFor, String forwarded) {
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            String first = forwardedFor.split(",", 2)[0].trim();
            if (isAddress(first)) return first;
        }
        if (forwarded != null && !forwarded.isBlank()) {
            for (String part : forwarded.split(",")) {
                for (String parameter : part.split(";")) {
                    String value = parameter.trim();
                    if (value.regionMatches(true, 0, "for=", 0, 4)) {
                        String address = value.substring(4).trim().replace("\"", "");
                        if (address.startsWith("[")) address = address.substring(1, address.indexOf(']'));
                        else if (address.contains(":")) address = address.substring(0, address.indexOf(':'));
                        if (isAddress(address)) return address;
                    }
                }
            }
        }
        return null;
    }

    private static boolean isAddress(String value) {
        try {
            InetAddress.getByName(value);
            return !value.equalsIgnoreCase("unknown");
        } catch (UnknownHostException exception) {
            return false;
        }
    }

    private static List<TrustedNetwork> parse(String configured) {
        List<TrustedNetwork> networks = new ArrayList<>();
        if (configured == null || configured.isBlank()) return networks;
        Arrays.stream(configured.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(ClientIpResolver::parseNetwork)
                .forEach(network -> { if (network != null) networks.add(network); });
        return List.copyOf(networks);
    }

    private static TrustedNetwork parseNetwork(String value) {
        try {
            String[] parts = value.split("/", 2);
            InetAddress address = InetAddress.getByName(parts[0]);
            int prefix = parts.length == 1 ? address.getAddress().length * 8 : Integer.parseInt(parts[1]);
            if (prefix < 0 || prefix > address.getAddress().length * 8) return null;
            return new TrustedNetwork(address.getAddress(), prefix);
        } catch (UnknownHostException | NumberFormatException exception) {
            return null;
        }
    }

    private record TrustedNetwork(byte[] network, int prefixLength) {
        boolean contains(String candidate) {
            try {
                byte[] address = InetAddress.getByName(candidate).getAddress();
                if (address.length != network.length) return false;
                int wholeBytes = prefixLength / 8;
                int remainingBits = prefixLength % 8;
                for (int i = 0; i < wholeBytes; i++) {
                    if (address[i] != network[i]) return false;
                }
                if (remainingBits == 0) return true;
                int mask = 0xff << (8 - remainingBits);
                return (address[wholeBytes] & mask) == (network[wholeBytes] & mask);
            } catch (UnknownHostException exception) {
                return false;
            }
        }
    }
}
