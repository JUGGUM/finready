package io.finready.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 요청마다 식별자를 만들어 MDC 와 응답 헤더에 싣는다.
 *
 * <p>application.yaml 의 로그 패턴이 이미 {@code [%X{requestId}]} 를 쓰고 있는데
 * 넣는 쪽이 없어서 지금까지 빈 대괄호로 찍혔다.
 *
 * <p>오류 응답의 requestId 와 서버 로그의 값이 같아야 "화면에 뜬 번호로 로그를 찾는" 흐름이 성립한다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

	public static final String MDC_KEY = "requestId";
	public static final String HEADER = "X-Request-Id";

	@Override
	protected void doFilterInternal(HttpServletRequest request,
	                                HttpServletResponse response,
	                                FilterChain chain) throws ServletException, IOException {
		String requestId = resolve(request);
		MDC.put(MDC_KEY, requestId);
		response.setHeader(HEADER, requestId);
		try {
			chain.doFilter(request, response);
		}
		finally {
			// 스레드가 재사용되므로 반드시 지운다. 안 지우면 다음 요청 로그에 남의 id 가 찍힌다
			MDC.remove(MDC_KEY);
		}
	}

	/** 클라이언트가 보낸 값이 있으면 잇는다. 로그를 프론트 요청 단위로 묶을 수 있다 */
	private String resolve(HttpServletRequest request) {
		String inbound = request.getHeader(HEADER);
		if (StringUtils.hasText(inbound) && inbound.length() <= 64) {
			return inbound;
		}
		return UUID.randomUUID().toString().substring(0, 8);
	}

	public static String current() {
		String value = MDC.get(MDC_KEY);
		return value != null ? value : "";
	}
}
