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
	                  short attempt) {
		this.sessionId = sessionId;
		this.stage = stage;
		this.promptVersion = promptVersion;
		this.model = model;
		this.requestSummary = requestSummary;
		this.rawResponse = rawResponse;
		this.parsedOk = parsedOk;
		this.latencyMs = latencyMs;
		this.attempt = attempt;
		this.createdAt = OffsetDateTime.now();
	}
}
