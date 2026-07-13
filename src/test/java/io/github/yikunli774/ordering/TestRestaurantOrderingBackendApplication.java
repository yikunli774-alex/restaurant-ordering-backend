package io.github.yikunli774.ordering;

import org.springframework.boot.SpringApplication;

public class TestRestaurantOrderingBackendApplication {

	public static void main(String[] args) {
		SpringApplication.from(RestaurantOrderingBackendApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
