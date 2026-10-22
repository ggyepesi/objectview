package objectview.field;

/**
 * Parsed form of the ordinary linked-text field value accepted by
 * {@link objectview.annotations.Link}: either {@code URL} or {@code label|URL}.
 * Body rows and DISPLAY captions use this same parser.
 */
public record LinkedText(String label, String url) {

    public static LinkedText parse(Object rawValue, String annotationText) {
        String value = rawValue == null ? "" : String.valueOf(rawValue).trim();
        String valueLabel = null;
        int bar = value.indexOf('|');
        if (bar > 0 && bar < value.length() - 1) {
            valueLabel = value.substring(0, bar).trim();
            value = value.substring(bar + 1).trim();
        }
        String label = annotationText != null && !annotationText.isBlank()
                ? annotationText.trim()
                : valueLabel != null && !valueLabel.isBlank() ? valueLabel : value;
        return new LinkedText(label, value);
    }
}
