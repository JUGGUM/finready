package io.finready.coverage;

import org.springframework.stereotype.Component;

/**
 * classifier 원판정 + provenance + semantic 을 합쳐 최종 {@code coverageStatus} 를 정한다
 * (PRD §7.4 / TRD §8.5, openapi {@code POST /sessions/{id}/coverage} 의 결정표).
 *
 * <p>합성 상태를 <b>저장</b>하지 않는다는 규칙 2와 충돌하지 않는다. 여기서 나온 값은
 * {@code coverage_status} 컬럼에 그대로 들어가고, 원판정은 {@code classifier_status} 에
 * 따로 남는다. 저장하지 않는 건 둘을 또 합친 {@code effectiveStatus} 다.
 *
 * <p>계약 결정표:
 * <pre>
 * provenanceValid | semanticRelation | coverageStatus
 * true            | SUPPORTS         | EXPLAINED
 * true            | CONTRADICTS      | CONTRADICTED
 * true            | INSUFFICIENT     | INSUFFICIENT
 * true            | UNRELATED        | NOT_FOUND
 * false           | (무관), 원판정 EXPLAINED | INSUFFICIENT
 * false           | (무관), 그 외      | 원판정 유지
 * -               | Verifier 미실행    | 원판정 유지
 * </pre>
 */
@Component
public class CoverageStatusResolver {

	/**
	 * @param classifierStatus AI 원판정. 이 메서드는 이 값을 바꾸지 않는다(규칙 1) — 읽기만 한다
	 * @param provenance       서버가 재계산한 근거 검증 결과
	 * @param semanticRelation Verifier 를 돌리지 않았으면 null
	 */
	public CoverageStatus resolve(CoverageStatus classifierStatus,
	                              ProvenanceCheck provenance,
	                              SemanticRelation semanticRelation) {

		CoverageStatus resolved = applyContractTable(classifierStatus, provenance, semanticRelation);

		// 계약 결정표의 "원판정 유지" 행은 EXPLAINED 를 그대로 통과시킬 수 있는데,
		// DB 의 ck_explained_requires_verification 은 EXPLAINED 에 provenance + SUPPORTS 를
		// 예외 없이 요구한다. 둘을 그대로 두면 INSERT 시점에 터지므로 여기서 한 번 더 접는다.
		// 규칙 3은 "애플리케이션에서 우회하지 말 것"이지 "DB가 알아서 막게 두라"가 아니다.
		if (resolved == CoverageStatus.EXPLAINED && !isExplainedProvable(provenance, semanticRelation)) {
			return CoverageStatus.INSUFFICIENT;
		}
		return resolved;
	}

	private CoverageStatus applyContractTable(CoverageStatus classifierStatus,
	                                          ProvenanceCheck provenance,
	                                          SemanticRelation semanticRelation) {
		if (semanticRelation == null) {
			return classifierStatus;                    // Verifier 미실행
		}
		if (!provenance.valid()) {
			return classifierStatus == CoverageStatus.EXPLAINED
					? CoverageStatus.INSUFFICIENT
					: classifierStatus;
		}
		return switch (semanticRelation) {
			case SUPPORTS -> CoverageStatus.EXPLAINED;
			case CONTRADICTS -> CoverageStatus.CONTRADICTED;
			case INSUFFICIENT -> CoverageStatus.INSUFFICIENT;
			case UNRELATED -> CoverageStatus.NOT_FOUND;
		};
	}

	/** EXPLAINED 가 성립하는 유일한 조합 (규칙 3) */
	private boolean isExplainedProvable(ProvenanceCheck provenance, SemanticRelation semanticRelation) {
		return provenance.valid() && semanticRelation == SemanticRelation.SUPPORTS;
	}
}
