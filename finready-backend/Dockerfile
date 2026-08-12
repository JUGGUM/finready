# FinReady Backend — Render Web Service (Singapore)
# 기준: Backend TRD v1.2.3 §16 / Java 25 LTS + Spring Boot 4.0.7
#
# 모노레포 주의: Render 서비스 설정에서 Root Directory를 backend 로 지정할 것.
#               그래야 이 파일의 COPY 경로가 맞는다.

# ---------- build ----------
# gradle 공식 이미지 대신 temurin + Gradle Wrapper를 쓴다.
# 래퍼가 Gradle 버전을 저장소에 고정하므로 로컬과 컨테이너 빌드가 항상 일치한다.
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

# 의존성 레이어를 먼저 굳혀 재빌드를 빠르게 한다
COPY gradlew ./
COPY gradle ./gradle
RUN chmod +x ./gradlew && ./gradlew --version --no-daemon

COPY build.gradle.kts settings.gradle.kts ./
RUN ./gradlew dependencies --no-daemon || true

COPY src ./src
RUN ./gradlew bootJar --no-daemon -x test

# ---------- runtime ----------
FROM eclipse-temurin:25-jre
WORKDIR /app

# root로 돌리지 않는다
RUN useradd --system --create-home --shell /usr/sbin/nologin finready
USER finready

COPY --from=build --chown=finready:finready /workspace/build/libs/*.jar app.jar

# Render는 PORT 환경변수를 주입한다
ENV PORT=8080
EXPOSE 8080

# 컨테이너 메모리에 맞춰 힙을 자동 산정. Starter 인스턴스 기준
ENV JAVA_OPTS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]