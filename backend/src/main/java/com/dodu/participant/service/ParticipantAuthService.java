package com.dodu.participant.service;

import com.dodu.common.exception.BusinessException;
import com.dodu.common.exception.ErrorType;
import com.dodu.participant.domain.*;
import com.dodu.participant.repository.ParticipantAccessRepository;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

@Service
public class ParticipantAuthService {

    public static final String ACCESS_REQUIRED = "ACCESS_REQUIRED";
    public static final String SESSION_REPLACED = "SESSION_REPLACED";
    public static final String ACCESS_REVOKED = "ACCESS_REVOKED";

    private final ParticipantAccessRepository repository;
    private final SecretHasher secretHasher;
    private final Clock clock;

    public ParticipantAuthService(ParticipantAccessRepository repository, SecretHasher secretHasher, Clock clock) {
        this.repository = repository;
        this.secretHasher = secretHasher;
        this.clock = clock;
    }

    public SessionContext authenticate(String authorizationHeader) {
        String token = SessionToken.fromAuthorizationHeader(authorizationHeader)
                .orElseThrow(ParticipantAuthService::accessRequired);
        SessionContext context = repository.findSessionByTokenHash(secretHasher.hash(token))
                .orElseThrow(ParticipantAuthService::accessRequired);

        Instant now = Instant.now(clock);
        switch (context.check(now)) {
            case EXPIRED -> throw accessRequired();
            case REPLACED -> throw new BusinessException(ErrorType.UNAUTHENTICATED, SESSION_REPLACED,
                    "다른 기기에서 이용 중이에요.");
            case REVOKED -> throw accessRevoked();
            case ALLOWED -> {
                // 접근 허용
            }
        }

        if (context.session().isActivityStale(now)) {
            repository.touchSession(context.session().id(), now, now.minus(ParticipantSession.ACTIVITY_UPDATE_INTERVAL));
        }
        return context;
    }

    /** 인증 정보가 없는 요청에 쓰는 오류. 인터셉터를 거치지 않은 요청을 막을 때도 같은 응답을 씁니다. */
    public static BusinessException accessRequired() {
        return new BusinessException(ErrorType.UNAUTHENTICATED, ACCESS_REQUIRED, "코드를 입력해 주세요.");
    }

    private static BusinessException accessRevoked() {
        return new BusinessException(ErrorType.UNAUTHENTICATED, ACCESS_REVOKED, "더 이상 이용할 수 없어요.");
    }
}
