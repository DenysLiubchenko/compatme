package ua.kpi.project.compatme.adapter.telegram.ui;

import java.util.List;
import java.util.Set;

/** Pure text helpers shared by the Telegram flows. */
public final class Labels {

    private Labels() {
    }

    public static String humanize(String genderEnumName) {
        if (genderEnumName == null) {
            return "";
        }
        return switch (genderEnumName) {
            case "MALE" -> "Male";
            case "FEMALE" -> "Female";
            case "NON_BINARY" -> "Non-binary";
            default -> genderEnumName;
        };
    }

    public static String joinHumanized(Set<String> genders) {
        return genders.stream().map(Labels::humanize).reduce((a, b) -> a + ", " + b).orElse("anyone");
    }

    public static boolean isValidScope(String scope) {
        return "CITY".equals(scope) || "COUNTRY".equals(scope) || "WORLDWIDE".equals(scope);
    }

    public static String humanizeScope(String scope) {
        return switch (scope == null ? "" : scope) {
            case "CITY" -> "My city";
            case "COUNTRY" -> "My country";
            default -> "Worldwide";
        };
    }

    public static String numbered(List<String> values) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            result.append(i + 1).append(". ").append(values.get(i));
            if (i + 1 < values.size()) {
                result.append('\n');
            }
        }
        return result.toString();
    }
}
