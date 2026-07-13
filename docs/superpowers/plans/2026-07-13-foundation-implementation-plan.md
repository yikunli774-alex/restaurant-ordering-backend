# Foundation and Engineering Guardrails Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the first runnable vertical slice: a Java 21/Spring Boot 3.5 API that compiles, migrates a real MySQL database, emits stable Problem Details, publishes OpenAPI and health endpoints, runs as two Docker containers behind Nginx, and passes CI.

**Architecture:** This phase creates one deployable modular-monolith artifact and the engineering seams later features will use. It intentionally excludes staff auth, Redis, RocketMQ, and business endpoints; those receive separate plans after this foundation is verified.

**Tech Stack:** Java 21, Spring Boot 3.5.16, Maven, Spring MVC, Bean Validation, Actuator, MySQL 8.4, Flyway, SpringDoc 2.8.10, JUnit 5, REST Assured, Testcontainers, Docker Compose, Nginx, GitHub Actions.

## Global Constraints

- The repository is an API-only modular monolith; do not create frontend files.
- Use Java 21 and Spring Boot 3.5.16 exactly in this plan.
- Use package-by-feature under `io.github.yikunli774.ordering`.
- Production code must not use field injection.
- Database structure is managed only through Flyway migrations.
- MySQL-specific behavior is tested against MySQL 8.4 through Testcontainers, never H2.
- API errors use `application/problem+json`, a stable application code, and `X-Trace-Id`.
- Configuration comes from environment variables; secrets are never committed.
- Every task follows red-green-refactor and ends with a focused commit.
- Do not add Redis, RocketMQ, Spring Security, JWT, Prometheus, Jaeger, or business tables in this phase.

## Planned file map

```text
.
├── .dockerignore
├── .env.example
├── .gitignore
├── Dockerfile
├── README.md
├── compose.yaml
├── pom.xml
├── mvnw / mvnw.cmd / .mvn/wrapper/*
├── deploy/nginx/nginx.conf
├── scripts/smoke-test.sh
├── .github/workflows/ci.yml
├── src/main/java/io/github/yikunli774/ordering
│   ├── RestaurantOrderingApplication.java
│   ├── common/api/ApiErrorCode.java
│   ├── common/api/ApiException.java
│   ├── common/api/FieldViolation.java
│   ├── common/api/GlobalExceptionHandler.java
│   ├── common/api/TraceIdFilter.java
│   └── common/config/OpenApiConfiguration.java
├── src/main/resources
│   ├── application.yml
│   ├── application-docker.yml
│   └── db/migration/V1__create_store.sql
└── src/test/java/io/github/yikunli774/ordering
    ├── RestaurantOrderingApplicationTest.java
    ├── support/AbstractIntegrationTest.java
    ├── support/TestcontainersConfiguration.java
    ├── common/api/ProblemDetailsWebMvcTest.java
    ├── common/config/OpenApiIntegrationTest.java
    └── foundation/DatabaseMigrationIntegrationTest.java
```

---

### Task 1: Bootstrap the Java 21 Spring Boot build

**Files:**
- Create: `pom.xml`
- Create: `.gitignore`
- Create: `src/main/java/io/github/yikunli774/ordering/RestaurantOrderingApplication.java`
- Create: `src/test/java/io/github/yikunli774/ordering/RestaurantOrderingApplicationTest.java`
- Generate: `mvnw`, `mvnw.cmd`, `.mvn/wrapper/maven-wrapper.properties`

**Interfaces:**
- Consumes: none
- Produces: `RestaurantOrderingApplication` as the Spring Boot entry point; Maven dependency and plugin management for every later task

- [ ] **Step 1: Create the build descriptor and a failing entry-point test**

