package io.finready.common;

/**
 * session_question · understanding_result · re_explanation 의 generation_source — TRD §6.
 * ck_question_source · ck_generation_source · ck_reexplain_source 가 모두 같은 두 값을 쓴다.
 *
 * <p>understanding 과 explanation 두 패키지가 함께 쓰므로 common 에 둔다.
 * 패키지마다 같은 enum 을 복제하면 TRD §6 값이 갈라진다.
 */
public enum GenerationSource {
	LLM,
	FALLBACK
}
