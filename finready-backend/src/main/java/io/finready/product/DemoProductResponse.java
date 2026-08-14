package io.finready.product;

import java.util.List;

/**
 * openapi.yml v1.4.2 DemoProductResponse.
 * required 가 [product, risks, customers] 다 — 셋 중 하나라도 비면 계약 위반이다.
 *
 * <p>엔티티를 그대로 내보내지 않는다. product 테이블의 document_sha256·is_live_demo 처럼
 * 계약에 없는 컬럼이 응답에 새는 것을 막는다.
 */
public record DemoProductResponse(
		ProductView product,
		List<RiskView> risks,
		List<String> understandingCheckRiskIds,
		List<CustomerView> customers
) {

	public record ProductView(
			String id,
			String name,
			String archetype,
			String productRiskVersion,
			String documentUrl,
			Integer documentPageCount,
			String syntheticNotice
	) {
		static ProductView from(Product product) {
			return new ProductView(
					product.getId(),
					product.getName(),
					product.getArchetype(),
					product.getProductRiskVersion(),
					product.getDocumentUrl(),
					product.getDocumentPageCount(),
					product.getSyntheticNotice());
		}
	}

	public record RiskView(
			String riskId,
			String category,
			String title,
			String fact,
			CoveragePolicy coveragePolicy,
			boolean understandingCheck,
			int sourcePage,
			String sourceText
	) {
		static RiskView from(ProductRisk risk) {
			return new RiskView(
					risk.getRiskId(),
					risk.getCategory(),
					risk.getTitle(),
					risk.getFact(),
					risk.getCoveragePolicy(),
					risk.isUnderstandingCheck(),
					risk.getSourcePage(),
					risk.getSourceText());
		}
	}

	public record CustomerView(
			String id,
			String label,
			String ageGroup,
			InvestmentExperience investmentExperience,
			FinancialLiteracy financialLiteracy,
			ExplanationLevel explanationLevel
	) {
		static CustomerView from(CustomerProfile profile) {
			return new CustomerView(
					profile.getId(),
					profile.getLabel(),
					profile.getAgeGroup(),
					profile.getInvestmentExperience(),
					profile.getFinancialLiteracy(),
					profile.getExplanationLevel());
		}
	}
}
