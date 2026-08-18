package io.finready.ai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 여기서 지키려는 것은 <b>API 키가 문자열로 새지 않는 것</b>이다 (규칙 10).
 *
 * <p>record 기본 {@code toString} 은 모든 필드를 찍는다. Spring 은 바인딩 실패 시 대상
 * 객체를 오류 메시지에 넣으므로, 재정의하지 않으면 설정 오타 한 번에 키가 로그로 나간다.
 */
@DisplayName("AiProperties")
class AiPropertiesTest {

	private static final String SECRET = "sk-ant-super-secret-value";

	@Test
	@DisplayName("toString 에 API 키가 나오지 않는다")
	void toStringMasksApiKey() {
		AiProperties properties = configured();

		assertThat(properties.toString())
				.doesNotContain(SECRET)
				.contains("***")
				.contains("claude-sonnet-4-6");
	}

	@Test
	@DisplayName("키가 없으면 미설정으로 표시된다")
	void toStringShowsUnconfigured() {
		AiProperties properties = new AiProperties(
				"", "claude-sonnet-4-6", "https://api.anthropic.com", 30, 1, 0.0);

		assertThat(properties.toString()).contains("(미설정)");
	}

	@Test
	@DisplayName("키나 모델이 비면 미설정이다 — 스텁이 등록되는 조건")
	void isConfiguredRequiresKeyAndModel() {
		assertThat(configured().isConfigured()).isTrue();

		assertThat(new AiProperties(null, "claude-sonnet-4-6", "u", 30, 1, 0.0)
				.isConfigured()).isFalse();
		assertThat(new AiProperties("  ", "claude-sonnet-4-6", "u", 30, 1, 0.0)
				.isConfigured()).isFalse();
		assertThat(new AiProperties(SECRET, "", "u", 30, 1, 0.0)
				.isConfigured()).isFalse();
	}

	@Test
	@DisplayName("timeout 은 Duration 으로 꺼낸다")
	void timeoutAsDuration() {
		assertThat(configured().timeout()).isEqualTo(Duration.ofSeconds(30));
	}

	private AiProperties configured() {
		return new AiProperties(SECRET, "claude-sonnet-4-6", "https://api.anthropic.com", 30, 1, 0.0);
	}
}
