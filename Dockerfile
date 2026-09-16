# ---- build stage -----------------------------------------------------------------------
# Multi-arch base image: builds the same on the Oracle Cloud ARM (aarch64) VM and on x86 machines.
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -q -B --no-transfer-progress dependency:resolve dependency:resolve-plugins
COPY src src
RUN ./mvnw -q -B --no-transfer-progress -DskipTests -DskipITs package

# ---- runtime stage ---------------------------------------------------------------------
FROM eclipse-temurin:25-jre
WORKDIR /app
RUN useradd --system --uid 1001 theotech && chown -R theotech /app
COPY --from=build /workspace/target/theo-tech-system-*.jar app.jar
USER theotech
EXPOSE 8080
ENV SPRING_PROFILES_ACTIVE=prod JAVA_OPTS="-XX:MaxRAMPercentage=60 -XX:+UseSerialGC"
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=5   CMD sh -c "wget -qO- http://127.0.0.1:8080/actuator/health | grep -q UP"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
