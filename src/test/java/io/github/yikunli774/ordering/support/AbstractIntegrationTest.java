package io.github.yikunli774.ordering.support;

import io.github.yikunli774.ordering.TestcontainersConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Base class for tests that need the full Spring context backed by a real
 * MySQL 8.4 container. Extend this instead of repeating the annotations.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
public abstract class AbstractIntegrationTest {
}
