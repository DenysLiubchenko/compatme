package ua.kpi.project.compatme.application.port.out;

import ua.kpi.project.compatme.domain.model.EmbeddingVector;

/**
 * Outbound port for computing text embeddings. Implemented by {@code adapter.out.gemini} today
 * (backed by the {@code gemini-embedding-001} model via the Google Gen AI SDK). Swapping to a
 * different embedding provider only requires a new adapter implementing this port — nothing in
 * the domain or application layer references the Gemini SDK.
 */
public interface EmbeddingProviderPort {

    /**
     * Computes an embedding for the given text.
     *
     * @param text arbitrary UTF-8 text (may be Ukrainian, or any other language)
     * @return the resulting vector, tagged with the model name and dimensionality used
     */
    EmbeddingVector embed(String text);

    /** Name of the model this adapter is currently configured to use, e.g. {@code gemini-embedding-001}. */
    String modelName();
}
