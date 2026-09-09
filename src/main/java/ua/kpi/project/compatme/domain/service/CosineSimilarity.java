package ua.kpi.project.compatme.domain.service;

/**
 * Pure cosine-similarity math. No framework or I/O dependencies — trivially unit-testable and
 * reusable regardless of which embedding provider produced the vectors.
 */
public final class CosineSimilarity {

    private CosineSimilarity() {
    }

    /**
     * @return cosine similarity in {@code [-1.0, 1.0]}; {@code 0.0} if either vector has zero
     *     magnitude (avoids division by zero for degenerate/empty embeddings).
     */
    public static double compute(float[] a, float[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException(
                    "Vectors must have the same dimensionality: %d vs %d".formatted(a.length, b.length));
        }
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < a.length; i++) {
            dotProduct += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0.0 || normB == 0.0) {
            return 0.0;
        }
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
