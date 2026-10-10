package ua.kpi.project.compatme.domain.model;

import java.util.List;
import java.util.Set;

/**
 * Explicit attributes a profile will not accept in a match. A missing candidate attribute remains
 * eligible because the profile does not provide enough information to establish a conflict.
 */
public record DealBreakers(
        Set<RelationshipStatus> statuses,
        Set<SmokingStatus> smokingStatuses,
        Set<DrinkingFrequency> drinkingFrequencies,
        Set<DrugUseFrequency> drugUseFrequencies,
        List<String> bodyTypes,
        List<String> diets,
        List<String> offspring,
        List<String> pets,
        List<String> religions) {

    public DealBreakers {
        statuses = immutableSet(statuses);
        smokingStatuses = immutableSet(smokingStatuses);
        drinkingFrequencies = immutableSet(drinkingFrequencies);
        drugUseFrequencies = immutableSet(drugUseFrequencies);
        bodyTypes = normalized(bodyTypes);
        diets = normalized(diets);
        offspring = normalized(offspring);
        pets = normalized(pets);
        religions = normalized(religions);
    }

    public static DealBreakers empty() {
        return new DealBreakers(Set.of(), Set.of(), Set.of(), Set.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }

    public boolean rejects(Profile candidate) {
        return rejects(statuses, candidate.status())
                || rejects(smokingStatuses, candidate.smokes())
                || rejects(drinkingFrequencies, candidate.drinks())
                || rejects(drugUseFrequencies, candidate.drugs())
                || rejects(bodyTypes, candidate.bodyType())
                || rejects(diets, candidate.diet())
                || rejects(offspring, candidate.offspring())
                || rejects(pets, candidate.pets())
                || rejects(religions, candidate.religion());
    }

    private static <T> Set<T> immutableSet(Set<T> values) {
        return values == null || values.isEmpty() ? Set.of() : Set.copyOf(values);
    }

    private static List<String> normalized(List<String> values) {
        if (values == null || values.isEmpty()) return List.of();
        return values.stream().filter(value -> value != null && !value.isBlank())
                .map(value -> value.trim().toLowerCase()).distinct().toList();
    }

    private static <T> boolean rejects(Set<T> rejected, T value) {
        return value != null && rejected.contains(value);
    }

    private static boolean rejects(List<String> rejected, String value) {
        return value != null && rejected.contains(value.trim().toLowerCase());
    }
}