Create `pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.5.16</version>
        <relativePath/>
    </parent>

    <groupId>io.github.yikunli774</groupId>
    <artifactId>restaurant-ordering-backend</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <name>restaurant-ordering-backend</name>
    <description>Concurrency-safe restaurant ordering backend</description>

    <properties>
        <java.version>21</java.version>
        <springdoc.version>2.8.10</springdoc.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-core</artifactId>
        </dependency>
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-mysql</artifactId>
        </dependency>
        <dependency>
            <groupId>com.mysql</groupId>
            <artifactId>mysql-connector-j</artifactId>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>org.springdoc</groupId>
            <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
            <version>${springdoc.version}</version>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-testcontainers</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>junit-jupiter</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>mysql</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>io.rest-assured</groupId>
            <artifactId>rest-assured</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <layers>
                        <enabled>true</enabled>
                    </layers>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

Create `src/test/java/io/github/yikunli774/ordering/RestaurantOrderingApplicationTest.java`:

```java
package io.github.yikunli774.ordering;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RestaurantOrderingApplicationTest {

    @Test
    void exposesAnApplicationEntryPoint() {
        assertThat(new RestaurantOrderingApplication()).isNotNull();
    }
}
```

- [ ] **Step 2: Run the test and verify the red state**

Run:

```bash
mvn -q -Dtest=RestaurantOrderingApplicationTest test
```

Expected: compilation fails because `RestaurantOrderingApplication` does not exist.

- [ ] **Step 3: Add the minimal application entry point**

Create `src/main/java/io/github/yikunli774/ordering/RestaurantOrderingApplication.java`:

```java
package io.github.yikunli774.ordering;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class RestaurantOrderingApplication {

    public static void main(String[] args) {
        SpringApplication.run(RestaurantOrderingApplication.class, args);
    }
}
```

Create `.gitignore`:

```gitignore
.idea/
.vscode/
*.iml
.DS_Store
target/
*.log
.env
docker-data/
```

- [ ] **Step 4: Generate the Maven Wrapper and run the test**

Run:

```bash
mvn -N wrapper:wrapper -Dmaven=3.9.11
./mvnw -q -Dtest=RestaurantOrderingApplicationTest test
```

Expected: `RestaurantOrderingApplicationTest` passes and Maven Wrapper files exist.

- [ ] **Step 5: Commit the build baseline**

```bash
git add pom.xml .gitignore mvnw mvnw.cmd .mvn src/main src/test
git commit -m "build: bootstrap Spring Boot application"
```

---

### Task 2: Add MySQL configuration, Flyway, and real integration tests

**Files:**
- Create: `src/main/resources/application.yml`
- Create: `src/main/resources/db/migration/V1__create_store.sql`
- Create: `src/test/java/io/github/yikunli774/ordering/support/TestcontainersConfiguration.java`
- Create: `src/test/java/io/github/yikunli774/ordering/support/AbstractIntegrationTest.java`
- Create: `src/test/java/io/github/yikunli774/ordering/foundation/DatabaseMigrationIntegrationTest.java`

**Interfaces:**
- Consumes: `RestaurantOrderingApplication`; datasource dependencies from Task 1
- Produces: a reusable `AbstractIntegrationTest`; MySQL schema version 1; environment-variable configuration contract

- [ ] **Step 1: Write a failing Flyway integration test**

Create `src/test/java/io/github/yikunli774/ordering/support/TestcontainersConfiguration.java`:

```java
package io.github.yikunli774.ordering.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.MySQLContainer;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    MySQLContainer<?> mysqlContainer() {
        return new MySQLContainer<>("mysql:8.4");
    }
}
```

Create `src/test/java/io/github/yikunli774/ordering/support/AbstractIntegrationTest.java`:

```java
package io.github.yikunli774.ordering.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
public abstract class AbstractIntegrationTest {
}
```

Create `src/test/java/io/github/yikunli774/ordering/foundation/DatabaseMigrationIntegrationTest.java`:

```java
package io.github.yikunli774.ordering.foundation;

