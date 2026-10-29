package com.dodu.operator.cli;

import com.dodu.common.exception.BusinessException;
import com.dodu.operator.domain.OperatorAction;
import com.dodu.operator.domain.OperatorResult;
import com.dodu.operator.repository.OperatorAccessRepository;
import com.dodu.operator.service.OperatorAccessService;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 운영자 명령 실행기(계약 §9 옵션 A). {@code operator} 프로필로 실행할 때만 켜지며,
 * 명령 하나를 처리하고 프로세스를 종료합니다. 웹 포트는 열지 않습니다.
 *
 * <pre>
 * issue    --participant=&lt;uuid&gt; --operator=&lt;이름&gt;
 * reissue  --participant=&lt;uuid&gt; --operator=&lt;이름&gt;
 * withdraw --participant=&lt;uuid&gt; --operator=&lt;이름&gt; --requested-at=&lt;ISO-8601, 예: 2026-10-25T14:00:00+09:00&gt;
 * </pre>
 *
 * 접근 코드 원문은 표준 출력에 한 번만 보여주고, 로그에는 남기지 않습니다.
 */
@Component
@Profile("operator")
public class OperatorCommandRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(OperatorCommandRunner.class);

    private static final int EXIT_OK = 0;
    private static final int EXIT_REJECTED = 1;
    private static final int EXIT_USAGE = 2;
    private static final int EXIT_FAILED = 3;

    private final OperatorAccessService service;
    private final OperatorAccessRepository repository;
    private final Clock clock;
    private final ConfigurableApplicationContext context;

    public OperatorCommandRunner(
            OperatorAccessService service,
            OperatorAccessRepository repository,
            Clock clock,
            ConfigurableApplicationContext context) {
        this.service = service;
        this.repository = repository;
        this.clock = clock;
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments args) {
        int exitCode = execute(args);
        System.exit(SpringApplication.exit(context, () -> exitCode));
    }

    private int execute(ApplicationArguments args) {
        List<String> commands = args.getNonOptionArgs();
        if (commands.size() != 1) {
            return usage("명령은 하나만 입력해 주세요: issue | reissue | withdraw");
        }
        String command = commands.get(0);
        String operatorRef = option(args, "operator");
        UUID participantId;
        try {
            participantId = UUID.fromString(option(args, "participant"));
        } catch (IllegalArgumentException | NullPointerException e) {
            return usage("--participant 에 참가자 UUID를 입력해 주세요.");
        }

        OperatorAction action = switch (command) {
            case "issue" -> OperatorAction.ISSUE_CREDENTIAL;
            case "reissue" -> OperatorAction.REISSUE_CREDENTIAL;
            case "withdraw" -> OperatorAction.WITHDRAW;
            default -> null;
        };
        if (action == null) {
            return usage("알 수 없는 명령이에요: " + command);
        }

        try {
            switch (action) {
                case ISSUE_CREDENTIAL -> {
                    var issued = service.issue(participantId, operatorRef);
                    printIssued("발급 완료", issued.code());
                }
                case REISSUE_CREDENTIAL -> {
                    var issued = service.reissue(participantId, operatorRef);
                    printIssued("재발급 완료 (기존 코드·세션·알림 구독 종료됨)", issued.code());
                }
                case WITHDRAW -> {
                    Instant requestedAt = parseRequestedAt(option(args, "requested-at"));
                    var result = service.withdraw(participantId, requestedAt, operatorRef);
                    System.out.printf("철회 반영 완료 (종료된 세션 %d개, 해제된 알림 구독 %d개)%n",
                            result.endedSessions(), result.deactivatedSubscriptions());
                }
            }
            return EXIT_OK;
        } catch (BusinessException e) {
            System.err.println("거부됨 [" + e.code() + "] " + e.getMessage());
            recordRejected(action, participantId, operatorRef, e.code());
            return EXIT_REJECTED;
        } catch (IllegalArgumentException e) {
            return usage(e.getMessage());
        } catch (RuntimeException e) {
            // 메시지에 비밀이 섞일 가능성을 피하려고 예외 종류만 출력합니다.
            log.error("운영 명령 실패: action={}, exception={}", action, e.getClass().getSimpleName());
            System.err.println("실패: 서버 로그를 확인해 주세요.");
            return EXIT_FAILED;
        }
    }

    private static Instant parseRequestedAt(String value) {
        if (value == null) {
            throw new IllegalArgumentException("--requested-at 에 철회 요청 시각을 입력해 주세요. 예: 2026-10-25T14:00:00+09:00");
        }
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("--requested-at 형식이 올바르지 않아요. 예: 2026-10-25T14:00:00+09:00");
        }
    }

    /** 거부된 시도를 감사 로그에 남깁니다. 서비스 트랜잭션은 이미 롤백됐으므로 별도로 기록합니다. */
    private void recordRejected(OperatorAction action, UUID participantId, String operatorRef, String errorCode) {
        if (operatorRef == null || operatorRef.isBlank()) {
            return; // operator_ref는 필수 컬럼이라 기록할 수 없습니다.
        }
        try {
            repository.insertAuditLog(action, participantId, operatorRef, OperatorResult.REJECTED,
                    "{\"reason\":\"%s\"}".formatted(errorCode), Instant.now(clock));
        } catch (RuntimeException e) {
            // 참가자 없음 등으로 기록이 안 돼도 명령의 결과(거부)는 바뀌지 않습니다.
            log.warn("거부 감사 로그 기록 실패: exception={}", e.getClass().getSimpleName());
        }
    }

    private static void printIssued(String title, String code) {
        System.out.println(title);
        System.out.println("접근 코드: " + code);
        System.out.println("이 코드는 다시 볼 수 없어요. 지금 안전하게 전달해 주세요.");
    }

    private static String option(ApplicationArguments args, String name) {
        List<String> values = args.getOptionValues(name);
        return (values == null || values.isEmpty()) ? null : values.get(0);
    }

    private static int usage(String message) {
        System.err.println(message);
        System.err.println("""
                사용법:
                  issue    --participant=<uuid> --operator=<이름>
                  reissue  --participant=<uuid> --operator=<이름>
                  withdraw --participant=<uuid> --operator=<이름> --requested-at=<ISO-8601>""");
        return EXIT_USAGE;
    }
}