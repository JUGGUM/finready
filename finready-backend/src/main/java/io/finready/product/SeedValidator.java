package io.finready.product;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * TRD §4.5 시드 검증. 실패 항목을 하나만 던지지 않고 전부 모아서 보고한다 —
 * 시드를 고칠 때 한 번에 몇 개가 틀렸는지 알아야 왕복이 줄어든다.
 */
@Component
public class SeedValidator {

	/** TRD §4.5 검증 3: understandingCheck 대상은 정확히 이 셋이다 */
	private static final Set<String> EXPECTED_UNDERSTANDING_CHECK_RISK_IDS = Set.of("R01", "R02", "R03");

	private static final String DOCUMENT_URL_PREFIX = "/documents";

	private final ResourceLoader resourceLoader;
	private final String documentBasePath;

	public SeedValidator(ResourceLoader resourceLoader,
	                     @Value("${finready.document.base-path}") String documentBasePath) {
		this.resourceLoader = resourceLoader;
		this.documentBasePath = documentBasePath;
	}

	/**
	 * @throws SeedValidationException 검증 실패 항목이 하나라도 있으면
	 */
	public void validate(ProductSeedDocument seed) {
		List<String> failures = new ArrayList<>();

		validateProductPresent(seed, failures);
		if (!failures.isEmpty()) {
			throw new SeedValidationException(format(failures));
		}

		validateRiskFieldsPresent(seed.risks(), failures);          // TRD §4.5 검증 1
		validateCoveragePolicy(seed.risks(), failures);             // TRD §4.5 검증 2
		validateUnderstandingCheckRisks(seed.risks(), failures);    // TRD §4.5 검증 3
		validateDocumentHash(seed.product(), failures);             // TRD §4.5 검증 4 (§5.4)

		validateRiskIdsUnique(seed.risks(), failures);              // 추가: uq_product_risk 선반영
		validateRecheckQuestionDiffers(seed.risks(), failures);     // 추가: ck_recheck_question_differs 선반영

		if (!failures.isEmpty()) {
			throw new SeedValidationException(format(failures));
		}
	}

	private void validateProductPresent(ProductSeedDocument seed, List<String> failures) {
		if (seed == null || seed.product() == null) {
			failures.add("시드에 product 블록이 없다");
			return;
		}
		if (!StringUtils.hasText(seed.product().id())) {
			failures.add("product.id 가 비어 있다");
		}
		if (!StringUtils.hasText(seed.product().documentSha256())) {
			failures.add("product.documentSha256 이 비어 있다");
		}
		if (!StringUtils.hasText(seed.product().documentUrl())) {
			failures.add("product.documentUrl 이 비어 있다");
		}
		if (seed.risks() == null || seed.risks().isEmpty()) {
			failures.add("시드에 risks 가 없다");
		}
	}

	/** TRD §4.5 검증 1 — sourceText·sourcePage·fallback 3종이 모두 있어야 한다 */
	private void validateRiskFieldsPresent(List<RiskSeedData> risks, List<String> failures) {
		for (RiskSeedData risk : risks) {
			String id = riskLabel(risk);
			if (!StringUtils.hasText(risk.riskId())) {
				failures.add(id + ": riskId 가 비어 있다");
			}
			if (risk.sourcePage() == null) {
				failures.add(id + ": sourcePage 가 없다");
			}
			requireText(failures, id, "sourceText", risk.sourceText());
			requireText(failures, id, "fallbackQuestion", risk.fallbackQuestion());
			requireText(failures, id, "fallbackRecheckQuestion", risk.fallbackRecheckQuestion());
			requireText(failures, id, "fallbackPlainExplanation", risk.fallbackPlainExplanation());
			requireText(failures, id, "fact", risk.fact());
			requireText(failures, id, "title", risk.title());
			requireText(failures, id, "category", risk.category());
			requireText(failures, id, "verifiedBy", risk.verifiedBy());
			requireText(failures, id, "verifiedAt", risk.verifiedAt());
			if (risk.understandingCheck() == null) {
				failures.add(id + ": understandingCheck 가 없다");
			}
		}
	}

	/** TRD §4.5 검증 2 */
	private void validateCoveragePolicy(List<RiskSeedData> risks, List<String> failures) {
		for (RiskSeedData risk : risks) {
			String raw = risk.coveragePolicy();
			if (!StringUtils.hasText(raw)) {
				failures.add(riskLabel(risk) + ": coveragePolicy 가 비어 있다");
				continue;
			}
			try {
				CoveragePolicy.valueOf(raw);
			}
			catch (IllegalArgumentException ex) {
				failures.add(riskLabel(risk) + ": coveragePolicy '" + raw
						+ "' 는 TRD §6 목록에 없다. 허용값 = GATE_REQUIRED / WARN_ONLY / NOT_APPLICABLE");
			}
		}
	}

