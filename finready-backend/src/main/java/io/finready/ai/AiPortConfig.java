package io.finready.ai;

import io.finready.coverage.CoverageClassifier;
import io.finready.coverage.SemanticVerifier;
import io.finready.understanding.AnswerJudge;
import io.finready.understanding.QuestionGenerator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * LLM 포트의 기본 구현. <b>실제 구현체가 없을 때만</b> 등록된다.
 *
 * <p>모델·요금제가 미결정이라(TRD D-02) 아직 진짜 구현체가 없다. 그렇다고 포트를 빈 없이 두면
 * 애플리케이션이 기동조차 못 해서 F03 의 나머지(서비스·컨트롤러·Gate)를 붙일 수 없다.
 * 그래서 <b>기동은 되지만 호출되면 명확히 실패하는</b> 구현을 둔다 — 조용히 빈 결과를
 * 돌려주면 "모든 Risk 가 설명 안 됨"으로 읽혀 Gate 가 잠기고, 원인을 찾기 어렵다.
 *
 * <p>{@link ConditionalOnMissingBean} 이라 진짜 구현체를 {@code @Component} 로 추가하는
 * 순간 이 빈들은 사라진다. 교체할 때 이 파일을 지울 필요가 없다.
 */
@Configuration
public class AiPortConfig {

	@Bean
	@ConditionalOnMissingBean(CoverageClassifier.class)
	CoverageClassifier unconfiguredCoverageClassifier() {
		return (transcript, risks) -> {
			throw unconfigured("Coverage 분류기");
		};
	}

	@Bean
	@ConditionalOnMissingBean(SemanticVerifier.class)
	SemanticVerifier unconfiguredSemanticVerifier() {
		return (transcript, requests) -> {
			throw unconfigured("Evidence Semantic Verifier");
		};
	}

	/**
	 * 여기만 예외적으로 <b>조용히 비어 있는 결과를 돌려준다.</b> 질문 생성 실패는 계약이 정한
	 * 정상 경로이고(검수된 {@code fallbackQuestion} 으로 대체 + {@code source: FALLBACK}),
	 * 호출부가 이미 그 경로를 갖고 있다. 여기서 던지면 LLM 없이 F04 를 돌려볼 수 없다.
	 */
	@Bean
	@ConditionalOnMissingBean(QuestionGenerator.class)
	QuestionGenerator unconfiguredQuestionGenerator() {
		return seeds -> List.of();
	}

	@Bean
	@ConditionalOnMissingBean(AnswerJudge.class)
	AnswerJudge unconfiguredAnswerJudge() {
		return request -> {
			throw unconfigured("답변 판정기");
		};
	}

	/**
	 * 계약 오류가 아니라 배포 설정 누락이다. {@code ApiException} 을 쓰면 "재시도하면 될지도"로
	 * 보이지만 키가 없는 한 몇 번을 눌러도 안 된다. 500 + 스택트레이스로 남겨 로그에서 바로 보이게 한다.
	 */
	private static IllegalStateException unconfigured(String port) {
		return new IllegalStateException(
				port + " 구현체가 없다. LLM 모델이 정해지면 CoverageClassifier / SemanticVerifier "
						+ "구현체를 @Component 로 추가하고 LLM_API_KEY·LLM_MODEL·LLM_BASE_URL 을 설정할 것 "
						+ "(TRD D-02, finready-backend/CLAUDE.md '다음 순서')");
	}
}
