package io.finready.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * V1__init.sql / audit_event — append-only (TRD §4.4).
 * V2 트리거가 UPDATE / DELETE / TRUNCATE 를 차단하며, 어기면 런타임에
 * {@code [23001] append-only table: audit_event} 로 터진다.
 *
 * <p>@Immutable 은 UPDATE 만 막으므로 트리거와 겹치지 않고 서로를 보완한다.
 * 리포지토리에 UPDATE·DELETE 메서드를 만들지 말 것(CLAUDE.md 규칙 1).
 *
 * <p>event_type 은 DDL 에 check 제약이 없어 문자열로 둔다. 값 목록을 enum 으로 올리려면
 * 마이그레이션으로 check 를 먼저 추가해야 코드와 DB 가 같이 강제된다.
 */
@Entity
@Table(name = "audit_event")
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditEvent {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id", nullable = false)
	private Long id;

	@Column(name = "session_id", length = 40, nullable = false)
	private String sessionId;

	@Column(name = "event_type", length = 48, nullable = false)
	private String eventType;

	@Column(name = "actor", length = 64, nullable = false)
	private String actor;

	@Enumerated(EnumType.STRING)
	@Column(name = "actor_role", length = 16, nullable = false)
	private ActorRole actorRole;

	/** 요약만 남긴다. API 키·자격증명이 흘러들지 않게 기록 시점에서 걸러야 한다 (규칙 10) */
	@Column(name = "payload_summary", columnDefinition = "text")
	private String payloadSummary;

	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt;

	public AuditEvent(String sessionId,
	                  String eventType,
	                  String actor,
	                  ActorRole actorRole,
	                  String payloadSummary) {
		this.sessionId = sessionId;
		this.eventType = eventType;
		this.actor = actor;
		this.actorRole = actorRole;
		this.payloadSummary = payloadSummary;
		this.createdAt = OffsetDateTime.now();
	}
}
