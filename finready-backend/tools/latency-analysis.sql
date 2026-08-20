-- TRD §18 Step 5 — S1+S2 레이턴시 분석 질의
--
-- run-coverage-eval.ps1 실행 뒤 이 파일의 질의를 순서대로 돌린다.
-- 스크립트가 마지막에 찍는 session_id 목록을 :sessions 자리에 넣는다.
--
-- 이 질의들의 목적은 "빨라졌다"가 아니라 **왜 빨라졌는가**를 귀속시키는 것이다.
-- 레이턴시만 보면 실행 간 편차(같은 프롬프트로 2.4배)에 묻힌다.

-- ---------------------------------------------------------------------------
-- 1. 단계별 기본 통계
--
-- attempt 로 필터하지 않고 parsed_ok 로 거른다. max-retries=1 이라 한 호출이
-- 두 행을 남길 수 있고, 그냥 합치면 실패한 시도의 시간까지 더해진다.
-- ---------------------------------------------------------------------------
select stage,
       count(*)                                        as calls,
       min(latency_ms)                                 as min_ms,
       percentile_cont(0.5) within group (order by latency_ms)::int as median_ms,
       max(latency_ms)                                 as max_ms,
       min(output_tokens)                              as min_out,
       max(output_tokens)                              as max_out
from finready.llm_call_log
where parsed_ok
  and stage in ('COVERAGE_CLASSIFY', 'SEMANTIC_VERIFY')
  and session_id in (:sessions)
group by stage;

-- ---------------------------------------------------------------------------
-- 2. 세션별 S1+S2 합계 — §14 의 판정 단위
--
-- 12000 을 넘는 행이 하나라도 있으면 예산 미충족이다. 예산은 평균이 아니라 천장이다.
-- ---------------------------------------------------------------------------
select session_id,
       max(latency_ms) filter (where stage = 'COVERAGE_CLASSIFY') as s1_ms,
       max(latency_ms) filter (where stage = 'SEMANTIC_VERIFY')   as s2_ms,
       coalesce(max(latency_ms) filter (where stage = 'COVERAGE_CLASSIFY'), 0)
     + coalesce(max(latency_ms) filter (where stage = 'SEMANTIC_VERIFY'), 0)   as combined_ms
from finready.llm_call_log
where parsed_ok
  and stage in ('COVERAGE_CLASSIFY', 'SEMANTIC_VERIFY')
  and session_id in (:sessions)
group by session_id
order by combined_ms desc;

-- ---------------------------------------------------------------------------
-- 3. 회귀: latency_ms ~ output_tokens          ★ 이 단계의 진짜 산출물
--
-- 지금까지 이 관계의 근거는 사람이 로그에서 옮겨 적은 **두 점**뿐이었고,
-- 거기서 나온 "호출당 고정 5.9초"가 병렬화 설계의 전제였다. 두 점을 지나는
-- 직선은 자유도가 0이라 맞는지 틀린지 판정할 수단 자체가 없다.
--
-- intercept 가 작으면(~1초) 병렬화만으로 12초가 사정권이다.
-- intercept 가 크면(~6초) 직렬 2호출의 바닥이 ~12초라 모델 교체까지 가야 한다.
-- 이 숫자가 나오기 전에 사다리를 오르지 않는다.
--
-- regr_count 가 30 미만이면 결론을 내지 말 것.
-- ---------------------------------------------------------------------------
select stage,
       regr_count(latency_ms, output_tokens)          as n,
       round(regr_intercept(latency_ms, output_tokens))          as intercept_ms,
       round(regr_slope(latency_ms, output_tokens)::numeric, 3)  as ms_per_token,
       round(regr_r2(latency_ms, output_tokens)::numeric, 3)     as r2
from finready.llm_call_log
where parsed_ok
  and output_tokens is not null
  and stage in ('COVERAGE_CLASSIFY', 'SEMANTIC_VERIFY')
group by stage;

-- 두 단계를 합친 적합. 성질이 다르므로(S1 출력 지배 / S2 고정비 지배)
-- 위의 단계별 결과와 어긋나면 단계별 쪽을 믿는다.
select regr_count(latency_ms, output_tokens)                     as n,
       round(regr_intercept(latency_ms, output_tokens))          as intercept_ms,
       round(regr_slope(latency_ms, output_tokens)::numeric, 3)  as ms_per_token,
       round(regr_r2(latency_ms, output_tokens)::numeric, 3)     as r2
from finready.llm_call_log
where parsed_ok and output_tokens is not null
  and stage in ('COVERAGE_CLASSIFY', 'SEMANTIC_VERIFY');

-- ---------------------------------------------------------------------------
-- 4. 캐시가 실제로 붙었는가
--
-- 캐시 미달은 오류 없이 조용히 안 걸린다. cache_read_tokens 가 유일한 신호다.
-- 병렬화 후 이 값이 0으로 떨어지면 배치마다 system 프롬프트가 갈라진 것이다 —
-- 그 사고는 오류도 로그도 테스트 실패도 없이 청구서로만 나타난다.
--
-- 팬아웃한 배치들은 동시에 나가므로 콜드에서는 셋 다 write 다(read 가 아니다).
-- 웜에서 read 가 0 이면 그때는 진짜 문제다.
-- ---------------------------------------------------------------------------
select stage,
       prompt_version,
       count(*)                       as calls,
       sum(cache_read_tokens)         as cache_read,
       sum(cache_write_tokens)        as cache_write,
       sum(input_tokens)              as input_total,
       sum(output_tokens)             as output_total
from finready.llm_call_log
where parsed_ok
  and stage in ('COVERAGE_CLASSIFY', 'SEMANTIC_VERIFY')
  and session_id in (:sessions)
group by stage, prompt_version
order by stage, prompt_version;

-- ---------------------------------------------------------------------------
-- 5. 팬아웃이 실제로 병렬로 나갔는가
--
-- OkHttp Dispatcher 나 커넥션 풀 때문에 조용히 직렬화되면 "팬아웃은 효과 없다"는
-- 잘못된 결론을 내게 된다. 한 세션의 COVERAGE_CLASSIFY 행들이 서로 겹치는
-- 시간대에 있어야 한다 — created_at 간격이 latency_ms 보다 훨씬 짧아야 정상이다.
-- ---------------------------------------------------------------------------
select session_id,
       count(*)                                                   as batches,
       min(created_at)                                            as first_done,
       max(created_at)                                            as last_done,
       extract(milliseconds from (max(created_at) - min(created_at)))::int as spread_ms,
       max(latency_ms)                                            as slowest_ms
from finready.llm_call_log
where parsed_ok
  and stage = 'COVERAGE_CLASSIFY'
  and session_id in (:sessions)
group by session_id
having count(*) > 1
order by session_id;
