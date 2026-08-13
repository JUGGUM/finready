package io.finready.product;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 고객 preset 한 건.
 * synthetic·note 는 사람이 읽는 메모다. DB 컬럼이 아니다.
 * enum 후보 3개를 String 으로 받는 이유는 RiskSeedData 와 같다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CustomerProfileSeedData(
		String id,
		String label,
		String ageGroup,
		String investmentExperience,
		String financialLiteracy,
		String explanationLevel
) {
}
