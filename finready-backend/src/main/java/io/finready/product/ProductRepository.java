package io.finready.product;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** id 가 시드 지정 문자열이므로 키 타입이 String 이다 */
public interface ProductRepository extends JpaRepository<Product, String> {

	/**
	 * F01 의 "Live MVP 대상 상품". 상품 id 를 코드에 박지 않고 is_live_demo 플래그로 찾는다 —
	 * 플래그가 시드에 있는 이유가 이것이다.
	 */
	Optional<Product> findFirstByLiveDemoTrue();
}
