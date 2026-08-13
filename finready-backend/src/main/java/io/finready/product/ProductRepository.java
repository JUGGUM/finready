package io.finready.product;

import org.springframework.data.jpa.repository.JpaRepository;

/** id 가 시드 지정 문자열이므로 키 타입이 String 이다 */
public interface ProductRepository extends JpaRepository<Product, String> {
}
