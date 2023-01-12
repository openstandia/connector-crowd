package jp.openstandia.connector.crowd;

import org.identityconnectors.framework.common.exceptions.ConnectorIOException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class CrowdRESTClientTest {

    @Test
    void testError() {
        // Given
        CrowdRESTClient client = new CrowdRESTClient("test", null, null);

        // Then
        assertThrows(ConnectorIOException.class, () -> {
            // When
            client.test();
        });
    }
}