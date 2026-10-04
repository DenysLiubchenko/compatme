package ua.kpi.project.compatme.domain.service;

import ua.kpi.project.compatme.domain.model.Location;
import ua.kpi.project.compatme.domain.model.LocationScope;

import java.util.Objects;

/**
 * Framework-agnostic location match predicate. Comparison is trimmed and case-insensitive exact
 * match; no fuzzy matching.
 */
public final class LocationMatcher {

    private LocationMatcher() { }

    public static boolean matches(Location requester, Location candidate, LocationScope scope) {
        Objects.requireNonNull(scope, "scope must not be null");
        return switch (scope) {
            case WORLDWIDE -> true;
            case COUNTRY -> sameText(requester.country(), candidate.country());
            case CITY -> sameText(requester.country(), candidate.country())
                    && sameText(requester.city(), candidate.city());
        };
    }

    private static boolean sameText(String a, String b) {
        return a.trim().equalsIgnoreCase(b.trim());
    }
}
