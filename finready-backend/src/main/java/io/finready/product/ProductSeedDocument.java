package io.finready.product;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * seed/product_a_risk_schema.json 의 루트.
 * $schema·note 는 사람이 읽는 메타라 매핑하지 않는다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProductSeedDocument(
		ProductSeedData product,
		List<RiskSeedData> risks
) {
}
