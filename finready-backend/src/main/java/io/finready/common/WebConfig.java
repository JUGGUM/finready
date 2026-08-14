package io.finready.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * finready.cors.allowed-origins 를 실제로 적용한다. 설정만 있고 읽는 쪽이 없었다.
 *
 * <p>API 경로에만 건다. context-path 를 /api 로 잡지 않은 이유는 ProductController 참고.
 * 상품설명서 PDF(/documents/**)는 같은 오리진에서 열리므로 대상이 아니다.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

	private final String[] allowedOrigins;

	public WebConfig(@Value("${finready.cors.allowed-origins}") String allowedOrigins) {
		this.allowedOrigins = allowedOrigins.split("\\s*,\\s*");
	}

	@Override
	public void addCorsMappings(CorsRegistry registry) {
		registry.addMapping("/api/**")
				.allowedOrigins(allowedOrigins)
				.allowedMethods("GET", "POST")
				.allowedHeaders("*")
				.exposedHeaders(RequestIdFilter.HEADER)
				// P0 에 인증이 없다. 쿠키를 주고받지 않으므로 credentials 를 열지 않는다 (TRD §13.1)
				.allowCredentials(false);
	}
}
