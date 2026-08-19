package io.finready.session;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ConsultationRevisionRepository extends JpaRepository<ConsultationRevision, Long> {

	/** 직전 revision. 번호 채번과 "동일 텍스트면 새로 만들지 않는다" 판정에 쓴다 (TRD §5.2) */
	Optional<ConsultationRevision> findTopBySessionIdOrderByRevisionNoDesc(String sessionId);

	/** F08 리포트 — 보완 설명 이력 전체를 순서대로 싣는다 */
	List<ConsultationRevision> findBySessionIdOrderByRevisionNoAsc(String sessionId);
}
