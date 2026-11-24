package objectview.field;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Map;

/**
 * The one answer to whether a field value contains real content. Containers are
 * present only when at least one member is present; a container holding only blank
 * strings therefore cannot advertise an expansion whose body renders nothing.
 */
public final class ValuePresence {
    private ValuePresence() { }

    public static boolean isPresent(Object value) {
        if (value == null) return false;
        if (value instanceof CharSequence text) return !text.toString().isBlank();
        if (value instanceof LinkedTextSequence text) return !text.text().isBlank();
        if (value instanceof Collection<?> values) {
            return values.stream().anyMatch(ValuePresence::isPresent);
        }
        if (value instanceof Map<?, ?> values) {
            return values.values().stream().anyMatch(ValuePresence::isPresent);
        }
        if (value.getClass().isArray()) {
            for (int index = 0; index < Array.getLength(value); index++) {
                if (isPresent(Array.get(value, index))) return true;
            }
            return false;
        }
        return true;
    }
}
