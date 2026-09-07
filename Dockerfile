# 실행 전용 이미지. 빌드는 CI(gradle bootJar)에서 끝내고 jar 만 담는다.
# RUN 단계가 없어 buildx 로 arm64 이미지를 에뮬레이션 없이 만들 수 있다.
FROM eclipse-temurin:21-jre

WORKDIR /app
COPY build/app.jar app.jar

ENV JAVA_TOOL_OPTIONS="-Xms256m -Xmx2g -XX:+UseG1GC -Duser.timezone=Asia/Seoul"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
