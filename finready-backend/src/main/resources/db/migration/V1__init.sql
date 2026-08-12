-- FinReady V1__init.sql
-- 기준: Backend TRD v1.2.3 §4 / PRD v1.3.1 §7.6 Canonical Enum Contract
-- 스키마: finready (Flyway schemas=finready, default-schema=finready)
--
-- enum은 PG enum type 대신 varchar + check로 둔다.
-- 값 추가/변경 시 마이그레이션이 단순하고, JPA @Enumerated(STRING)과 그대로 맞물린다.

-- ============================================================
-- 1. 상품 / Risk 시드
-- ============================================================

create table product (
                         id                   varchar(32)  primary key,
                         name                 varchar(200) not null,
                         archetype            varchar(64)  not null,
                         product_risk_version varchar(64)  not null,
                         document_id          varchar(64)  not null,
                         document_url         varchar(300) not null,
                         document_page_count  int,
                         document_sha256      char(64)     not null,
                         synthetic_notice     text,
                         is_live_demo         boolean      not null default false,
                         created_at           timestamptz  not null default now()
);

comment on column product.document_sha256 is
    'PDF 무결성. 기동 시 실제 파일 해시와 대조하며 불일치 시 기동 중단 (TRD §5.4)';

create table product_risk (
                              id                          bigserial primary key,
                              product_id                  varchar(32) not null references product(id),
                              risk_id                     varchar(8)  not null,
                              category                    varchar(48) not null,
                              title                       varchar(200) not null,
                              fact                        text        not null,
                              coverage_policy             varchar(24) not null,
                              understanding_check         boolean     not null,
                              source_page                 int         not null,
                              source_text                 text        not null,
                              fallback_question           text        not null,
                              fallback_recheck_question   text        not null,
                              fallback_plain_explanation  text        not null,
                              verified_at                 timestamptz not null,
                              verified_by                 varchar(64) not null,
                              constraint uq_product_risk unique (product_id, risk_id),
                              constraint ck_coverage_policy check (
                                  coverage_policy in ('GATE_REQUIRED', 'WARN_ONLY', 'NOT_APPLICABLE')
                                  ),
    -- 최초 질문과 후속 질문이 같으면 "동일 질문 반복 금지"를 지킬 수 없다 (TRD §4.6)
                              constraint ck_recheck_question_differs check (
                                  fallback_recheck_question <> fallback_question
                                  )
);

comment on table product_risk is
    'Verified Risk Schema. 사람이 검수한 값만 들어오며 런타임에 수정되지 않는다';

create table customer_profile (
                                  id                    varchar(32) primary key,
                                  label                 varchar(120) not null,
                                  age_group             varchar(24),
                                  investment_experience varchar(16),
                                  financial_literacy    varchar(16),
                                  explanation_level     varchar(16),
                                  constraint ck_investment_experience check (
                                      investment_experience in ('LOW', 'MEDIUM', 'HIGH')
                                      ),
                                  constraint ck_financial_literacy check (
                                      financial_literacy in ('BASIC', 'NORMAL', 'ADVANCED')
                                      ),
                                  constraint ck_explanation_level check (
                                      explanation_level in ('EASY', 'NORMAL')
                                      )
);

-- ============================================================
-- 2. 세션 / Revision
-- ============================================================

create table consultation_session (
                                      id                   varchar(40)  primary key,
                                      product_id           varchar(32)  not null references product(id),
                                      customer_id          varchar(32)  not null references customer_profile(id),
                                      status               varchar(40)  not null,
                                      product_risk_version varchar(64)  not null,
                                      version              bigint       not null default 0,
                                      unresolved_reason    varchar(500),
                                      closed_by            varchar(64),
                                      closed_at            timestamptz,
                                      created_at           timestamptz  not null default now(),
                                      constraint ck_session_status check (
                                          status in ('DRAFT', 'COVERAGE_ANALYZED', 'GATE_BLOCKED',
                                                     'UNDERSTANDING_IN_PROGRESS', 'AWAITING_STAFF_REVIEW',
                                                     'SESSION_CLOSED_BY_STAFF', 'SESSION_CLOSED_WITH_UNRESOLVED')
                                          )
);

comment on column consultation_session.version is
    '@Version 낙관적 락. 더블 클릭으로 attempt가 2씩 오르는 사고를 막는다 (TRD §5.3)';
comment on column consultation_session.product_risk_version is
    '생성 시점 snapshot. 시드가 바뀌어도 진행 중 세션의 판정 기준은 고정된다';

create table consultation_revision (
                                       id          bigserial   primary key,
                                       session_id  varchar(40) not null references consultation_session(id),
                                       revision_no int         not null,
                                       text        text        not null,
                                       char_count  int         not null,
                                       created_at  timestamptz not null default now(),
                                       constraint uq_revision unique (session_id, revision_no),
                                       constraint ck_char_count check (char_count between 1 and 8000)
);

comment on table consultation_revision is
    'immutable. 보완 설명도 전체 텍스트로 새 행을 만든다. evidence의 snapshot 근거 (TRD §5.2)';

