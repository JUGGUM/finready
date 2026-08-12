-- FinReady V2__audit_append_only.sql
-- 기준: Backend TRD v1.2.3 §4.4
--
-- 주의: 트리거 이벤트에 INSERT를 넣으면 감사 기록 자체가 막혀 앱이 통째로 죽는다.
--       이 사고는 통합 테스트 없이는 배포 후에 발견된다. §17 DB 테스트 필수.

create or replace function prevent_mutation() returns trigger
language plpgsql as $$
begin
    raise exception 'append-only table: %', tg_table_name
        using errcode = 'restrict_violation';
end $$;

-- 행 단위: UPDATE / DELETE 만
create trigger audit_event_no_row_mutation
    before update or delete on audit_event
    for each row execute function prevent_mutation();

-- 문 단위: TRUNCATE (row trigger로는 막을 수 없다)
create trigger audit_event_no_truncate
    before truncate on audit_event
    for each statement execute function prevent_mutation();