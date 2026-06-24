package com.removerr.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.sqlite.SQLiteConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration
public class DataSourceConfig {

    @Value("${spring.datasource.url}")
    private String jdbcUrl;

    @Bean
    public DataSource dataSource() {
        SQLiteConfig sqliteConfig = new SQLiteConfig();
        sqliteConfig.setJournalMode(SQLiteConfig.JournalMode.WAL);
        sqliteConfig.enforceForeignKeys(true);
        sqliteConfig.setSynchronous(SQLiteConfig.SynchronousMode.NORMAL);

        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(jdbcUrl);
        hikariConfig.setMaximumPoolSize(1);       // SQLite allows only one writer
        hikariConfig.setConnectionTestQuery("SELECT 1");
        hikariConfig.setDataSourceProperties(sqliteConfig.toProperties());

        return new HikariDataSource(hikariConfig);
    }
}
