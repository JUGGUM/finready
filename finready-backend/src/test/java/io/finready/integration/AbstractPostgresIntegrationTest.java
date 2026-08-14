package io.finready.integration;

import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * F03부터 필요한 실 Postgres 통합 테스트 공통 베이스 (TRD §17).
 *
 * <p>application-test.yaml은 순수 단위 테스트용으로 datasource를 더미로 막고
 * flyway·ddl-auto를 꺼둔다. 여기서는 {@link DynamicPropertySource}로 그 값을
 * 컨테이너 실접속 정보로 덮어써서, 같은 {@code test} 프로파일 안에서 통합 테스트만
 * 실제 DB를 쓰게 한다 — 별도 프로파일 파일을 만들 필요가 없다.
 *
 * <p>컨테이너는 클래스당 하나(static)라 상속 트리 전체가 같은 인스턴스를 공유한다.
 * 테스트 간 데이터 격리가 필요하면 각 테스트에서 직접 정리할 것 — Flyway는
 * 스키마만 만들고 clean은 하지 않는다.
 */
@Testcontainers
@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
public abstract class AbstractPostgresIntegrationTest {

	@Container
	static final PostgreSQLContainer<?> POSTGRES =
			new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

	@DynamicPropertySource
	static void datasourceProperties(DynamicPropertyRegistry registry) {
		// application.yaml의 finready 스키마 설정(Supabase 전용)과 맞춘다. 안 붙이면
		// 세션 search_path가 public이라 flyway.schemas: finready로 만든 테이블이 안 보인다.
		registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl() + "&currentSchema=finready");
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);
		registry.add("spring.flyway.enabled", () -> true);
		registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
	}
}
