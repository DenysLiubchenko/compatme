package ua.kpi.project.compatme.application.port.out;

import ua.kpi.project.compatme.domain.model.GroundTruthPair;

import java.util.List;

/**
 * Outbound port for storing/retrieving the thesis evaluation's ground-truth dataset (pairs of
 * profile ids with an expected reciprocal-compatibility label). Mirrors the
 * {@link ProfileRepositoryPort} pattern: a plain-Java interface implemented by a MongoDB adapter
 * in {@code adapter.out.persistence}, so the application layer never depends on Spring Data.
 */
public interface GroundTruthPairRepositoryPort {

    /** Replaces the entire stored dataset with {@code pairs} (used by the import loader). */
    void replaceAll(List<GroundTruthPair> pairs);

    List<GroundTruthPair> findAll();
}
