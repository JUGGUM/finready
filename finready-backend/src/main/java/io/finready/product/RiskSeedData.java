package io.finready.product;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 시드의 risks[] 항목.
 *
 * <p>coveragePolicy 를 enum 이 아니라 String 으로 받는다. enum 으로 받으면 잘못된 값이
 * Jackson 파싱 단계에서 터져서 "시드 어느 Risk 의 어느 필드가 문제인지"를 못 알려준다.
 * 검증은 SeedValidator 가 하고 변환은 SeedLoader 가 한다.
 *
 * <p>verifiedAt 도 같은 이유로 String 이다. 시드에는 "2026-08-12" 형태의 날짜만 있고
 * 컬럼은 timestamptz 라 변환이 필요하다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RiskSeedData(
		String riskId,
		String category,
		String title,
		String fact,
		String coveragePolicy,
		Boolean understandingCheck,
		Integer sourcePage,
		String sourceText,
		String fallbackQuestion,
		String fallbackRecheckQuestion,
		String fallbackPlainExplanation,
		String verifiedAt,
		String verifiedBy
) {
}