-- ============================================================
-- 3. Coverage
-- ============================================================

create table coverage_result (
                                 id                        bigserial   primary key,
                                 session_id                varchar(40) not null references consultation_session(id),
                                 revision_id               bigint      not null references consultation_revision(id),
                                 risk_id                   varchar(8)  not null,
                                 classifier_status         varchar(20) not null,
                                 coverage_status           varchar(20) not null,
                                 classifier_reason         text,
                                 verification_reason       text,
                                 evidence_text             text,
                                 evidence_start            int,
                                 evidence_end              int,
                                 provenance_valid          boolean     not null default false,
                                 provenance_failure_reason varchar(20),
                                 semantic_relation         varchar(20),
                                 created_at                timestamptz not null default now(),
                                 constraint uq_coverage unique (revision_id, risk_id),
                                 constraint ck_classifier_status check (
                                     classifier_status in ('EXPLAINED', 'INSUFFICIENT', 'NOT_FOUND', 'CONTRADICTED')
                                     ),
                                 constraint ck_coverage_status check (
                                     coverage_status in ('EXPLAINED', 'INSUFFICIENT', 'NOT_FOUND', 'CONTRADICTED')
                                     ),
                                 constraint ck_provenance_failure_reason check (
                                     provenance_failure_reason is null or provenance_failure_reason in
                                                                          ('EMPTY', 'TOO_SHORT', 'TOO_LONG', 'NOT_FOUND', 'AMBIGUOUS')
                                     ),
                                 constraint ck_semantic_relation check (
                                     semantic_relation is null or semantic_relation in
                                                                  ('SUPPORTS', 'CONTRADICTS', 'INSUFFICIENT', 'UNRELATED')
                                     ),
    -- provenance가 유효하면 offset이 반드시 있고, 실패하면 사유가 반드시 있다
                                 constraint ck_provenance_consistency check (
                                     (provenance_valid = true
                                         and evidence_start is not null
                                         and evidence_end is not null
                                         and provenance_failure_reason is null)
                                         or
                                     (provenance_valid = false)
                                     ),
    -- EXPLAINED는 provenance + semantic을 모두 통과해야만 성립한다 (TRD §8.5)
                                 constraint ck_explained_requires_verification check (
                                     coverage_status <> 'EXPLAINED'
                                         or (provenance_valid = true and semantic_relation = 'SUPPORTS')
                                     )
);

comment on column coverage_result.classifier_status is
    'AI 원판정. 어떤 경로로도 UPDATE되지 않는다 (TRD §4.2)';
comment on column coverage_result.coverage_status is
    'provenance + semantic 검증 후 확정값. effectiveStatus 같은 합성 상태는 저장하지 않는다';

create index ix_coverage_session_revision on coverage_result (session_id, revision_id);

create table gate_override (
                               id                          bigserial   primary key,
                               session_id                  varchar(40) not null references consultation_session(id),
                               risk_id                     varchar(8)  not null,
                               category                    varchar(40) not null,
                               reason                      varchar(500) not null,
                               staff_explanation_confirmed boolean,
                               actor                       varchar(64) not null,
                               created_at                  timestamptz not null default now(),
                               constraint uq_gate_override unique (session_id, risk_id),
                               constraint ck_override_category check (
                                   category in ('AI_MISCLASSIFICATION', 'EXPLANATION_OUTSIDE_TRANSCRIPT',
                                                'OPERATIONAL_EXCEPTION', 'OTHER')
                                   ),
                               constraint ck_override_reason_len check (char_length(reason) >= 5)
);

-- ============================================================
-- 4. Understanding
-- ============================================================

-- 발급된 질문의 단일 진실 (TRD §4.6).
-- attempt 1 = POST /questions, attempt 2 = /understanding(UNCERTAIN) 또는 /reexplain(MISUNDERSTOOD)
create table session_question (
                                  id                bigserial   primary key,
                                  session_id        varchar(40) not null references consultation_session(id),
                                  risk_id           varchar(8)  not null,
                                  attempt           smallint    not null,
                                  question          text        not null,
                                  generation_source varchar(16) not null,
                                  created_at        timestamptz not null default now(),
                                  constraint uq_session_question unique (session_id, risk_id, attempt),
                                  constraint ck_question_attempt check (attempt between 1 and 2),
                                  constraint ck_question_source check (generation_source in ('LLM', 'FALLBACK'))
);

comment on table session_question is
    '멱등 발급. 새로고침·뒤로가기로 문구가 바뀌지 않으며 attempt 2는 attempt 1과 반드시 다르다';

