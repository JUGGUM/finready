package io.finready.integration;

import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * F03부터 필요한 실 Postgres 통합 테스트 공통 베이스 (TRD §17).
 *
 * <p>application-test.yaml은 순수 단위 테스트용으로 datasource를 더미로 막고
 * flyway·ddl-auto를 꺼둔다. 여기서는 {@link DynamicPropertySource}로 그 값을
 * 컨테이너 실접속 정보로 덮어써서, 같은 {@code test} 프로파일 안에서 통합 테스트만
 * 실제 DB를 쓰게 한다 — 별도 프로파일 파일을 만들 필요가 없다.
 *
 * <p><b>싱글턴 컨테이너 패턴이다.</b> {@code @Testcontainers} + {@code @Container} 를 쓰면
 * JUnit 확장이 <b>테스트 클래스마다</b> 컨테이너를 stop 한다. 반면 Spring 은 같은 설정의
 * 애플리케이션 컨텍스트를 클래스 사이에 캐시해 재사용하므로, 먼저 끝난 클래스가 컨테이너를
 * 내리면 뒤 클래스는 살아 있는 컨텍스트로 <b>죽은 컨테이너</b>에 붙어 connection refused 로
 * 터진다. 그래서 여기서는 static 초기화로 한 번만 띄우고 stop 하지 않는다 —
 * 정리는 Testcontainers 의 Ryuk 컨테이너가 JVM 종료 후 맡는다.
 */
@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
public abstract class AbstractPostgresIntegrationTest {

	static final PostgreSQLContainer<?> POSTGRES =
			new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

	static {
		POSTGRES.start();
	}

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
