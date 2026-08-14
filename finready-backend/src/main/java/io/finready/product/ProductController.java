package io.finready.product;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * F01 — GET /api/products/demo.
 *
 * <p>경로에 /api 를 직접 쓴다. server.servlet.context-path 를 /api 로 잡으면
 * actuator 도 /api/actuator/health 로 옮겨가서 <b>이미 배포된 Render 헬스체크가 깨진다.</b>
 */
@RestController
@RequestMapping("/api/products")
public class ProductController {

	private final ProductQueryService productQueryService;

	public ProductController(ProductQueryService productQueryService) {
		this.productQueryService = productQueryService;
	}

	@GetMapping("/demo")
	public DemoProductResponse getDemoProduct() {
		return productQueryService.loadDemoProduct();
	}
}
