package io.finready.common;

import org.hibernate.exception.ConstraintViolationException;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 제약 위반 예외에서 DB 제약 이름을 꺼낸다.
 *
 * <p>경로가 둘이라 추출 방법도 둘이다. JPA 로 저장하면 Hibernate 가
 * {@link ConstraintViolationException} 으로 감싸며 이름을 직접 들고 있지만,
 * {@code JdbcTemplate} 로 직접 쓰면 Spring 이 드라이버 예외를 그대로 번역하므로
 * 이름이 메시지 안에만 남는다. 후자를 위해 메시지 파싱을 폴백으로 둔다.
 *
 * <p>드라이버 클래스({@code PSQLException})를 직접 참조하지 않는다 —
 * postgresql 은 {@code runtimeOnly} 라 컴파일 클래스패스에 없다.
 */
public final class ConstraintNames {

	/** Postgres 오류 메시지의 {@code ... constraint "uq_revision"} 부분 */
	private static final Pattern CONSTRAINT_IN_MESSAGE =
			Pattern.compile("constraint\\s+\"([^\"]+)\"");

	private ConstraintNames() {
	}

	/** 찾지 못하면 null. 이름을 모르는 위반은 호출부에서 보수적으로 다뤄야 한다 */
	public static String of(Throwable ex) {
		for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
			if (cause instanceof ConstraintViolationException hibernateEx
					&& hibernateEx.getConstraintName() != null) {
				return hibernateEx.getConstraintName();
			}
		}

		for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
			String message = cause.getMessage();
			if (message == null) {
				continue;
			}
			Matcher matcher = CONSTRAINT_IN_MESSAGE.matcher(message);
			if (matcher.find()) {
				return matcher.group(1);
			}
		}
		return null;
	}
}