import io.github.yikunli774.ordering.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class DatabaseMigrationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void flywayCreatesTheStoreTable() {
        Integer tableCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = DATABASE()
                  AND table_name = 'store'
                """, Integer.class);

        assertThat(tableCount).isEqualTo(1);
    }
}
```

- [ ] **Step 2: Run the integration test and verify the red state**

Run:

```bash
./mvnw -Dtest=DatabaseMigrationIntegrationTest test
```

Expected: the Spring context or assertion fails because application configuration and migration do not exist.

- [ ] **Step 3: Add configuration and the versioned migration**

Create `src/main/resources/application.yml`:

```yaml
spring:
  application:
    name: restaurant-ordering-backend
  datasource:
    url: ${DB_URL:jdbc:mysql://localhost:3306/restaurant_ordering?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC&useSSL=false&allowPublicKeyRetrieval=true}
    username: ${DB_USERNAME:ordering}
    password: ${DB_PASSWORD:ordering}
    hikari:
      maximum-pool-size: ${DB_POOL_SIZE:10}
      minimum-idle: ${DB_POOL_MIN_IDLE:2}
      connection-timeout: 3000
  flyway:
    enabled: true
    locations: classpath:db/migration
    validate-migration-naming: true
  lifecycle:
    timeout-per-shutdown-phase: 20s

server:
  port: ${SERVER_PORT:8080}
  shutdown: graceful
  forward-headers-strategy: framework

management:
  endpoints:
    web:
      exposure:
        include: health,info
  endpoint:
    health:
      probes:
        enabled: true
      show-details: never
  health:
    readinessstate:
      enabled: true
    livenessstate:
      enabled: true

springdoc:
  api-docs:
    path: /v3/api-docs
  swagger-ui:
    path: /swagger-ui.html
```

Create `src/main/resources/db/migration/V1__create_store.sql`:

```sql
CREATE TABLE store (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    code VARCHAR(32) NOT NULL,
    name VARCHAR(100) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_store_code UNIQUE (code),
    CONSTRAINT ck_store_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
```

- [ ] **Step 4: Run the integration test and the complete suite**

Run:

```bash
./mvnw -Dtest=DatabaseMigrationIntegrationTest test
./mvnw test
```

Expected: Flyway applies migration version 1 and all tests pass. If Docker is not running, start Docker Desktop and rerun; do not replace MySQL with H2.

- [ ] **Step 5: Commit database foundations**

```bash
git add src/main/resources src/test/java/io/github/yikunli774/ordering/support src/test/java/io/github/yikunli774/ordering/foundation
git commit -m "feat: add MySQL migration foundation"
```

---

### Task 3: Establish stable errors, trace IDs, and OpenAPI

**Files:**
- Create: `src/main/java/io/github/yikunli774/ordering/common/api/ApiErrorCode.java`
- Create: `src/main/java/io/github/yikunli774/ordering/common/api/ApiException.java`
- Create: `src/main/java/io/github/yikunli774/ordering/common/api/FieldViolation.java`
- Create: `src/main/java/io/github/yikunli774/ordering/common/api/TraceIdFilter.java`
- Create: `src/main/java/io/github/yikunli774/ordering/common/api/GlobalExceptionHandler.java`
- Create: `src/main/java/io/github/yikunli774/ordering/common/config/OpenApiConfiguration.java`
- Create: `src/test/java/io/github/yikunli774/ordering/common/api/ProblemDetailsWebMvcTest.java`
- Create: `src/test/java/io/github/yikunli774/ordering/common/config/OpenApiIntegrationTest.java`

**Interfaces:**
- Consumes: Spring MVC, Bean Validation, and `AbstractIntegrationTest`
- Produces: `ApiException(HttpStatus, ApiErrorCode, String)` for all later features; `X-Trace-Id` response contract; `/v3/api-docs`

- [ ] **Step 1: Write failing MVC contract tests**

Create `src/test/java/io/github/yikunli774/ordering/common/api/ProblemDetailsWebMvcTest.java`:

```java
package io.github.yikunli774.ordering.common.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ProblemDetailsWebMvcTest.TestController.class)
@Import({GlobalExceptionHandler.class, TraceIdFilter.class})
class ProblemDetailsWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void validationFailureUsesStableProblemDetails() throws Exception {
        mockMvc.perform(post("/test/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(header().exists(TraceIdFilter.TRACE_ID_HEADER))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(jsonPath("$.violations[0].field").value("name"));
    }

    @Test
    void domainFailurePreservesApplicationCode() throws Exception {
        mockMvc.perform(get("/test/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"))
                .andExpect(jsonPath("$.detail").value("state changed"));
    }

    @RestController
    static class TestController {

        @PostMapping("/test/validation")
        String validate(@Valid @RequestBody TestRequest request) {
            return request.name();
        }

        @GetMapping("/test/conflict")
        String conflict() {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    ApiErrorCode.INVALID_STATE_TRANSITION,
                    "state changed"
            );
        }
    }

    record TestRequest(@NotBlank String name) {
    }
}
```

- [ ] **Step 2: Run the MVC test and verify the red state**

Run:

```bash
./mvnw -Dtest=ProblemDetailsWebMvcTest test
```

Expected: test compilation fails because the error and trace types do not exist.

- [ ] **Step 3: Implement the error and trace contracts**

Create `src/main/java/io/github/yikunli774/ordering/common/api/ApiErrorCode.java`:

```java
package io.github.yikunli774.ordering.common.api;

public enum ApiErrorCode {
    VALIDATION_FAILED,
    INVALID_STATE_TRANSITION,
    RESOURCE_NOT_FOUND,
    INTERNAL_ERROR
}
```

Create `src/main/java/io/github/yikunli774/ordering/common/api/ApiException.java`:

```java
package io.github.yikunli774.ordering.common.api;

import org.springframework.http.HttpStatus;

public final class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final ApiErrorCode code;

    public ApiException(HttpStatus status, ApiErrorCode code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() {
        return status;
    }

    public ApiErrorCode code() {
        return code;
    }
}
```

Create `src/main/java/io/github/yikunli774/ordering/common/api/FieldViolation.java`:

```java
package io.github.yikunli774.ordering.common.api;

public record FieldViolation(String field, String message) {
}
```

Create `src/main/java/io/github/yikunli774/ordering/common/api/TraceIdFilter.java`:

```java
package io.github.yikunli774.ordering.common.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
public final class TraceIdFilter extends OncePerRequestFilter {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String TRACE_ID_MDC_KEY = "traceId";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String traceId = UUID.randomUUID().toString().replace("-", "");
        MDC.put(TRACE_ID_MDC_KEY, traceId);
        response.setHeader(TRACE_ID_HEADER, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(TRACE_ID_MDC_KEY);
        }
    }
}
```

Create `src/main/java/io/github/yikunli774/ordering/common/api/GlobalExceptionHandler.java`:

```java
package io.github.yikunli774.ordering.common.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.util.List;

@RestControllerAdvice
public final class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidation(MethodArgumentNotValidException exception) {
        List<FieldViolation> violations = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldViolation(error.getField(), error.getDefaultMessage()))
                .toList();
        ProblemDetail problem = baseProblem(
                HttpStatus.BAD_REQUEST,
                ApiErrorCode.VALIDATION_FAILED,
                "Request validation failed"
        );
        problem.setProperty("violations", violations);
        return problem;
    }

    @ExceptionHandler(ApiException.class)
    ProblemDetail handleApi(ApiException exception) {
        return baseProblem(exception.status(), exception.code(), exception.getMessage());
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception exception) {
        log.error("Unhandled request failure", exception);
        return baseProblem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                ApiErrorCode.INTERNAL_ERROR,
                "An unexpected error occurred"
        );
    }

    private ProblemDetail baseProblem(HttpStatus status, ApiErrorCode code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create("https://api.restaurant-ordering.local/problems/" + code.name().toLowerCase()));
        problem.setTitle(status.getReasonPhrase());
        problem.setProperty("code", code.name());
        problem.setProperty("traceId", MDC.get(TraceIdFilter.TRACE_ID_MDC_KEY));
        return problem;
    }
}
```

- [ ] **Step 4: Run the MVC tests and verify green**

Run:

```bash
./mvnw -Dtest=ProblemDetailsWebMvcTest test
```

Expected: both Problem Details tests pass.

- [ ] **Step 5: Write a failing OpenAPI integration test**

Create `src/test/java/io/github/yikunli774/ordering/common/config/OpenApiIntegrationTest.java`:

```java
package io.github.yikunli774.ordering.common.config;

import io.github.yikunli774.ordering.support.AbstractIntegrationTest;
import io.restassured.RestAssured;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;

import static org.hamcrest.Matchers.equalTo;

class OpenApiIntegrationTest extends AbstractIntegrationTest {

    @LocalServerPort
    private int port;

    @Test
    void publishesVersionedOpenApiMetadata() {
        RestAssured.given()
                .port(port)
                .when()
                .get("/v3/api-docs")
                .then()
                .statusCode(200)
                .body("info.title", equalTo("Restaurant Ordering API"))
                .body("info.version", equalTo("v1"));
    }
}
```

Run:

```bash
./mvnw -Dtest=OpenApiIntegrationTest test
```

Expected: test fails because the expected OpenAPI title/version are absent.

- [ ] **Step 6: Add explicit OpenAPI metadata and rerun all tests**

Create `src/main/java/io/github/yikunli774/ordering/common/config/OpenApiConfiguration.java`:

```java
package io.github.yikunli774.ordering.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfiguration {

    @Bean
    OpenAPI restaurantOrderingOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Restaurant Ordering API")
                .version("v1")
                .description("Concurrency-safe restaurant ordering backend"));
    }
}
```

Run:

```bash
./mvnw test
```

Expected: all unit, MVC, MySQL, Flyway, and OpenAPI tests pass.

- [ ] **Step 7: Commit API contracts**

```bash
git add src/main/java/io/github/yikunli774/ordering/common src/test/java/io/github/yikunli774/ordering/common
git commit -m "feat: establish API error contracts"
```

---

### Task 4: Package and run two application instances with Docker Compose

**Files:**
- Create: `.dockerignore`
- Create: `.env.example`
- Create: `Dockerfile`
- Create: `src/main/resources/application-docker.yml`
- Create: `compose.yaml`
- Create: `deploy/nginx/nginx.conf`
- Create: `scripts/smoke-test.sh`

**Interfaces:**
- Consumes: executable Spring Boot Jar, readiness endpoint, MySQL environment configuration
- Produces: `docker compose up --build -d` deployment on `http://localhost:8080`; executable smoke test

- [ ] **Step 1: Create the smoke test before container infrastructure**

Create `scripts/smoke-test.sh`:

```bash
#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"

curl --fail --silent --show-error \
  "${BASE_URL}/actuator/health/readiness" \
  | grep --quiet '"status":"UP"'

curl --fail --silent --show-error \
  "${BASE_URL}/v3/api-docs" \
  | grep --quiet '"title":"Restaurant Ordering API"'

echo "Smoke test passed: ${BASE_URL}"
```

Run:

```bash
chmod +x scripts/smoke-test.sh
./scripts/smoke-test.sh
```

Expected: connection failure because the Compose environment does not exist.

- [ ] **Step 2: Add the multi-stage application image**

Create `.dockerignore`:

```dockerignore
.git
.github
.idea
.vscode
target
docker-data
*.log
.env
docs
```

Create `Dockerfile`:

```dockerfile
# syntax=docker/dockerfile:1.7
FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw -B -DskipTests dependency:go-offline

COPY src/ src/
RUN ./mvnw -B -DskipTests package
RUN java -Djarmode=tools -jar target/restaurant-ordering-backend-*.jar \
    extract --layers --destination /workspace/extracted

FROM eclipse-temurin:21-jre-jammy AS runtime
RUN apt-get update \
    && apt-get install --yes --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system --gid 10001 ordering \
    && useradd --system --uid 10001 --gid ordering --home /application ordering

WORKDIR /application
COPY --from=build --chown=ordering:ordering /workspace/extracted/dependencies/ ./
COPY --from=build --chown=ordering:ordering /workspace/extracted/spring-boot-loader/ ./
COPY --from=build --chown=ordering:ordering /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=ordering:ordering /workspace/extracted/application/ ./

USER ordering
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "application.jar"]
```

- [ ] **Step 3: Add Docker profile, Compose services, and Nginx**

Create `src/main/resources/application-docker.yml`:

```yaml
spring:
  datasource:
    url: jdbc:mysql://mysql:3306/restaurant_ordering?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC&useSSL=false&allowPublicKeyRetrieval=true
    username: ${DB_USERNAME:ordering}
    password: ${DB_PASSWORD:ordering}
```

Create `.env.example`:

```dotenv
MYSQL_ROOT_PASSWORD=change-me-root
DB_USERNAME=ordering
DB_PASSWORD=change-me-app
```

Create `deploy/nginx/nginx.conf`:

```nginx
events {}

http {
    upstream ordering_backend {
        least_conn;
        server app-1:8080;
        server app-2:8080;
    }

    server {
        listen 8080;
        client_max_body_size 1m;

        location / {
            proxy_pass http://ordering_backend;
            proxy_http_version 1.1;
            proxy_set_header Host $host;
            proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
            proxy_set_header X-Forwarded-Proto $scheme;
            proxy_connect_timeout 3s;
            proxy_read_timeout 30s;
        }
    }
}
```

Create `compose.yaml`:

```yaml
name: restaurant-ordering

x-app: &app
  image: restaurant-ordering-backend:local
  build:
    context: .
  environment:
    SPRING_PROFILES_ACTIVE: docker
    DB_USERNAME: ${DB_USERNAME:-ordering}
    DB_PASSWORD: ${DB_PASSWORD:-ordering}
  depends_on:
    mysql:
      condition: service_healthy
  restart: unless-stopped
  healthcheck:
    test: ["CMD", "curl", "--fail", "--silent", "http://localhost:8080/actuator/health/readiness"]
    interval: 10s
    timeout: 3s
    retries: 12
    start_period: 30s

services:
  mysql:
    image: mysql:8.4
    environment:
      MYSQL_DATABASE: restaurant_ordering
      MYSQL_USER: ${DB_USERNAME:-ordering}
      MYSQL_PASSWORD: ${DB_PASSWORD:-ordering}
      MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD:-ordering-root}
    command: ["--character-set-server=utf8mb4", "--collation-server=utf8mb4_0900_ai_ci"]
    volumes:
      - mysql-data:/var/lib/mysql
    healthcheck:
      test: ["CMD-SHELL", "mysqladmin ping -h localhost -uroot -p$${MYSQL_ROOT_PASSWORD} --silent"]
      interval: 5s
      timeout: 3s
      retries: 20
      start_period: 20s

  app-1:
    <<: *app

  app-2:
    <<: *app

  nginx:
    image: nginx:1.28-alpine
    ports:
      - "8080:8080"
    volumes:
      - ./deploy/nginx/nginx.conf:/etc/nginx/nginx.conf:ro
    depends_on:
      app-1:
        condition: service_healthy
      app-2:
        condition: service_healthy
    restart: unless-stopped

volumes:
  mysql-data:
```

- [ ] **Step 4: Validate Compose, start the stack, and run the smoke test**

Run:

```bash
cp .env.example .env
docker compose config --quiet
docker compose up --build -d
docker compose ps
./scripts/smoke-test.sh
```

Expected: MySQL, `app-1`, `app-2`, and Nginx are healthy/running; the smoke test prints `Smoke test passed`.

Inspect both app instances:

```bash
docker compose logs --no-log-prefix app-1 app-2
```

Expected: both applications started and Flyway reports the same schema version without migration errors.

- [ ] **Step 5: Stop containers and commit deployment files**

Run:

```bash
docker compose down
git add .dockerignore .env.example Dockerfile compose.yaml deploy scripts src/main/resources/application-docker.yml
git commit -m "build: add two-instance Docker deployment"
```

Do not use `docker compose down -v`; the named database volume is preserved unless explicitly cleaning test data.

---

### Task 5: Add CI and the first operator-facing README

**Files:**
- Create: `.github/workflows/ci.yml`
- Create: `README.md`

**Interfaces:**
- Consumes: Maven Wrapper, full test suite, Dockerfile, Compose smoke test contract
- Produces: automated pull-request verification and a reproducible onboarding path

- [ ] **Step 1: Add the CI workflow**

Create `.github/workflows/ci.yml`:

```yaml
name: CI

on:
  push:
    branches: [main]
  pull_request:

permissions:
  contents: read

jobs:
  verify:
    runs-on: ubuntu-latest
    timeout-minutes: 20
    steps:
      - name: Check out repository
        uses: actions/checkout@v4

      - name: Set up Java 21
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: "21"
          cache: maven

      - name: Verify Maven project
        run: ./mvnw --batch-mode --no-transfer-progress verify

      - name: Validate Compose model
        run: docker compose config --quiet

      - name: Build application image
        run: docker build --tag restaurant-ordering:${{ github.sha }} .
```

- [ ] **Step 2: Add concise onboarding documentation**

Create `README.md`:

```markdown
# Restaurant Ordering Backend

API-only Java backend for concurrency-safe table ordering. The project focuses
on shared-cart coordination, idempotent ordering, inventory correctness,
staff RBAC, reliable messaging, repeatable tests, and observable deployment.

## Current milestone

The foundation milestone provides:

- Java 21 and Spring Boot 3.5
- MySQL schema migrations with Flyway
- stable Problem Details and trace IDs
- OpenAPI and Actuator health endpoints
- real MySQL integration tests with Testcontainers
- two application containers behind Nginx
- GitHub Actions verification

Business modules are added as independently reviewed vertical slices.

## Prerequisites

- Docker Desktop with Docker Compose v2
- Java 21 only when running outside Docker

## Start

```bash
cp .env.example .env
docker compose up --build -d
./scripts/smoke-test.sh
```

OpenAPI: <http://localhost:8080/swagger-ui.html>

Readiness: <http://localhost:8080/actuator/health/readiness>

## Test

```bash
./mvnw verify
```

Integration tests start a real MySQL 8.4 container. Docker must be running.

## Stop

```bash
docker compose down
```

## Design

- [Backend design](docs/superpowers/specs/2026-07-13-restaurant-ordering-backend-design.md)
- [Foundation implementation plan](docs/superpowers/plans/2026-07-13-foundation-implementation-plan.md)
```

- [ ] **Step 3: Verify the same commands CI will run**

Run:

```bash
./mvnw --batch-mode --no-transfer-progress verify
docker compose config --quiet
docker build --tag restaurant-ordering:local .
git diff --check
```

Expected: tests pass, Compose is valid, the Docker image builds, and `git diff --check` emits no output.

- [ ] **Step 4: Commit and push the foundation plan result**

```bash
git add .github/workflows/ci.yml README.md
git commit -m "ci: verify foundation build"
git push origin main
```

Expected: GitHub Actions starts on `main` and the `verify` job passes.

## Phase completion checkpoint

Before planning staff authentication, confirm all of these:

```bash
git status --short
./mvnw verify
docker compose up --build -d
./scripts/smoke-test.sh
docker compose ps
```

Expected:

- `git status --short` is empty.
- Maven tests pass, including MySQL/Flyway integration tests.
- both application instances and MySQL are healthy.
- Nginx serves readiness and OpenAPI on port 8080.
- the GitHub Actions `verify` job is green.

Only then write the separate staff authentication and RBAC implementation plan.

## Spec coverage and deferred implementation plans

This foundation plan covers the design's build baseline, configuration, Flyway,
error contract, trace correlation, OpenAPI, Actuator health, MySQL integration
testing, two-instance container deployment, and CI entry point. The remaining
design sections are intentionally split into these independently reviewable
plans, in order:

1. Staff authentication, refresh rotation, Redis-backed sessions, and RBAC
2. Store/table setup, signed table codes, anonymous participants, and sessions
3. Menu and inventory management with MyBatis-Plus and ArchUnit boundaries
4. Redis shared cart, Lua mutation idempotency, freeze, and recovery
5. Idempotent order submission, MySQL CAS, inventory ledger, and compensation
6. Simulated payment callbacks, kitchen state machine, and timeout cancellation
7. Transactional Outbox, RocketMQ retry/deduplication, and reconciliation
8. Prometheus/OpenTelemetry observability, concurrency tests, and benchmarks

No deferred subsystem dependency is added in this phase. Each plan introduces
its own library only when executable behavior first needs it.
