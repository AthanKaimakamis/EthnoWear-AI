package fmi.ethnowear.config;

import java.nio.file.Path;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DataSourceConfig {

    private static final String SQLITE_PREFIX = "jdbc:sqlite:";

    @Bean
    public static BeanPostProcessor sqliteDataSourcePathResolver() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
                if (bean instanceof HikariDataSource dataSource) {
                    dataSource.setJdbcUrl(resolveSqliteUrl(dataSource.getJdbcUrl()));
                }
                return bean;
            }
        };
    }

    private static String resolveSqliteUrl(String jdbcUrl) {
        if (jdbcUrl == null || !jdbcUrl.startsWith(SQLITE_PREFIX)) {
            return jdbcUrl;
        }

        String databasePath = jdbcUrl.substring(SQLITE_PREFIX.length());
        if (databasePath.isBlank()
                || databasePath.startsWith(":")
                || databasePath.startsWith("file:")) {
            return jdbcUrl;
        }

        Path path = Path.of(databasePath);
        if (path.isAbsolute()) {
            return jdbcUrl;
        }

        return SQLITE_PREFIX + ProjectPathResolver.resolve(path);
    }
}
