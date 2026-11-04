package com.dodu.notification;

import java.util.List;

/** 커밋 이후 안내에만 쓰는 메모리 이벤트. 비밀 토큰을 문자열 표현에 포함하지 않는다. */
public final class DeviceChangeNotice {
    private final List<String> tokens;
    private final java.util.UUID traceId = java.util.UUID.randomUUID();
    public DeviceChangeNotice(List<String> tokens) { this.tokens = List.copyOf(tokens); }
    public List<String> tokens() { return tokens; }
    public java.util.UUID traceId() { return traceId; }
    @Override public String toString() { return "DeviceChangeNotice[redacted]"; }
}
