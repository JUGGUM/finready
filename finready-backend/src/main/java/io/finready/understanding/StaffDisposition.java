package io.finready.understanding;

/**
 * staff_resolution.disposition — ck_staff_disposition.
 *
 * <p>FinalDisposition 의 부분집합이지만 별도 enum 으로 둔다. 재사용하면 AUTO_RESOLVED 나
 * SKIPPED_BY_OVERRIDE 를 넣는 코드가 컴파일에 통과하고 INSERT 시점에야 터진다.
 */
public enum StaffDisposition {
	RESOLVED_BY_STAFF,
	UNRESOLVED
}
