package com.athanor.api;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FlywayConfiguration {

	@Bean(initMethod = "migrate")
	Flyway flyway(
		DataSource dataSource,
		@Value("${spring.datasource.url:}") String datasourceUrl
	) {
		FluentConfiguration configuration = Flyway.configure()
			.dataSource(dataSource)
			.locations("classpath:db/migration");

		// H2 test schemas can appear non-empty before Flyway records history.
		if (datasourceUrl.startsWith("jdbc:h2:")) {
			configuration.baselineOnMigrate(true);
		}

		return configuration.load();
	}
}
