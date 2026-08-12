plugins {
	java
	id("org.springframework.boot") version "4.0.7"
	id("io.spring.dependency-management") version "1.1.7"
}

group = "io.finready"
version = "0.0.1-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(25)
	}
}

repositories {
	mavenCentral()
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-web")
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-actuator")

	// 스키마 변경은 Flyway로만. JPA는 validate만 한다 (TRD §1)
	implementation("org.flywaydb:flyway-core")
	implementation("org.flywaydb:flyway-database-postgresql")
	runtimeOnly("org.postgresql:postgresql")

	// openapi.yaml v1.4.2와 구현을 CI에서 diff한다 (TRD §17 계약 테스트)
	// Boot 4는 springdoc 3.x 라인이다. 2.x는 Boot 3 전용이므로 쓰면 안 된다.
	implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.0")

	// 시드 sourceText ↔ PDF 대조. 런타임이 아니라 테스트에서만 쓴다 (TRD §5.4)
	testImplementation("org.apache.pdfbox:pdfbox:3.0.3")

	compileOnly("org.projectlombok:lombok")
	annotationProcessor("org.projectlombok:lombok")

	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// 기본 test: 실제 LLM을 호출하는 평가 모듈은 제외한다 (TRD §17)
tasks.named<Test>("test") {
	systemProperty("spring.profiles.active", "test")
	useJUnitPlatform {
		excludeTags("evaluation")
	}
}

// 오프라인 평가 실행: ./gradlew evaluate
tasks.register<Test>("evaluate") {
	description = "Hold-out / Rule baseline 오프라인 평가. 실제 LLM을 호출한다"
	group = "verification"
	testClassesDirs = sourceSets["test"].output.classesDirs
	classpath = sourceSets["test"].runtimeClasspath
	systemProperty("spring.profiles.active", "eval")
	useJUnitPlatform {
		includeTags("evaluation")
	}
	outputs.upToDateWhen { false }
}