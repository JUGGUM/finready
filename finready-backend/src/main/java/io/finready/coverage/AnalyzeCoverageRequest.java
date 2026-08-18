package io.finready.coverage;

/**
 * openapi {@code POST /sessions/{sessionId}/coverage} 의 requestBody. 본문 전체가 선택이다.
 *
 * @param revisionId 생략하면 최신 revision 을 분석한다
 */
public record AnalyzeCoverageRequest(Long revisionId) {

	/** 본문 없이 호출할 수 있다 — 계약이 {@code required: false} 다 */
	public static AnalyzeCoverageRequest latest() {
		return new AnalyzeCoverageRequest(null);
	}
}
