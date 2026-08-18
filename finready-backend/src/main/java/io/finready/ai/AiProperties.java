package io.finready.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * {@code finready.ai.*} 바인딩 (TRD §7).
 *
 * <p><b>{@code apiKey} 는 비어 있어도 기동한다.</b> 필수로 두면 키 없이는 애플리케이션이
 * 아예 안 떠서, LLM 이 필요 없는 화면(F01·F02·리포트)까지 못 쓰게 된다. 대신 키가 없으면
 * {@link AiPortConfig} 가 실패하는 스텁을 등록하므로, 호출 시점에 명확히 터진다.
 *
 * @param model          모델 ID. 2026-08-18 기준 {@code claude-sonnet-4-6}
 * @param maxRetries     1회만. 무한 재시도 금지 (TRD §7.1)
 * @param temperature    Sonnet 4.6 은 sampling 파라미터를 받는다. Opus 4.7+ / Sonnet 5 는
 *                       400 이므로 모델을 올릴 때 함께 지워야 한다
 */
@ConfigurationProperties(prefix = "finready.ai")
public record AiProperties(
		String apiKey,
		String model,
		String baseUrl,
		int timeoutSeconds,
		int maxRetries,
		Double temperature
) {

	public boolean isConfigured() {
		return apiKey != null && !apiKey.isBlank()
				&& model != null && !model.isBlank();
	}

	public Duration timeout() {
		return Duration.ofSeconds(timeoutSeconds);
	}

	/**
	 * <b>apiKey 를 문자열로 노출하지 않는다</b>(규칙 10). record 기본 {@code toString} 은
	 * 모든 필드를 찍으므로, 이 객체가 로그·오류 메시지에 한 번이라도 들어가면 키가 그대로 샌다.
	 * Spring 은 바인딩 실패 시 대상 객체를 메시지에 넣는다 — 실제로 일어나는 경로다.
	 */
	@Override
	public String toString() {
		return "AiProperties[model=%s, baseUrl=%s, timeoutSeconds=%d, maxRetries=%d, temperature=%s, apiKey=%s]"
				.formatted(model, baseUrl, timeoutSeconds, maxRetries, temperature,
						isConfigured() ? "***" : "(미설정)");
	}
}
