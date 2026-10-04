package ua.kpi.project.compatme;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
		"photo-storage.minio.access-key=test-access-key",
		"photo-storage.minio.secret-key=test-secret-key"
})
class CompatmeApplicationTests {

	@Test
	void contextLoads() {
	}

}
