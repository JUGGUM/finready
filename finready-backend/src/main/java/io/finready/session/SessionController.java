package io.finready.session;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** F01 세션 생성 / F02 Revision 저장 / 세션 복구 */
@RestController
@RequestMapping("/api/sessions")
public class SessionController {

	private final SessionService sessionService;

	public SessionController(SessionService sessionService) {
		this.sessionService = sessionService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public SessionResponse createSession(@RequestBody CreateSessionRequest request) {
		return sessionService.createSession(request);
	}

	@GetMapping("/{sessionId}")
	public SessionSnapshotResponse getSession(@PathVariable String sessionId) {
		return sessionService.getSnapshot(sessionId);
	}

	/** 동일 텍스트 재전송이면 기존 revision 을 돌려주지만, 계약이 201 만 정의하므로 상태는 그대로다 */
	@PostMapping("/{sessionId}/revisions")
	@ResponseStatus(HttpStatus.CREATED)
	public RevisionResponse createRevision(@PathVariable String sessionId,
	                                       @RequestBody CreateRevisionRequest request) {
		return sessionService.createRevision(sessionId, request);
	}
}
