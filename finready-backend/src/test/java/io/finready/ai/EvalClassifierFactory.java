package io.finready.ai;

import io.finready.coverage.CoverageClassifier;
import org.mockito.Mockito;

/**
 * 평가에서 실제 Claude 분류기를 <b>Spring 컨텍스트 없이</b> 만드는 브릿지. 테스트 전용이다.
 *
 * <p>{@code ClaudeCoverageClassifier} 와 {@code AiPortConfig} 의 빈 메서드는
 * package-private 이라 {@code io.finready.evaluation} 에서 닿지 않는다. 같은 패키지에
 * 이 클래스를 두면 <b>운영 코드의 가시성을 평가 때문에 넓히지 않아도 된다</b> —
 * 공개 범위는 필요해서가 아니라 편해서 넓어지기 시작하면 되돌리기 어렵다.
 *
 * <p>{@code @SpringBootTest} 를 쓰지 않는 이유도 있다. {@code evaluate} 태스크는
 * {@code eval} 프로파일로 도는데 해당 설정 파일이 없고, {@code SeedLoader} 가
 * {@code @Profile("!test")} 라 컨텍스트가 DB 를 요구하게 된다. 평가에 필요한 것은
 * 분류기 하나뿐이라 컨텍스트를 띄울 이유가 없다.
 */
public final class EvalClassifierFactory {

	private EvalClassifierFactory() {
	}

	/**
	 * @param apiKey 실제 Anthropic API 키. 호출마다 요금이 든다
	 */
	public static CoverageClassifier claude(String apiKey) {
		AiProperties properties = new AiProperties(
				apiKey,
				"claude-sonnet-4-6",
				"https://api.anthropic.com",
				60,     // application.yaml 과 같은 값. 실측에서 classifier 단독 23.3초가 나왔다
				1,
				0.0);

		// 호출 기록은 DB 로 간다. 평가는 DB 를 쓰지 않으므로 삼키는 대역을 끼운다.
		// null 을 넘겨도 record() 가 NPE 를 잡아 WARN 으로 삼키지만, 호출마다 경고가 찍혀
		// 정작 봐야 할 채점표가 로그에 묻힌다
		LlmCallRecorder recorder = new LlmCallRecorder(Mockito.mock(LlmCallLogRepository.class));

		return new ClaudeCoverageClassifier(new AiGateway(properties, recorder));
	}
}
