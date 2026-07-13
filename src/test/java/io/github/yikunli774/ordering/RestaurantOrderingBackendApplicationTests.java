package io.github.yikunli774.ordering;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class RestaurantOrderingBackendApplicationTests {

	@Test
	void exposesAnApplicationEntryPoint() {
		assertThat(new RestaurantOrderingBackendApplication()).isNotNull();
	}

}
