package com.dodu.intervention.domain;

import java.util.Locale;
import java.util.Optional;

/**
 * AI가 사진에서 본 물건(label)이 약속한 인증 도구와 같은지 판단한다.
 * 약속 도구는 COMPUTER/BOOK이며, 과거 데이터의 소문자·LAPTOP 값도 같은 도구로 본다.
 * AI label은 worker 계약(docs/api/AI_WORKER_CONTRACT.md)의 laptop/book/other이다.
 */
public final class PhotoToolMatchPolicy {
    public static final String TOOL_MISMATCH = "AI_TOOL_MISMATCH";

    private PhotoToolMatchPolicy() { }

    /** 약속 도구에 대응하는 AI label. 알 수 없는 도구 값이면 비어 있다. */
    public static Optional<String> expectedLabel(String promiseTool) {
        if (promiseTool == null) return Optional.empty();
        return switch (promiseTool.trim().toUpperCase(Locale.ROOT)) {
            case "COMPUTER", "LAPTOP" -> Optional.of("laptop");
            case "BOOK" -> Optional.of("book");
            default -> Optional.empty();
        };
    }

    /** label이 약속 도구와 같을 때만 true. 도구를 알 수 없거나 label이 없으면 false. */
    public static boolean matches(String promiseTool, String detectedLabel) {
        return detectedLabel != null && expectedLabel(promiseTool)
                .map(expected -> expected.equals(detectedLabel.trim().toLowerCase(Locale.ROOT)))
                .orElse(false);
    }
}
