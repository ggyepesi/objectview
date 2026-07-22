package objectview.viewconfig;

import java.util.List;

/**
 * Discovers the immutable rows shown by {@link ViewConfigEditor}.
 *
 * <p>The source is Swing-independent, so row enumeration can be unit-tested without
 * constructing a JTable or running on the Event Dispatch Thread.
 */
@FunctionalInterface
public interface FieldRowSource {

    List<FieldRow> rows(FieldRowContext context);
}
