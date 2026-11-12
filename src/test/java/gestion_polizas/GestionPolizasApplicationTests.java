package gestion_polizas;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "app.security.api-key=test-key")
class GestionPolizasApplicationTests {
	@Autowired
	ApplicationContext context;

	@Test
	void contextLoads() {
		assertThat(context.getEnvironment().getProperty("spring.h2.console.enabled", Boolean.class)).isFalse();
		assertThat(context.containsBean("h2Console")).isFalse();
		assertThat(context.getBeanNamesForAnnotation(org.springframework.web.bind.annotation.RestController.class))
				.isEmpty();
	}

}
