package io.finready.integration;

import io.finready.ai.AiProperties;
import io.finready.coverage.CoverageAnalysisService;
import io.finready.coverage.CoverageClassifier;
import io.finready.coverage.CoverageController;
import io.finready.coverage.SemanticVerifier;
import io.finready.understanding.UnderstandingController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * F03 배선 확인. <b>LLM 이 설정되지 않은 상태에서도 애플리케이션이 뜬다</b>는 것이 요점이다 —
 * 모델 선정(TRD D-02)을 기다리는 동안 나머지를 붙일 수 있게 하려고 스텁 빈을 뒀다.
 *
 * <p>동시에, 스텁이 <b>조용히 넘어가지 않는지</b>도 본다. 빈 결과를 돌려주면 모든 Risk 가
 * "설명 안 됨"으로 읽혀 Gate 가 잠기고, 원인이 코드가 아니라 설정이라는 걸 알기 어렵다.
 */
@DisplayName("F03/F04 배선 (Testcontainers)")
@TestPropertySource(properties = {
		// 키를 비워 둔 채로 기동하는지 본다. application.yaml 의 기본값이 필수로 바뀌면
		// 여기서 컨텍스트 기동 자체가 실패한다
		"finready.ai.api-key=",
		"finready.ai.model=claude-sonnet-4-6"
})
class CoverageWiringIntegrationTest extends AbstractPostgresIntegrationTest {

	@Autowired
	private CoverageController coverageController;

	@Autowired
	private CoverageAnalysisService coverageAnalysisService;

	@Autowired
	private UnderstandingController understandingController;

	@Autowired
	private CoverageClassifier classifier;

	@Autowired
	private SemanticVerifier semanticVerifier;

	@Autowired
	private AiProperties aiProperties;

	@Test
	@DisplayName("LLM 미설정 상태로도 컨텍스트가 기동하고 빈이 모두 배선된다")
	void contextStartsWithoutLlmConfigured() {
		assertThat(coverageController).isNotNull();
		assertThat(coverageAnalysisService).isNotNull();
		assertThat(understandingController).isNotNull();
		assertThat(classifier).isNotNull();
		assertThat(semanticVerifier).isNotNull();
	}

	@Test
	@DisplayName("키가 비어도 바인딩은 성공하고 미설정으로 판정된다")
	void propertiesBindWithoutKey() {
		assertThat(aiProperties.isConfigured()).isFalse();
		assertThat(aiProperties.model()).isEqualTo("claude-sonnet-4-6");
		assertThat(aiProperties.toString()).doesNotContain("api-key=");
	}

	@Test
	@DisplayName("분류기 스텁은 호출되면 설정 누락을 명시적으로 알린다")
	void classifierStubFailsLoudly() {
		Throwable thrown = catchThrowable(() -> classifier.classify("S-1", "상담 원문", List.of()));

		assertThat(thrown)
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("LLM_API_KEY");
	}

	@Test
	@DisplayName("Verifier 스텁도 마찬가지다 — 빈 결과를 돌려주지 않는다")
	void verifierStubFailsLoudly() {
		Throwable thrown = catchThrowable(() -> semanticVerifier.verify("S-1", "상담 원문", List.of()));

		assertThat(thrown)
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("LLM_API_KEY");
	}
}
