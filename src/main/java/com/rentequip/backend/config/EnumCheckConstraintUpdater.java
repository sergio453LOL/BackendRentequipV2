package com.rentequip.backend.config;

import com.rentequip.backend.enums.EquipmentStatus;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Hibernate writes a CHECK constraint listing an enum column's values when it creates the table, and
 * `ddl-auto=update` never rewrites it. When an enum gains a value (EquipmentStatus.SOLD), existing
 * PostgreSQL databases would refuse it, so the constraint is rebuilt from the enum at startup. It is
 * idempotent and does nothing on other databases (the tests' H2 is created fresh every run).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class EnumCheckConstraintUpdater implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(EnumCheckConstraintUpdater.class);

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            if (!"PostgreSQL".equalsIgnoreCase(connection.getMetaData().getDatabaseProductName())) {
                return;
            }
        }
        rebuild("equipment", "status", EquipmentStatus.class);
    }

    private void rebuild(String table, String column, Class<? extends Enum<?>> type) {
        String constraint = table + "_" + column + "_check";
        String values = Arrays.stream(type.getEnumConstants())
                .map(constant -> "'" + constant.name() + "'")
                .collect(Collectors.joining(", "));
        jdbcTemplate.execute("ALTER TABLE " + table + " DROP CONSTRAINT IF EXISTS " + constraint);
        jdbcTemplate.execute("ALTER TABLE " + table + " ADD CONSTRAINT " + constraint
                + " CHECK (" + column + " IN (" + values + "))");
        log.info("Rebuilt {} with {}", constraint, values);
    }
}
