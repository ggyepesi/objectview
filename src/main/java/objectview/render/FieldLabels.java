package objectview.render;

import java.util.Locale;

/**
 * Turns a camelCase / snake_case field name into a human display label —
 * {@code "won"} → {@code "Won"}, {@code "isWinner"} → {@code "Is Winner"},
 * {@code "won_award"} → {@code "Won award"}. Shared by the card render and the
 * facet bridge so a boolean flag reads the same everywhere.
 */
public final class FieldLabels {

    private FieldLabels() {}

    public static String humanize(String field) {
        if (field == null) {
            return "";
        }
        String spaced = field.trim()
                .replaceAll("([a-z0-9])([A-Z])", "$1 $2")
                .replaceAll("[_\\-]+", " ")
                .trim();
        if (spaced.isEmpty()) {
            return "";
        }
        return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }

    /**
     * Bucket/label for a boolean flag {@code fieldName}: the humanized name when
     * truthy, {@code "Not <name>"} otherwise (e.g. {@code won} → "Won" / "Not won").
     */
    public static String booleanLabel(boolean truthy, String fieldName) {
        String name = humanize(fieldName);
        return truthy ? name : "Not " + name.toLowerCase(Locale.ROOT);
    }

    /** true/1/yes → {@link #booleanLabel} truthy; blank → null; else falsey. */
    public static String booleanBucket(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return booleanLabel(isTruthy(value), fieldName);
    }

    public static boolean isTruthy(String value) {
        if (value == null) {
            return false;
        }
        String v = value.trim();
        return v.equalsIgnoreCase("true") || v.equals("1") || v.equalsIgnoreCase("yes");
    }
}
