package io.finready.session;

import java.time.OffsetDateTime;

/**
 * openapi.yml v1.4.2 RevisionResponse. required: [revisionId, revision, text]
 *
 * <p>revisionId 는 DB PK(bigserial), revision 은 세션 안에서의 순번이다. 둘은 다르다 —
 * coverage_result.revision_id 가 참조하는 것은 앞쪽이다.
 */
public record RevisionResponse(
		Long revisionId,
		int revision,
		String text,
		int charCount,
		OffsetDateTime createdAt
) {
	public static RevisionResponse from(ConsultationRevision revision) {
		return new RevisionResponse(
				revision.getId(),
				revision.getRevisionNo(),
				revision.getText(),
				revision.getCharCount(),
				revision.getCreatedAt());
	}
}
