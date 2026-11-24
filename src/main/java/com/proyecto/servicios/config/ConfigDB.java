package com.proyecto.servicios.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.core.env.Environment;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(
        basePackages = {
                "com.proyecto.servicios.repositorys"
        },
        transactionManagerRef = "sfTransactionManager",
        entityManagerFactoryRef = "sfEntityManagerFactory"
)
public class ConfigDB {

    private static final String SF_DATASOURCE = "sfDatasource";
    private static final long MIN_MAX_LIFETIME_MS = 30_000L;

    @Bean(name = SF_DATASOURCE)
    public DataSource sfDatasource(Environment env) {
        String jdbcUrl = env.getProperty("spring.datasource.url");
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            throw new IllegalStateException("La propiedad spring.datasource.url es obligatoria para iniciar HikariCP");
        }

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(env.getProperty("spring.datasource.username"));
        config.setPassword(env.getProperty("spring.datasource.password"));
        config.setMaximumPoolSize(env.getProperty("spring.datasource.hikari.maximum-pool-size", Integer.class, 3));
        config.setMinimumIdle(env.getProperty("spring.datasource.hikari.minimum-idle", Integer.class, 0));
        config.setConnectionTimeout(env.getProperty("spring.datasource.hikari.connection-timeout", Long.class, 30_000L));
        config.setValidationTimeout(env.getProperty("spring.datasource.hikari.validation-timeout", Long.class, 5_000L));
        config.setIdleTimeout(env.getProperty("spring.datasource.hikari.idle-timeout", Long.class, 30_000L));
        config.setKeepaliveTime(env.getProperty("spring.datasource.hikari.keepalive-time", Long.class, 20_000L));
        long configuredMaxLifetime = env.getProperty("spring.datasource.hikari.max-lifetime", Long.class, 60_000L);
        config.setMaxLifetime(Math.max(MIN_MAX_LIFETIME_MS, configuredMaxLifetime));
        config.addDataSourceProperty(
                "prepareThreshold",
                env.getProperty("spring.datasource.hikari.data-source-properties.prepareThreshold", "0"));
        config.addDataSourceProperty(
                "preparedStatementCacheQueries",
                env.getProperty("spring.datasource.hikari.data-source-properties.preparedStatementCacheQueries", "0"));
        config.setConnectionTestQuery("SELECT 1");
        config.setPoolName(SF_DATASOURCE);
        return new HikariDataSource(config);
    }

    @Bean(name = "sfEntityManagerFactory")
    @DependsOn("flyway")
    public LocalContainerEntityManagerFactoryBean sfEntityManagerFactory(
            @Qualifier(SF_DATASOURCE) DataSource dataSource,
            Environment env) {
        LocalContainerEntityManagerFactoryBean em = new LocalContainerEntityManagerFactoryBean();
        em.setDataSource(dataSource);
        em.setPackagesToScan("com.proyecto.servicios.entity");
        em.setPersistenceUnitName(SF_DATASOURCE);
        em.setJpaVendorAdapter(new HibernateJpaVendorAdapter());

        Map<String, Object> properties = new HashMap<>();
        properties.put("hibernate.hbm2ddl.auto", "none");
        properties.put("hibernate.show_sql", env.getProperty("spring.jpa.show-sql", Boolean.class, Boolean.FALSE));
        properties.put(
                "jakarta.persistence.query.timeout",
                env.getProperty("spring.jpa.properties.jakarta.persistence.query.timeout", Integer.class, 600_000));
        em.setJpaPropertyMap(properties);
        return em;
    }

    @Bean(name = "sfTransactionManager")
    public PlatformTransactionManager sfTransactionManager(
            @Qualifier("sfEntityManagerFactory") EntityManagerFactory sfEntityManagerFactory) {
        return new JpaTransactionManager(sfEntityManagerFactory);
    }
}
