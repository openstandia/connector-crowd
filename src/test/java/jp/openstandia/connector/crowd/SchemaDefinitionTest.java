package jp.openstandia.connector.crowd;

import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class SchemaDefinitionTest {

    @Test
    void schema() {
        ZonedDateTime dateTime = ZonedDateTime.parse("2022-06-01T15:00:25.167+09:00", DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        assertNotNull(dateTime);
    }
}
