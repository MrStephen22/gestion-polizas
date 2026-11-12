package gestion_polizas;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class GestionPolizasApplication {

	public static void main(String[] args) {
		SpringApplication.run(GestionPolizasApplication.class, args);
	}

}
