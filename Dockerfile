# The runnable jar is built on the host (or in CI) first:
#     ./mvnw -DskipTests package
# This single-stage image just wraps that jar in a small JRE runtime.
#
# Why not a self-contained multi-stage Maven build? It works, but on this machine
# the Chinese project path forces Docker's legacy builder (BuildKit rejects the
# non-ASCII path), and the legacy builder re-downloads every Maven dependency
# inside the container on each build with no cache. Building the jar on the host
# (where ~/.m2 is already warm) and copying it in is dramatically faster.
FROM eclipse-temurin:21-jre-jammy
RUN apt-get update \
    && apt-get install --yes --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system --gid 10001 ordering \
    && useradd --system --uid 10001 --gid ordering --home /application ordering

WORKDIR /application
COPY --chown=ordering:ordering target/roundtable-*.jar application.jar

USER ordering
EXPOSE 8080
# MaxRAMPercentage lets the JVM size its heap from the container's memory limit.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "application.jar"]
