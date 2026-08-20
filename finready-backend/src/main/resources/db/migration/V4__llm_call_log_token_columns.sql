-- FinReady V4__llm_call_log_token_columns.sql
-- 기준: Backend TRD v1.2.3 §7.2(llm_call_log = 평가 재현·성능 실측의 원천) / §14(성능 예산)
--
-- llm_call_log 가 latency_ms 만 갖고 있어 "성능 실측의 원천"으로 쓸 수 없었다.
--
-- Coverage 튜닝 문서의 핵심 인과 주장은 "레이턴시는 출력 토큰에 비례한다"인데,
-- 그 근거가 사람이 서버 INFO 로그에서 손으로 옮겨 적은 두 점뿐이다. 두 점을 지나는
-- 직선은 자유도가 0이라 잔차가 정의되지 않는다 — 적합이 맞는지 틀린지 알 방법이
-- 애초에 없다. 그 직선에서 나온 "호출당 고정 5.9초"가 지금 병렬화 설계의 전제다.
--
-- 토큰 수는 AiGateway#logUsage 가 INFO 로 찍고 흘려보낸다. 로그는 회전하고,
-- session_id 로 묶이지 않으며, 배포 환경에서는 접근 자체가 번거롭다.
-- 같은 이유로 "prompt caching 이 실제로 붙었는가"도 DB 로 답할 수 없다 —
-- 캐시 미달은 오류 없이 조용히 안 걸리므로 cache_read_tokens 가 유일한 신호다.
-- 이건 classifier 를 배치로 쪼갤 때 특히 중요하다. 배치마다 system 프롬프트가
-- 달라지면 캐시 prefix 가 쪼개지는데, 그 사고는 오류도 로그도 테스트 실패도 없이
-- 청구서로만 나타난다.
--
-- effort 를 따로 두는 이유: 레이턴시를 줄이는 레버 중 하나인데 prompt_version 으로
-- 복원되지 않는다(프롬프트 본문의 일부가 아니다). 같은 prompt_version 의 두 행이
-- 서로 다른 effort 로 측정된 값일 수 있고, 그러면 비교가 조용히 오염된다.
--
-- 전부 nullable 이다. 기존 행에는 값이 없고, 응답을 받기 전에 실패한 호출에도 없다.
-- 여기에 0 을 넣으면 "토큰을 0개 썼다"로 읽혀 평균이 오염된다 (AnalysisView 가
-- 멱등 경로에서 0 대신 null 을 쓰는 것과 같은 이유).

alter table llm_call_log
    add column input_tokens       int,
    add column output_tokens      int,
    add column cache_read_tokens  int,
    add column cache_write_tokens int,
    add column effort             varchar(8);

comment on column llm_call_log.output_tokens is
    '레이턴시 회귀의 설명 변수. thinking 토큰을 포함한다 (TRD §14 예산 분석용)';

comment on column llm_call_log.cache_read_tokens is
    'prompt caching 적중 여부의 유일한 신호. 계속 0이면 캐시가 조용히 안 걸리는 것이다';
