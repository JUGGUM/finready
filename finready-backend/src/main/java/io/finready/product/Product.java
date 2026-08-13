package io.finready.product;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

/**
 * V1__init.sql / product.
 * id 는 시드가 지정하는 값이므로 생성 전략을 두지 않는다.
 */
@Entity
@Table(name = "product")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product {

	@Id
	@Column(name = "id", length = 32, nullable = false)
	private String id;

	@Column(name = "name", length = 200, nullable = false)
	private String name;

	@Column(name = "archetype", length = 64, nullable = false)
	private String archetype;

	@Column(name = "product_risk_version", length = 64, nullable = false)
	private String productRiskVersion;

	@Column(name = "document_id", length = 64, nullable = false)
	private String documentId;

	@Column(name = "document_url", length = 300, nullable = false)
	private String documentUrl;

	@Column(name = "document_page_count")
	private Integer documentPageCount;

	/**
	 * 기동 시 실제 PDF 해시와 대조하며 불일치 시 기동 중단 (TRD §5.4).
	 * DDL 이 char(64) 라 JDBC 타입 코드가 CHAR(bpchar)다. columnDefinition 은 DDL 생성용이라
	 * 타입 코드를 바꾸지 못하므로 @JdbcTypeCode 로 지정해야 validate 를 통과한다.
	 */
	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(name = "document_sha256", length = 64, nullable = false)
	private String documentSha256;

	@Column(name = "synthetic_notice", columnDefinition = "text")
	private String syntheticNotice;

	@Column(name = "is_live_demo", nullable = false)
	private boolean liveDemo;

	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt;

	public Product(String id,
	               String name,
	               String archetype,
	               String productRiskVersion,
	               String documentId,
	               String documentUrl,
	               Integer documentPageCount,
	               String documentSha256,
	               String syntheticNotice,
	               boolean liveDemo) {
		this.id = id;
		this.name = name;
		this.archetype = archetype;
		this.productRiskVersion = productRiskVersion;
		this.documentId = documentId;
		this.documentUrl = documentUrl;
		this.documentPageCount = documentPageCount;
		this.documentSha256 = documentSha256;
		this.syntheticNotice = syntheticNotice;
		this.liveDemo = liveDemo;
		this.createdAt = OffsetDateTime.now();
	}
}