	/** TRD §4.5 검증 3 — 개수뿐 아니라 어느 Risk 인지까지 본다 */
	private void validateUnderstandingCheckRisks(List<RiskSeedData> risks, List<String> failures) {
		Set<String> actual = new TreeSet<>();
		for (RiskSeedData risk : risks) {
			if (Boolean.TRUE.equals(risk.understandingCheck())) {
				actual.add(risk.riskId());
			}
		}
		if (!EXPECTED_UNDERSTANDING_CHECK_RISK_IDS.equals(actual)) {
			failures.add("understandingCheck=true 인 Risk 가 " + actual
					+ " 다. TRD §4.5 는 정확히 " + new TreeSet<>(EXPECTED_UNDERSTANDING_CHECK_RISK_IDS) + " 를 요구한다");
		}
	}

	/** TRD §4.5 검증 4 / §5.4 — 실제 PDF 파일 해시와 시드 기재값을 대조한다 */
	private void validateDocumentHash(ProductSeedData product, List<String> failures) {
		String documentUrl = product.documentUrl();
		if (!documentUrl.startsWith(DOCUMENT_URL_PREFIX + "/")) {
			failures.add("product.documentUrl 이 '" + DOCUMENT_URL_PREFIX + "/' 로 시작하지 않는다: " + documentUrl
					+ " — finready.document.base-path 와 이어붙일 수 없다");
			return;
		}

		String location = documentBasePath + documentUrl.substring(DOCUMENT_URL_PREFIX.length());
		Resource resource = resourceLoader.getResource(location);
		if (!resource.exists()) {
			failures.add("상품설명서 파일이 없다: " + location);
			return;
		}

		String actual;
		try (InputStream in = resource.getInputStream()) {
			actual = sha256(in);
		}
		catch (IOException ex) {
			failures.add("상품설명서를 읽지 못했다: " + location + " (" + ex.getMessage() + ")");
			return;
		}

		String expected = product.documentSha256();
		if (!actual.equalsIgnoreCase(expected)) {
			failures.add("상품설명서 SHA-256 불일치 (" + location + ")"
					+ "\n    시드 기재값: " + expected
					+ "\n    실제 파일  : " + actual);
		}
	}

	/** uq_product_risk(product_id, risk_id) 를 INSERT 전에 잡는다 */
	private void validateRiskIdsUnique(List<RiskSeedData> risks, List<String> failures) {
		Set<String> seen = new HashSet<>();
		Set<String> duplicated = new TreeSet<>();
		for (RiskSeedData risk : risks) {
			if (!seen.add(risk.riskId())) {
				duplicated.add(risk.riskId());
			}
		}
		if (!duplicated.isEmpty()) {
			failures.add("riskId 가 중복됐다: " + duplicated);
		}
	}

	/** ck_recheck_question_differs 를 INSERT 전에 잡는다 (TRD §4.6 "동일 질문 반복 금지") */
	private void validateRecheckQuestionDiffers(List<RiskSeedData> risks, List<String> failures) {
		for (RiskSeedData risk : risks) {
			if (StringUtils.hasText(risk.fallbackQuestion())
					&& risk.fallbackQuestion().equals(risk.fallbackRecheckQuestion())) {
				failures.add(riskLabel(risk)
						+ ": fallbackRecheckQuestion 이 fallbackQuestion 과 같다. attempt 2 는 attempt 1 과 달라야 한다");
			}
		}
	}

	private void requireText(List<String> failures, String label, String field, String value) {
		if (!StringUtils.hasText(value)) {
			failures.add(label + ": " + field + " 가 비어 있다");
		}
	}

	private String riskLabel(RiskSeedData risk) {
		return "risk[" + (StringUtils.hasText(risk.riskId()) ? risk.riskId() : "?") + "]";
	}

	private String format(List<String> failures) {
		StringBuilder sb = new StringBuilder("시드 검증 실패 ").append(failures.size()).append("건 (TRD §4.5)");
		for (String failure : failures) {
			sb.append("\n  - ").append(failure);
		}
		return sb.toString();
	}

	private String sha256(InputStream in) throws IOException {
		MessageDigest digest;
		try {
			digest = MessageDigest.getInstance("SHA-256");
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 을 지원하지 않는 JVM", ex);
		}
		byte[] buffer = new byte[8192];
		int read;
		while ((read = in.read(buffer)) != -1) {
			digest.update(buffer, 0, read);
		}
		return HexFormat.of().formatHex(digest.digest());
	}
}
