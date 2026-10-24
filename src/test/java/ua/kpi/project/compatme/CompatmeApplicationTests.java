package ua.kpi.project.compatme;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
		"photo-storage.minio.access-key=test-access-key",
		"photo-storage.minio.secret-key=test-secret-key",
		"security.jwt-secret=test-secret-test-secret-test-secret-123",
		"security.service-key=test-service-key-1234"
})
class CompatmeApplicationTests {

	@Test
	void contextLoads() {
	}

}
