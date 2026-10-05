package com.example.AndesStay.service;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import tools.jackson.databind.json.JsonMapper;
import java.sql.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="TEST_MYSQL",matches="true")
class MySqlMigrationTests {
    @Test void freshDatabaseMigratesToLatestVersionAndCanBeMigratedAgain() throws Exception {
        String url=checkedUrl("TEST_FRESH_DB_URL");
        var flyway=flyway(url,false);
        assertEquals(6,flyway.migrate().migrationsExecuted);
        assertEquals(0,flyway.migrate().migrationsExecuted);
        assertEquals(List.of("1", "2", "3", "4", "5", "6"), Arrays.stream(flyway.info().applied()).map(m -> m.getVersion().toString()).toList());
        try(var connection=connection(url);var statement=connection.createStatement()) {
            for(String table:List.of("units","reservations","audit_events","outbox_events","consumed_events","user_profiles","reservation_notifications"))
                try(var rows=statement.executeQuery("select count(*) from "+table)){assertTrue(rows.next());assertEquals(0,rows.getInt(1));}
            connection.setAutoCommit(false);
            try {
                statement.executeUpdate("insert into user_profiles(owner_key,display_name,email,created_at) "
                    + "values('tenant:object','Ana Torres','verified@example.test','2026-10-04 12:00:00.123456')");
                assertThrows(SQLIntegrityConstraintViolationException.class, () -> statement.executeUpdate(
                    "insert into user_profiles(owner_key,display_name,created_at) values('tenant:object','Otra persona',now(6))"));
                try(var rows=statement.executeQuery("select display_name,created_at from user_profiles where owner_key='tenant:object'")) {
                    assertTrue(rows.next()); assertEquals("Ana Torres",rows.getString(1));
                    assertEquals(123456000,rows.getTimestamp(2).getNanos());
                }
            } finally { connection.rollback(); }
        }
    }
    @Test void copiedLegacyDataIsPreservedAndHistoricalConflictsAreReported() throws Exception {
        String url=checkedUrl("TEST_COPY_DB_URL");
        Map<String,Object> report=new LinkedHashMap<>();
        try(var connection=connection(url)) {
            var before=fingerprint(connection);
            assertEquals(5,flyway(url,true).migrate().migrationsExecuted);
            assertEquals(before,fingerprint(connection),"Las migraciones no deben alterar los datos originales");
            report.put("originalDataPreserved",true);
            report.put("fingerprints",before);
            var conflicts=new ArrayList<Map<String,Object>>();
            String sql="""
                select a.id,b.id,a.unit_id from reservations a join reservations b
                on a.id<b.id and a.unit_id=b.unit_id and a.check_in<b.check_out and a.check_out>b.check_in
                where a.status in ('CONFIRMADA','CHECKIN_PENDIENTE','EN_ESTADÍA')
                and b.status in ('CONFIRMADA','CHECKIN_PENDIENTE','EN_ESTADÍA')
                order by a.id,b.id
                """;
            try(var statement=connection.createStatement();var rows=statement.executeQuery(sql)) {
                while(rows.next()) conflicts.add(Map.of("reservationA",rows.getLong(1),"reservationB",rows.getLong(2),"unitId",rows.getLong(3)));
            }
            report.put("historicalOverlaps",conflicts);
            try(var statement=connection.createStatement();var rows=statement.executeQuery(
                    "select count(*) from reservations where owner_id is not null or guest_assigned=true")) {
                assertTrue(rows.next()); assertEquals(0,rows.getLong(1));
            }
            var indexes=new HashSet<String>();
            try(var rows=connection.getMetaData().getIndexInfo(connection.getCatalog(),null,"reservations",false,false)) {
                while(rows.next()) indexes.add(rows.getString("INDEX_NAME"));
            }
            assertTrue(indexes.contains("idx_reservation_unit_dates"));
            assertTrue(indexes.contains("idx_reservation_owner"));
        }
        Path file=Path.of(System.getenv("TEST_REPORT_PATH"));
        Files.writeString(file,JsonMapper.builder().build().writerWithDefaultPrettyPrinter().writeValueAsString(report),StandardCharsets.UTF_8);
    }
    private Flyway flyway(String url,boolean baseline) {
        return Flyway.configure().dataSource(url,System.getenv("TEST_DB_USERNAME"),System.getenv("TEST_DB_PASSWORD"))
            .locations("classpath:db/migration").baselineOnMigrate(baseline).baselineVersion("1").cleanDisabled(true).load();
    }
    private Connection connection(String url) throws SQLException {
        return DriverManager.getConnection(url,System.getenv("TEST_DB_USERNAME"),System.getenv("TEST_DB_PASSWORD"));
    }
    private String checkedUrl(String name) {
        String url=System.getenv(name);
        assertNotNull(url);
        assertTrue(url.matches("jdbc:mysql://localhost:[0-9]+/andesstay_test_[a-z0-9_]+\\?.*"),"Solo se permiten esquemas temporales de prueba");
        return url;
    }
    private Map<String,String> fingerprint(Connection connection) throws Exception {
        Map<String,String> results=new LinkedHashMap<>();
        Map<String,String> columns=Map.of(
            "units","id,name,type,capacity,price,available,description",
            "reservations","id,guest_name,unit_id,check_in,check_out,status",
            "audit_events","id,action,reservation_id,description,event_date,user");
        for(var entry:columns.entrySet()) {
            var digest=MessageDigest.getInstance("SHA-256");
            try(var statement=connection.createStatement();var rows=statement.executeQuery(
                    "select "+entry.getValue()+" from "+entry.getKey()+" order by id")) {
                while(rows.next()) for(int column=1;column<=rows.getMetaData().getColumnCount();column++) {
                    byte[] value=String.valueOf(rows.getObject(column)).getBytes(StandardCharsets.UTF_8);
                    digest.update(java.nio.ByteBuffer.allocate(4).putInt(value.length).array());digest.update(value);
                }
            }
            results.put(entry.getKey(),HexFormat.of().formatHex(digest.digest()));
        }
        return results;
    }
}
