package io.finready.product;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductRiskRepository extends JpaRepository<ProductRisk, Long> {

	List<ProductRisk> findByProductIdOrderByRiskIdAsc(String productId);

	/**
	 * 재시드 시 해당 상품의 Risk 를 통째로 지우고 다시 넣는다.
	 * product_risk.id 를 참조하는 테이블이 없어 안전하다 — 다른 테이블은 risk_id 문자열만 들고 있다.
	 */
	long deleteByProductId(String productId);
}
