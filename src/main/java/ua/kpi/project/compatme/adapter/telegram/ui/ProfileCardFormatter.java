package ua.kpi.project.compatme.adapter.telegram.ui;

import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

import java.util.List;
import java.util.Map;

/** Renders backend profile/recommendation maps into {@link Card}s. */
public final class ProfileCardFormatter {

    private ProfileCardFormatter() {
    }

    public static String formatProfileCard(Map<String, Object> profile, boolean includePreference) {
        Object age = profile.get("age");
        StringBuilder sb = new StringBuilder();
        sb.append("👤 %s, %s\n".formatted(profile.get("displayName"), age));
        String city = (String) profile.get("city");
        String country = (String) profile.get("country");
        if (city != null || country != null) {
            sb.append("📍 %s, %s\n".formatted(city, country));
        }
        sb.append("\n\"%s\"".formatted(profile.get("selfDescription")));
        if (includePreference && profile.get("preferenceDescription") != null) {
            sb.append("\n\nLooking for: \"%s\"".formatted(profile.get("preferenceDescription")));
        }
        return sb.toString();
    }

    public static Card toCard(Map<String, Object> profile, boolean includePreference, InlineKeyboardMarkup keyboard) {
        String text = formatProfileCard(profile, includePreference);
        String photoUrn = firstPhotoUrn(profile);
        Object id = profile.get("id") != null ? profile.get("id") : profile.get("candidateId");
        return new Card(text, id == null ? null : String.valueOf(id), photoUrn, keyboard);
    }

    public static String firstPhotoUrn(Map<String, Object> profile) {
        if (profile.get("photoUrns") instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof String value && !value.isBlank()) {
                    return value;
                }
            }
        }
        return null;
    }

    public static String formatRecommendations(List<Map<String, Object>> recommendations) {
        if (recommendations.isEmpty()) {
            return "No matches available right now.";
        }
        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> r : recommendations) {
            sb.append("• %s (score: %.2f)\n".formatted(r.get("displayName"), ((Number) r.get("aggregatedScore")).doubleValue()));
        }
        return sb.toString().stripTrailing();
    }
}
