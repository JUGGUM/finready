package io.finready.understanding;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** F04 이해확인 질문 발급 / F05 답변 판정 */
@RestController
@RequestMapping("/api/sessions")
public class UnderstandingController {

	private final UnderstandingService understandingService;

	public UnderstandingController(UnderstandingService understandingService) {
		this.understandingService = understandingService;
	}

	/** 멱등 — 이미 발급된 질문이 있으면 그대로 반환한다 (TRD §4.6) */
	@PostMapping("/{sessionId}/questions")
	public QuestionsResponse getOrCreateQuestions(@PathVariable String sessionId) {
		return understandingService.getOrCreateQuestions(sessionId);
	}

	/**
	 * attempt 1. 후속 확인은 {@code /recheck} 이며, <b>attempt 는 경로가 정한다</b> —
	 * 클라이언트가 보내지 않으므로 2를 1로 바꿔 재시도할 수 없다.
	 */
	@PostMapping("/{sessionId}/understanding")
	public UnderstandingResponse submitAnswer(@PathVariable String sessionId,
	                                          @RequestBody SubmitAnswerRequest request) {
		return understandingService.submitAnswer(sessionId, request);
	}
}
