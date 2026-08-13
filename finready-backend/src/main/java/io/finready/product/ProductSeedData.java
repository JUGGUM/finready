package io.finready.product;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 시드의 product 블록.
 * documentFileName·reference 는 DB 컬럼이 아니라 사람이 읽는 근거 메모다. 매핑하지 않는다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProductSeedData(
		String id,
		String name,
		String archetype,
		String productRiskVersion,
		String documentId,
		String documentUrl,
		Integer documentPageCount,
		String documentSha256,
		Boolean isLiveDemo,
		String syntheticNotice
) {
}
