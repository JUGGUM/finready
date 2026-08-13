package io.finready.product;

/**
 * 시드 검증 실패. finready.seed.fail-fast=true 면 이 예외가 기동을 중단시킨다 (TRD §4.5).
 * PRD §19 "정확히 일치" 요건을 서류가 아니라 코드로 지키는 지점이다.
 */
public class SeedValidationException extends RuntimeException {

	public SeedValidationException(String message) {
		super(message);
	}
}
