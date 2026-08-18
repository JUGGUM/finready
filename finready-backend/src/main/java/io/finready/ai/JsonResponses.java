package io.finready.ai;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * LLM 응답 텍스트에서 JSON 을 꺼낸다.
 *
 * <p><b>Sonnet 4.6 은 structured outputs 를 지원하지 않는다</b>(지원: Fable 5 / Opus 5 /
 * Opus 4.8 / Sonnet 5 / Haiku 4.5). 그래서 스키마를 API 에 위임할 수 없고, 프롬프트로
 * 유도한 뒤 여기서 방어적으로 파싱한다 — 규칙 9("enum 밖 값은 파싱 실패, 임의 매핑 금지")의
 * 보장이 API 가 아니라 이 코드에 있다는 뜻이다.
 *
 * <p>모델을 structured outputs 지원 모델로 올리면 이 클래스의 관대한 부분(코드펜스 벗기기)은
 * 필요 없어지지만, enum 검증은 그대로 둘 것 — 지원 모델이라도 응답이 스키마를 벗어나는
 * 경로가 완전히 없지는 않다.
 */
public final class JsonResponses {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private JsonResponses() {
	}

	/**
	 * 응답 텍스트를 JSON 으로 읽는다. 모델이 ```json 펜스나 앞뒤 설명을 붙이는 일이 잦아
	 * 첫 {@code {} 부터 마지막 {@code }} 까지만 잘라 쓴다.
	 */
	public static JsonNode parse(String responseText) {
		String json = extractJsonObject(responseText);
		try {
			return MAPPER.readTree(json);
		} catch (RuntimeException ex) {
			throw new AiGateway.ResponseParseException("JSON 으로 읽을 수 없다: " + ex.getMessage());
		}
	}

	/** 필수 문자열. 없거나 비면 파싱 실패다 — 빈 값을 기본값으로 채우지 않는다 */
	public static String requireText(JsonNode node, String field) {
		JsonNode value = node.get(field);
		if (value == null || value.isNull() || value.asString().isBlank()) {
			throw new AiGateway.ResponseParseException("필수 필드 누락: " + field);
		}
		return value.asString();
	}

	/** 선택 문자열. 없으면 null */
	public static String optionalText(JsonNode node, String field) {
		JsonNode value = node.get(field);
		if (value == null || value.isNull()) {
			return null;
		}
		String text = value.asString();
		return text.isBlank() ? null : text;
	}

	/**
	 * enum 값. <b>목록에 없는 문자열은 파싱 실패다</b>(규칙 9) — 비슷한 값으로 매핑하거나
	 * 기본값으로 떨어뜨리지 않는다. 그렇게 하면 모델이 만들어낸 값이 판정으로 둔갑한다.
	 */
	public static <E extends Enum<E>> E requireEnum(JsonNode node, String field, Class<E> type) {
		String raw = requireText(node, field);
		try {
			return Enum.valueOf(type, raw.trim());
		} catch (IllegalArgumentException ex) {
			throw new AiGateway.ResponseParseException(
					"%s 가 허용된 값이 아니다: %s".formatted(field, raw));
		}
	}

	public static JsonNode requireArray(JsonNode node, String field) {
		JsonNode value = node.get(field);
		if (value == null || !value.isArray()) {
			throw new AiGateway.ResponseParseException("배열 필드 누락: " + field);
		}
		return value;
	}

	private static String extractJsonObject(String responseText) {
		if (responseText == null) {
			throw new AiGateway.ResponseParseException("응답이 비어 있다");
		}
		int start = responseText.indexOf('{');
		int end = responseText.lastIndexOf('}');
		if (start < 0 || end <= start) {
			throw new AiGateway.ResponseParseException("응답에서 JSON 객체를 찾지 못했다");
		}
		return responseText.substring(start, end + 1);
	}
}
