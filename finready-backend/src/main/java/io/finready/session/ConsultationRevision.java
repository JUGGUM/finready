package io.finready.session;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.time.OffsetDateTime;

/**
 * V1__init.sql / consultation_revision — immutable (TRD §5.2).
 * 보완 설명도 전체 텍스트로 새 행을 만든다. evidence 의 snapshot 근거다.
 *
 * <p>@Immutable 로 UPDATE 경로를 Hibernate 수준에서 막는다.
 */
@Entity
@Table(name = "consultation_revision")
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConsultationRevision {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id", nullable = false)
	private Long id;

	@Column(name = "session_id", length = 40, nullable = false)
	private String sessionId;

	@Column(name = "revision_no", nullable = false)
	private int revisionNo;

	@Column(name = "text", nullable = false, columnDefinition = "text")
	private String text;

	@Column(name = "char_count", nullable = false)
	private int charCount;

	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt;

	/**
	 * charCount 는 인자로 받지 않고 text 에서 계산한다. 둘이 어긋나면
	 * ck_char_count 로 기동이 아니라 INSERT 가 실패하므로 애초에 어긋날 수 없게 둔다.
	 * String.length() 는 UTF-16 code unit 이며, evidence offset 기준과 같다 (TRD §8).
	 */
	public ConsultationRevision(String sessionId, int revisionNo, String text) {
		this.sessionId = sessionId;
		this.revisionNo = revisionNo;
		this.text = text;
		this.charCount = text.length();
		this.createdAt = OffsetDateTime.now();
	}
}