create table understanding_result (
                                      id                bigserial   primary key,
                                      session_id        varchar(40) not null references consultation_session(id),
                                      risk_id           varchar(8)  not null,
                                      attempt           smallint    not null,
                                      question          text        not null,
                                      generation_source varchar(16) not null,
                                      answer            text        not null,
                                      answer_source     varchar(32) not null,
                                      ai_status         varchar(20) not null,
                                      reason            text,
                                      created_at        timestamptz not null default now(),
                                      constraint uq_understanding unique (session_id, risk_id, attempt),
    -- 애플리케이션과 DB 양쪽에서 막는다 (TRD §4.2)
                                      constraint ck_understanding_attempt check (attempt between 1 and 2),
                                      constraint ck_ai_status check (
                                          ai_status in ('UNDERSTOOD', 'MISUNDERSTOOD', 'UNCERTAIN')
                                          ),
                                      constraint ck_answer_source check (
                                          answer_source in ('CUSTOMER_DIRECT_DEMO', 'STAFF_PROXY_VERBAL')
                                          ),
                                      constraint ck_generation_source check (generation_source in ('LLM', 'FALLBACK')),
    -- MISUNDERSTOOD/UNCERTAIN은 사유가 필수다 (PRD §9 F05)
                                      constraint ck_reason_required check (
                                          ai_status = 'UNDERSTOOD' or (reason is not null and char_length(reason) > 0)
                                          )
);

comment on column understanding_result.ai_status is
    'AI 최초 판정. Staff Resolution이 이 값을 덮어쓰지 않는다';
comment on column understanding_result.question is
    'session_question의 복사본. 감사 목적이며 원천은 session_question이다';

create index ix_understanding_session_risk on understanding_result (session_id, risk_id);

create table risk_workflow_state (
                                     id                bigserial   primary key,
                                     session_id        varchar(40) not null references consultation_session(id),
                                     risk_id           varchar(8)  not null,
                                     workflow_status   varchar(32) not null,
                                     final_disposition varchar(32),
                                     updated_at        timestamptz not null default now(),
                                     constraint uq_workflow_state unique (session_id, risk_id),
                                     constraint ck_workflow_status check (
                                         workflow_status in ('NOT_STARTED', 'IN_PROGRESS',
                                                             'MANUAL_REVIEW_REQUIRED', 'COMPLETE')
                                         ),
                                     constraint ck_final_disposition check (
                                         final_disposition is null or final_disposition in
                                                                      ('AUTO_RESOLVED', 'RESOLVED_BY_STAFF', 'UNRESOLVED', 'SKIPPED_BY_OVERRIDE')
                                         ),
    -- finalDisposition은 COMPLETE일 때만 non-null (TRD §6.3)
                                     constraint ck_disposition_only_when_complete check (
                                         (workflow_status = 'COMPLETE' and final_disposition is not null)
                                             or
                                         (workflow_status <> 'COMPLETE' and final_disposition is null)
                                         )
);

create table staff_resolution (
                                  id          bigserial   primary key,
                                  session_id  varchar(40) not null references consultation_session(id),
                                  risk_id     varchar(8)  not null,
                                  disposition varchar(32) not null,
                                  reason      varchar(500) not null,
                                  actor       varchar(64) not null,
                                  created_at  timestamptz not null default now(),
                                  constraint uq_staff_resolution unique (session_id, risk_id),
                                  constraint ck_staff_disposition check (
                                      disposition in ('RESOLVED_BY_STAFF', 'UNRESOLVED')
                                      ),
                                  constraint ck_resolution_reason_len check (char_length(reason) >= 5)
);

create table re_explanation (
                                id                bigserial   primary key,
                                session_id        varchar(40) not null references consultation_session(id),
                                risk_id           varchar(8)  not null,
                                source_page       int         not null,
                                source_text       text        not null,
                                explanation       text        not null,
                                generation_source varchar(16) not null,
                                guardrail_retried boolean     not null default false,
                                guardrail_violations text,
                                created_at        timestamptz not null default now(),
                                constraint ck_reexplain_source check (generation_source in ('LLM', 'FALLBACK'))
);

-- ============================================================
-- 5. 감사 / 관측
-- ============================================================

create table audit_event (
                             id              bigserial   primary key,
                             session_id      varchar(40) not null references consultation_session(id),
                             event_type      varchar(48) not null,
                             actor           varchar(64) not null,
                             actor_role      varchar(16) not null,
                             payload_summary text,
                             created_at      timestamptz not null default now(),
                             constraint ck_actor_role check (
                                 actor_role in ('STAFF', 'CUSTOMER', 'SYSTEM', 'AI')
                                 )
);

comment on table audit_event is
    'append-only. UPDATE/DELETE/TRUNCATE는 V2 트리거가 차단한다 (TRD §4.4)';

create index ix_audit_session_created on audit_event (session_id, created_at);

create table llm_call_log (
                              id              bigserial   primary key,
                              session_id      varchar(40) references consultation_session(id),
                              stage           varchar(32) not null,
                              prompt_version  varchar(32) not null,
                              model           varchar(64) not null,
                              request_summary text,
                              raw_response    text,
                              parsed_ok       boolean     not null,
                              latency_ms      int,
                              attempt         smallint    not null default 1,
                              created_at      timestamptz not null default now()
);

comment on table llm_call_log is
    '평가 재현과 성능 실측의 원천. prompt_version이 Hold-out 재현 조건이다 (TRD §7.2)';

create index ix_llm_call_session_stage on llm_call_log (session_id, stage);