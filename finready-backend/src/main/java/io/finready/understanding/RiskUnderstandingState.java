package io.finready.understanding;

import io.finready.common.GenerationSource;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * openapi.yml v1.4.2 {@code RiskUnderstandingState} — {@code GET /sessions/{id}} 의
 * {@code understanding} 배열 항목.
 *
 * <p>Risk 하나의 이해확인 이력 전부다. {@code aiStatus} 는 attempt 별로 남으며
 * {@code staffResolution} 이 있어도 <b>덮이지 않는다</b>(규칙 1).
 *
 * @param pendingQuestion 발급됐으나 아직 답변되지 않은 질문. <b>새로고침 복구의 근거다.</b>
 *                        답변이 저장되면 null 이 된다
 */
public record RiskUnderstandingState(
		String riskId,
		String riskTitle,
		List<AttemptView> attempts,
		StaffResolutionView staffResolution,
		WorkflowStatus workflowStatus,
		FinalDisposition finalDisposition,
		PendingQuestionView pendingQuestion
) {

	public record AttemptView(
			int attempt,
			String question,
			GenerationSource questionSource,
			String answer,
			AnswerSource answerSource,
			UnderstandingStatus aiStatus,
			String reason
	) {

		static AttemptView from(UnderstandingResult result) {
			return new AttemptView(
					result.getAttempt(),
					result.getQuestion(),
					result.getGenerationSource(),
					result.getAnswer(),
					result.getAnswerSource(),
					result.getAiStatus(),
					result.getReason());
		}
	}

	public record StaffResolutionView(
			StaffDisposition disposition,
			String reason,
			String actor,
			OffsetDateTime createdAt
	) {

		static StaffResolutionView from(StaffResolution resolution) {
			if (resolution == null) {
				return null;
			}
			return new StaffResolutionView(
					resolution.getDisposition(),
					resolution.getReason(),
					resolution.getActor(),
					resolution.getCreatedAt());
		}
	}

	/** 계약 required: [attempt, question, source] */
	public record PendingQuestionView(int attempt, String question, GenerationSource source) {

		static PendingQuestionView from(SessionQuestion question) {
			if (question == null) {
				return null;
			}
			return new PendingQuestionView(
					question.getAttempt(), question.getQuestion(), question.getGenerationSource());
		}
	}
}
