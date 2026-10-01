package ua.kpi.project.compatme.adapter.out.gemini;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GeminiEmbeddingAdapterTest {

    @Test
    void retryDelayMillis_readsGoogleRetryInfoDuration() {
        String message = "429 RESOURCE_EXHAUSTED {\"@type\":\"type.googleapis.com/google.rpc.RetryInfo\","
                + "\"retryDelay\":\"2s\"}";

        assertThat(GeminiEmbeddingAdapter.retryDelayMillis(message)).contains(2000L);
    }

    @Test
    void retryDelayMillis_readsFractionalHumanReadableDuration() {
        String message = "Please retry in 2.909655823s.";

        assertThat(GeminiEmbeddingAdapter.retryDelayMillis(message)).contains(2910L);
    }

    @Test
    void retryDelayMillis_returnsEmptyWhenProviderDoesNotSpecifyDelay() {
        assertThat(GeminiEmbeddingAdapter.retryDelayMillis("429 RESOURCE_EXHAUSTED")).isEmpty();
    }
}
