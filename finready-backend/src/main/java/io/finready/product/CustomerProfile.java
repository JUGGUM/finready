package io.finready.product;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * V1__init.sql / customer_profile. TRD §4.1 "Synthetic only".
 * created_at 컬럼이 없다 — DDL 그대로다.
 */
@Entity
@Table(name = "customer_profile")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CustomerProfile {

	@Id
	@Column(name = "id", length = 32, nullable = false)
	private String id;

	@Column(name = "label", length = 120, nullable = false)
	private String label;

	/** check 제약이 없는 자유 문자열이다. enum으로 올리지 않는다 */
	@Column(name = "age_group", length = 24)
	private String ageGroup;

	@Enumerated(EnumType.STRING)
	@Column(name = "investment_experience", length = 16)
	private InvestmentExperience investmentExperience;

	@Enumerated(EnumType.STRING)
	@Column(name = "financial_literacy", length = 16)
	private FinancialLiteracy financialLiteracy;

	@Enumerated(EnumType.STRING)
	@Column(name = "explanation_level", length = 16)
	private ExplanationLevel explanationLevel;

	public CustomerProfile(String id,
	                       String label,
	                       String ageGroup,
	                       InvestmentExperience investmentExperience,
	                       FinancialLiteracy financialLiteracy,
	                       ExplanationLevel explanationLevel) {
		this.id = id;
		this.label = label;
		this.ageGroup = ageGroup;
		this.investmentExperience = investmentExperience;
		this.financialLiteracy = financialLiteracy;
		this.explanationLevel = explanationLevel;
	}
}
