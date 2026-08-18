package io.finready.coverage;

import org.springframework.stereotype.Component;

/**
 * {@link CoverageResult} 를 만드는 <b>유일한 정상 경로</b>.
 *
 * <p>엔티티 생성자는 인자 13개를 그대로 받을 뿐 조합을 검사하지 않는다. 조합이 어긋나면
 * DB 의 ck_provenance_consistency / ck_explained_requires_verification 이 INSERT 시점에
 * 터지는데, 그때는 이미 트랜잭션 한복판이라 어느 Risk 때문인지 추적이 번거롭다.
 * 여기서 먼저 막아 <b>만들 수 없게</b> 한다.
 *
 * <p>서비스가 엔티티 생성자를 직접 부르지 않게 하는 것이 이 클래스의 존재 이유다.
 */
@Component
public class CoverageResultFactory {

	private final CoverageStatusResolver statusResolver;

	public CoverageResultFactory(CoverageStatusResolver statusResolver) {
		this.statusResolver = statusResolver;
	}

	/**
	 * coverageStatus 를 인자로 받지 않는다 — 호출부가 정할 수 있게 두면 결정표를 우회하는
	 * 경로가 생긴다. 항상 {@link CoverageStatusResolver} 가 계산한 값만 들어간다.
	 *
	 * @param semanticRelation Verifier 를 돌리지 않은 Risk 는 null
	 */
	public CoverageResult create(String sessionId,
	                             Long revisionId,
	                             String riskId,
	                             CoverageStatus classifierStatus,
	                             String classifierReason,
	                             String verificationReason,
	                             String evidenceText,
	                             ProvenanceCheck provenance,
	                             SemanticRelation semanticRelation) {

		CoverageStatus coverageStatus =
				statusResolver.resolve(classifierStatus, provenance, semanticRelation);

		// resolve 가 접어주므로 정상 흐름에서는 걸리지 않는다. 결정표가 나중에 바뀌었을 때
		// DB 대신 여기서 먼저 터지라고 남겨둔다 — 스택트레이스가 훨씬 읽기 쉽다
		if (coverageStatus == CoverageStatus.EXPLAINED
				&& !(provenance.valid() && semanticRelation == SemanticRelation.SUPPORTS)) {
			throw new IllegalStateException(
					"EXPLAINED 는 provenance + SUPPORTS 를 모두 통과해야 한다 (riskId=%s)".formatted(riskId));
		}

		return new CoverageResult(
				sessionId,
				revisionId,
				riskId,
				classifierStatus,
				coverageStatus,
				classifierReason,
				verificationReason,
				evidenceText,
				provenance.startOffset(),
				provenance.endOffset(),
				provenance.valid(),
				provenance.failureReason(),
				semanticRelation);
	}
}
