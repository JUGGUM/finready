package io.finready.ai;

import io.finready.coverage.CoverageStatus;
import io.finready.understanding.UnderstandingStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * <b>Sonnet 4.6 은 structured outputs 를 지원하지 않는다.</b> 그래서 "enum 밖 값은 파싱
 * 실패"(규칙 9)의 보장이 API 가 아니라 이 코드에 있고, 여기가 그걸 지키는지 보는 자리다.
 *
 * <p>임의 매핑이 왜 위험한지: 모델이 "EXPLAINED_PARTIAL" 같은 값을 만들었을 때 이를
 * INSUFFICIENT 로 "알아서" 매핑하면, 모델이 지어낸 상태가 판정으로 둔갑해 Gate 를 통과시킨다.
 */
@DisplayName("JsonResponses — LLM 응답 파싱")
class JsonResponsesTest {

	@Nested
	@DisplayName("JSON 추출")
	class Extraction {

		@Test
		@DisplayName("순수 JSON 을 읽는다")
		void plainJson() {
			JsonNode node = JsonResponses.parse("{\"status\":\"UNDERSTOOD\"}");
			assertThat(node.get("status").asString()).isEqualTo("UNDERSTOOD");
		}

		@Test
		@DisplayName("코드펜스로 감싸도 읽는다 — 모델이 자주 붙인다")
		void codeFenced() {
			String response = """
					```json
					{"status":"UNDERSTOOD"}
					```
					""";
			assertThat(JsonResponses.parse(response).get("status").asString()).isEqualTo("UNDERSTOOD");
		}

		@Test
		@DisplayName("앞뒤 설명이 붙어도 읽는다")
		void surroundedByProse() {
			String response = "판정 결과입니다.\n{\"status\":\"UNCERTAIN\"}\n이상입니다.";
			assertThat(JsonResponses.parse(response).get("status").asString()).isEqualTo("UNCERTAIN");
		}

		@Test
		@DisplayName("JSON 이 아예 없으면 파싱 실패")
		void noJson() {
			assertThatThrownBy(() -> JsonResponses.parse("죄송합니다. 판정할 수 없습니다."))
					.isInstanceOf(AiGateway.ResponseParseException.class);
		}

		@Test
		@DisplayName("깨진 JSON 은 파싱 실패")
		void brokenJson() {
			assertThatThrownBy(() -> JsonResponses.parse("{\"status\":"))
					.isInstanceOf(AiGateway.ResponseParseException.class);
		}

		@Test
		@DisplayName("null 응답도 파싱 실패로 다룬다")
		void nullResponse() {
			assertThatThrownBy(() -> JsonResponses.parse(null))
					.isInstanceOf(AiGateway.ResponseParseException.class);
		}
	}

	@Nested
	@DisplayName("enum 검증 (규칙 9)")
	class EnumValidation {

		@Test
		@DisplayName("허용된 값은 그대로 읽는다")
		void validEnum() {
			JsonNode node = JsonResponses.parse("{\"status\":\"CONTRADICTED\"}");
			assertThat(JsonResponses.requireEnum(node, "status", CoverageStatus.class))
					.isEqualTo(CoverageStatus.CONTRADICTED);
		}

		@Test
		@DisplayName("앞뒤 공백은 허용한다 — 의미가 달라지지 않는다")
		void trimsWhitespace() {
			JsonNode node = JsonResponses.parse("{\"status\":\"  EXPLAINED  \"}");
			assertThat(JsonResponses.requireEnum(node, "status", CoverageStatus.class))
					.isEqualTo(CoverageStatus.EXPLAINED);
		}

		@Test
		@DisplayName("목록에 없는 값은 비슷해 보여도 파싱 실패다 — 임의 매핑 금지")
		void unknownEnumIsFailure() {
			JsonNode node = JsonResponses.parse("{\"status\":\"EXPLAINED_PARTIAL\"}");

			assertThatThrownBy(() -> JsonResponses.requireEnum(node, "status", CoverageStatus.class))
					.isInstanceOf(AiGateway.ResponseParseException.class)
					.hasMessageContaining("EXPLAINED_PARTIAL");
		}

		@Test
		@DisplayName("소문자도 파싱 실패다 — 계약은 대문자 enum이다")
		void lowercaseIsFailure() {
			JsonNode node = JsonResponses.parse("{\"status\":\"understood\"}");

			assertThatThrownBy(() -> JsonResponses.requireEnum(node, "status", UnderstandingStatus.class))
					.isInstanceOf(AiGateway.ResponseParseException.class);
		}

		@Test
		@DisplayName("필드가 없으면 파싱 실패")
		void missingEnumField() {
			JsonNode node = JsonResponses.parse("{\"reason\":\"…\"}");

			assertThatThrownBy(() -> JsonResponses.requireEnum(node, "status", CoverageStatus.class))
					.isInstanceOf(AiGateway.ResponseParseException.class);
		}
	}

	@Nested
	@DisplayName("필드 접근")
	class Fields {

		@Test
		@DisplayName("requireText 는 빈 문자열을 누락으로 본다 — 빈 값을 기본값으로 채우지 않는다")
		void blankIsMissing() {
			JsonNode node = JsonResponses.parse("{\"riskId\":\"   \"}");

			assertThatThrownBy(() -> JsonResponses.requireText(node, "riskId"))
					.isInstanceOf(AiGateway.ResponseParseException.class);
		}

		@Test
		@DisplayName("optionalText 는 없거나 비면 null")
		void optionalReturnsNull() {
			JsonNode node = JsonResponses.parse("{\"a\":\"  \",\"b\":null}");

			assertThat(JsonResponses.optionalText(node, "a")).isNull();
			assertThat(JsonResponses.optionalText(node, "b")).isNull();
			assertThat(JsonResponses.optionalText(node, "없는필드")).isNull();
		}

		@Test
		@DisplayName("requireArray 는 배열이 아니면 파싱 실패")
		void notAnArray() {
			JsonNode node = JsonResponses.parse("{\"results\":\"배열이 아님\"}");

			assertThatThrownBy(() -> JsonResponses.requireArray(node, "results"))
					.isInstanceOf(AiGateway.ResponseParseException.class);
		}
	}
}
