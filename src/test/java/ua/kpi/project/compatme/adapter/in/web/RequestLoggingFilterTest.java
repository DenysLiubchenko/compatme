package ua.kpi.project.compatme.adapter.in.web;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RequestLoggingFilterTest {

    @Test
    void safePath_masksUuidAndNumericIdentifiers() {
        assertThat(RequestLoggingFilter.safePath(
                "/api/v1/profiles/12345678-1234-1234-1234-123456789abc/likes/987654321"))
                .isEqualTo("/api/v1/profiles/{id}/likes/{id}");
    }
}
