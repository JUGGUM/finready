package io.finready.coverage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * openapi {@code POST /sessions/{id}/coverage} 의 결정표를 그대로 고정한다.
 *
 * <p>이 표는 화면 표시와 Gate 판정 양쪽의 입력이라, 한 칸만 어긋나도 "설명했는데 막힘" /
 * "안 했는데 통과" 가 된다.
 */
@DisplayName("CoverageStatusResolver — 결정표")
class CoverageStatusResolverTest {

	private final CoverageStatusResolver resolver = new CoverageStatusResolver();

	private static final ProvenanceCheck VALID = ProvenanceCheck.found(0, 20);
	private static final ProvenanceCheck INVALID =
			ProvenanceCheck.failed(ProvenanceFailureReason.NOT_FOUND);

	@Nested
	@DisplayName("provenance 통과")
	class ProvenanceValid {

		@Test
		@DisplayName("SUPPORTS → EXPLAINED")
		void supports() {
			assertThat(resolver.resolve(CoverageStatus.EXPLAINED, VALID, SemanticRelation.SUPPORTS))
					.isEqualTo(CoverageStatus.EXPLAINED);
		}

		@Test
		@DisplayName("CONTRADICTS → CONTRADICTED")
		void contradicts() {
			assertThat(resolver.resolve(CoverageStatus.EXPLAINED, VALID, SemanticRelation.CONTRADICTS))
					.isEqualTo(CoverageStatus.CONTRADICTED);
		}

		@Test
		@DisplayName("INSUFFICIENT → INSUFFICIENT")
		void insufficient() {
			assertThat(resolver.resolve(CoverageStatus.EXPLAINED, VALID, SemanticRelation.INSUFFICIENT))
					.isEqualTo(CoverageStatus.INSUFFICIENT);
		}

		@Test
		@DisplayName("UNRELATED → NOT_FOUND")
		void unrelated() {
			assertThat(resolver.resolve(CoverageStatus.EXPLAINED, VALID, SemanticRelation.UNRELATED))
					.isEqualTo(CoverageStatus.NOT_FOUND);
		}

		@Test
		@DisplayName("semantic 이 원판정을 이긴다 — 원판정이 NOT_FOUND 여도 SUPPORTS 면 EXPLAINED")
		void semanticOverridesClassifier() {
			assertThat(resolver.resolve(CoverageStatus.NOT_FOUND, VALID, SemanticRelation.SUPPORTS))
					.isEqualTo(CoverageStatus.EXPLAINED);
		}
	}

	@Nested
	@DisplayName("provenance 실패")
	class ProvenanceInvalid {

		@Test
		@DisplayName("원판정이 EXPLAINED 였으면 INSUFFICIENT 로 내린다")
		void explainedIsDowngraded() {
			assertThat(resolver.resolve(CoverageStatus.EXPLAINED, INVALID, SemanticRelation.SUPPORTS))
					.isEqualTo(CoverageStatus.INSUFFICIENT);
		}

		@ParameterizedTest
		@EnumSource(value = CoverageStatus.class, names = {"INSUFFICIENT", "NOT_FOUND", "CONTRADICTED"})
		@DisplayName("그 외 원판정은 그대로 유지된다")
		void otherStatusesAreKept(CoverageStatus classifierStatus) {
			assertThat(resolver.resolve(classifierStatus, INVALID, SemanticRelation.SUPPORTS))
					.isEqualTo(classifierStatus);
		}
	}

	@Nested
	@DisplayName("Verifier 미실행 (semanticRelation = null)")
	class VerifierNotRun {

		@ParameterizedTest
		@EnumSource(value = CoverageStatus.class, names = {"INSUFFICIENT", "NOT_FOUND", "CONTRADICTED"})
		@DisplayName("원판정을 유지한다")
		void keepsClassifierStatus(CoverageStatus classifierStatus) {
			assertThat(resolver.resolve(classifierStatus, VALID, null)).isEqualTo(classifierStatus);
		}

		/**
		 * 계약 결정표만 그대로 읽으면 "원판정 유지"라 EXPLAINED 가 나오는데, DB 의
		 * ck_explained_requires_verification 은 semantic_relation='SUPPORTS' 를 예외 없이
		 * 요구한다. 계약과 DB 가 어긋나는 지점이며, 여기서는 DB(규칙 3)를 따른다.
		 */
		@Test
		@DisplayName("EXPLAINED 는 유지되지 않고 INSUFFICIENT 로 접힌다 — 규칙 3")
		void explainedCannotSurviveWithoutVerifier() {
			assertThat(resolver.resolve(CoverageStatus.EXPLAINED, VALID, null))
					.isEqualTo(CoverageStatus.INSUFFICIENT);
		}
	}

	@Test
	@DisplayName("EXPLAINED 는 provenance + SUPPORTS 조합에서만 나온다")
	void explainedOnlyFromProvableCombination() {
		for (CoverageStatus classifier : CoverageStatus.values()) {
			for (ProvenanceCheck provenance : new ProvenanceCheck[]{VALID, INVALID}) {
				for (SemanticRelation relation : allRelationsIncludingNull()) {
					CoverageStatus resolved = resolver.resolve(classifier, provenance, relation);
					if (resolved == CoverageStatus.EXPLAINED) {
						assertThat(provenance.valid()).isTrue();
						assertThat(relation).isEqualTo(SemanticRelation.SUPPORTS);
					}
				}
			}
		}
	}

	private SemanticRelation[] allRelationsIncludingNull() {
		SemanticRelation[] values = SemanticRelation.values();
		SemanticRelation[] withNull = new SemanticRelation[values.length + 1];
		System.arraycopy(values, 0, withNull, 0, values.length);
		return withNull;
	}
}
