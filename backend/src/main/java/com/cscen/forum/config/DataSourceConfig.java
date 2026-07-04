package com.cscen.forum.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;

import javax.sql.DataSource;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * Accepts DATABASE_URL in the postgres://user:pass@host:port/db form used by
 * Railway, Heroku, and the original Node backend, and converts it to JDBC.
 */
@Configuration
public class DataSourceConfig {

    @Bean
    @Primary
    public DataSource dataSource(Environment env) {
        String raw = env.getProperty("DATABASE_URL", "postgresql://forum:forum@localhost:5432/forum");

        if (raw.startsWith("jdbc:")) {
            return DataSourceBuilder.create()
                    .type(HikariDataSource.class)
                    .url(raw)
                    .username(env.getProperty("DB_USER", ""))
                    .password(env.getProperty("DB_PASSWORD", ""))
                    .build();
        }

        URI uri = URI.create(raw.replaceFirst("^postgres://", "postgresql://"));
        int port = uri.getPort() == -1 ? 5432 : uri.getPort();
        String jdbcUrl = "jdbc:postgresql://" + uri.getHost() + ":" + port + uri.getPath()
                + (uri.getQuery() != null ? "?" + uri.getQuery() : "");

        String username = "";
        String password = "";
        if (uri.getUserInfo() != null) {
            String[] parts = uri.getUserInfo().split(":", 2);
            username = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
            password = parts.length > 1 ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : "";
        }

        return DataSourceBuilder.create()
                .type(HikariDataSource.class)
                .url(jdbcUrl)
                .username(username)
                .password(password)
                .build();
    }
}
