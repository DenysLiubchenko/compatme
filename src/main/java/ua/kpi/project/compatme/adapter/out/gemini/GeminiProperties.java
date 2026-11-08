package ua.kpi.project.compatme.adapter.out.gemini;


/**
 * Externalized configuration for the Gemini API adapter. Bound from {@code application.yml}
 * under the {@code gemini} prefix (binding declared on the {@code geminiProperties} @Bean factory
 * method in {@code config.GeminiClientConfig}, not here — see that class's Javadoc for why).
 * Model names are never hardcoded in adapter code, since they are expected to change over time as
 * Google releases newer models.
 */
public class GeminiProperties {

    /** Gemini Developer API key. Must be supplied via environment variable, never hardcoded. */
    private String apiKey;

    /** Embedding model name, e.g. {@code gemini-embedding-001}. */
    private String embeddingModel = "gemini-embedding-001";

    /** Output vector dimensionality requested from the embedding model. */
    private int embeddingDimensionality = 768;

    /** Chat/generation model name, e.g. {@code gemini-2.5-flash}. */
    private String chatModel = "gemini-2.5-flash";

    /** Maximum number of attempts (including the first) for retryable Gemini API calls. */
    private int maxRetryAttempts = 3;

    /**
     * Attempts for the interactive chat call. Kept at 1 by default: a user is waiting on the answer,
     * and a failure is handled by a graceful fallback rather than by retrying.
     */
    private int chatMaxRetryAttempts = 1;

    public int getChatMaxRetryAttempts() {
        return chatMaxRetryAttempts;
    }

    public void setChatMaxRetryAttempts(int chatMaxRetryAttempts) {
        this.chatMaxRetryAttempts = chatMaxRetryAttempts;
    }

    /** Initial backoff delay in milliseconds before the first retry. */
    private long retryInitialBackoffMillis = 1000L;

    /** Multiplier applied to the backoff delay after each retry attempt. */
    private double retryBackoffMultiplier = 2.0;

    /**
     * Per-request HTTP timeout in milliseconds. Without it a stalled Gemini response blocks the
     * calling request thread indefinitely (the bot then appears frozen).
     */
    private int requestTimeoutMillis = 60000;

    public int getRequestTimeoutMillis() {
        return requestTimeoutMillis;
    }

    public void setRequestTimeoutMillis(int requestTimeoutMillis) {
        this.requestTimeoutMillis = requestTimeoutMillis;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getEmbeddingModel() {
        return embeddingModel;
    }

    public void setEmbeddingModel(String embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public int getEmbeddingDimensionality() {
        return embeddingDimensionality;
    }

    public void setEmbeddingDimensionality(int embeddingDimensionality) {
        this.embeddingDimensionality = embeddingDimensionality;
    }

    public String getChatModel() {
        return chatModel;
    }

    public void setChatModel(String chatModel) {
        this.chatModel = chatModel;
    }

    public int getMaxRetryAttempts() {
        return maxRetryAttempts;
    }

    public void setMaxRetryAttempts(int maxRetryAttempts) {
        this.maxRetryAttempts = maxRetryAttempts;
    }

    public long getRetryInitialBackoffMillis() {
        return retryInitialBackoffMillis;
    }

    public void setRetryInitialBackoffMillis(long retryInitialBackoffMillis) {
        this.retryInitialBackoffMillis = retryInitialBackoffMillis;
    }

    public double getRetryBackoffMultiplier() {
        return retryBackoffMultiplier;
    }

    public void setRetryBackoffMultiplier(double retryBackoffMultiplier) {
        this.retryBackoffMultiplier = retryBackoffMultiplier;
    }
}
