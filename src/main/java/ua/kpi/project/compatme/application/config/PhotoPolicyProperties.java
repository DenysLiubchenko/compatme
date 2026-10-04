package ua.kpi.project.compatme.application.config;

import java.util.List;

/**
 * Photo acceptance rules. Plain record (no Spring annotations); bound from {@code photo-storage.*}
 * in the {@code config} package.
 */
public record PhotoPolicyProperties(long maxSizeBytes, List<String> allowedContentTypes, int maxPhotosPerProfile) {

    public PhotoPolicyProperties {
        allowedContentTypes = allowedContentTypes == null ? List.of() : List.copyOf(allowedContentTypes);
    }
}
