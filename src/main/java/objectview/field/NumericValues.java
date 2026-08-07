package objectview.field;

import java.util.Map;
import java.util.OptionalDouble;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The one place that reads the numeric value out of a value that may already be a
 * {@link Number} or a messy human string — {@code "300 million"}, {@code "8–13 million"},
 * {@code "1,538 K"}, {@code "~3 million"}. Everything that needs the number of a numeric
 * (ORDERED / {@code @Numeric}) field goes through here — sort, the ordering/timeline quiz,
 * filters — so they agree instead of each re-parsing differently.
 *
 * <p>Conventions: a value is scaled by a trailing magnitude word (thousand/K, million,
 * billion, trillion); a range ({@code a–b}) yields its MIDPOINT; a percent or any value
 * with no leading count (free prose, blank, null) yields {@link OptionalDouble#empty()} so
 * callers skip it rather than guess. The first numeric token wins, so a trailing note like
 * {@code "5 million (2020)"} reads as five million.
 */
public final class NumericValues {

    private NumericValues() {}

    // A number: optional sign, digits with optional thousands-commas, optional decimals.
    private static final String NUMBER =
            "-?(?:\\d{1,3}(?:,\\d{3})+|\\d+)(?:\\.\\d+)?";

    // First number, an optional range partner (en/em dash, hyphen or "to"), and an
    // optional magnitude word. Case-insensitive. The number must not be glued to a letter
    // so a use-label like "L1"/"L2" (or "iso1") does not read as a spurious 1/2.
    private static final Pattern VALUE = Pattern.compile(
            "(?<![\\p{L}])(?<a>" + NUMBER + ")"
                    + "(?:\\s*(?:[\\u2013\\u2014-]|to)\\s*(?<b>" + NUMBER + "))?"
                    + "\\s*(?<scale>trillion|billion|million|thousand|bn|k)?",
            Pattern.CASE_INSENSITIVE);

    private static final Map<String, Double> SCALE = Map.ofEntries(
            Map.entry("k", 1_000d),
            Map.entry("thousand", 1_000d),
            Map.entry("million", 1_000_000d),
            Map.entry("bn", 1_000_000_000d),
            Map.entry("billion", 1_000_000_000d),
            Map.entry("trillion", 1_000_000_000_000d));

    /** The numeric value of {@code value}, or empty when it carries no orderable count. */
    public static OptionalDouble parse(Object value) {
        if (value == null) {
            return OptionalDouble.empty();
        }
        if (value instanceof Number number) {
            return OptionalDouble.of(number.doubleValue());
        }
        String text = value.toString();
        if (text == null || text.isBlank()) {
            return OptionalDouble.empty();
        }
        // A percent (or other share) is not an absolute count — not orderable on the same
        // scale as counts, so it is skipped rather than read as its bare number.
        if (text.indexOf('%') >= 0) {
            return OptionalDouble.empty();
        }
        Matcher m = VALUE.matcher(text);
        if (!m.find()) {
            return OptionalDouble.empty();
        }
        double a = toDouble(m.group("a"));
        String b = m.group("b");
        double base = b == null ? a : (a + toDouble(b)) / 2.0;   // range -> midpoint
        return OptionalDouble.of(base * scaleOf(m.group("scale")));
    }

    private static double toDouble(String number) {
        return Double.parseDouble(number.replace(",", ""));
    }

    private static double scaleOf(String word) {
        return word == null ? 1.0
                : SCALE.getOrDefault(word.toLowerCase(java.util.Locale.ROOT), 1.0);
    }
}
