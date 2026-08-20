package io.finready.ai;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * V1__init.sql / llm_call_log.
 * 평가 재현과 성능 실측의 원천이며 prompt_version 이 Hold-out 재현 조건이다 (TRD §7.2).
 *
 * <p>session_id 가 nullable 이다 — 세션 밖에서 도는 오프라인 평가 호출도 여기에 쌓인다.
 */
@Entity
@Table(name = "llm_call_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LlmCallLog {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id", nullable = false)
	private Long id;

	/** DDL 에 not null 이 없다. 세션에 속하지 않는 호출을 허용한다 */
	@Column(name = "session_id", length = 40)
	private String sessionId;

	@Column(name = "stage", length = 32, nullable = false)
	private String stage;

	@Column(name = "prompt_version", length = 32, nullable = false)
	private String promptVersion;

	@Column(name = "model", length = 64, nullable = false)
	private String model;

	/** API 키를 넣지 않는다 (규칙 10). 프롬프트 전문이 아니라 요약이다 */
	@Column(name = "request_summary", columnDefinition = "text")
	private String requestSummary;

	@Column(name = "raw_response", columnDefinition = "text")
	private String rawResponse;

	@Column(name = "parsed_ok", nullable = false)
	private boolean parsedOk;

	@Column(name = "latency_ms")
	private Integer latencyMs;

	@Column(name = "attempt", nullable = false)
	private short attempt;

	/**
	 * 토큰 사용량. <b>null 과 0 은 다른 뜻이다</b> — 응답을 받기 전에 실패한 호출에는
	 * 값이 없고, 거기에 0 을 넣으면 "토큰을 0개 썼다"로 읽혀 평균이 오염된다.
	 */
	@Column(name = "input_tokens")
	private Integer inputTokens;

	@Column(name = "output_tokens")
	private Integer outputTokens;

	/** 계속 0 이면 prompt caching 이 조용히 안 걸리는 것이다. 유일한 신호다 */
	@Column(name = "cache_read_tokens")
	private Integer cacheReadTokens;

	@Column(name = "cache_write_tokens")
	private Integer cacheWriteTokens;

	/** {@code prompt_version} 으로 복원되지 않는 레이턴시 변수라 따로 남긴다 */
	@Column(name = "effort", length = 8)
	private String effort;

	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt;

	public LlmCallLog(String sessionId,
	                  String stage,
	                  String promptVersion,
	                  String model,
	                  String requestSummary,
	                  String rawResponse,
	                  boolean parsedOk,
	                  Integer latencyMs,
	                  short attempt,
	                  TokenUsage usage,
	                  String effort) {
		this.sessionId = sessionId;
		this.stage = stage;
		this.promptVersion = promptVersion;
		this.model = model;
		this.requestSummary = requestSummary;
		this.rawResponse = rawResponse;
		this.parsedOk = parsedOk;
		this.latencyMs = latencyMs;
		this.attempt = attempt;
		this.effort = effort;
		if (usage != null) {
			this.inputTokens = usage.input();
			this.outputTokens = usage.output();
			this.cacheReadTokens = usage.cacheRead();
			this.cacheWriteTokens = usage.cacheWrite();
		}
		this.createdAt = OffsetDateTime.now();
	}

	/**
	 * 토큰 4개를 위치 인자로 풀지 않는다. 생성자가 13개 인자가 되면 같은 타입
	 * ({@code Integer}) 넷이 나란히 서서 <b>순서를 바꿔 넘겨도 컴파일이 통과</b>한다.
	 *
	 * <p>응답을 못 받은 호출에는 통째로 {@code null} 을 넘긴다.
	 */
	public record TokenUsage(Integer input, Integer output, Integer cacheRead, Integer cacheWrite) {
	}
}
