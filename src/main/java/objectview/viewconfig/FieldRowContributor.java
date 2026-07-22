package objectview.viewconfig;

import java.util.List;

/**
 * A plugin that reshapes the per-field config table ({@link ViewConfigEditor}) without
 * the callers each reinventing a field list. It governs four things, all keyed by a
 * field's dotted path so it stays generic:
 *
 * <ul>
 *   <li>the <b>selection mode</b> — the classic multi-check (with per-field {@code Use}
 *       boxes) or single-select (pick ONE field, e.g. a pipeline argument);</li>
 *   <li>which <b>built-in affordances</b> show — {@code Use}, the {@code Up}/{@code Down}
 *       reorder chips, the {@code Expand} nested-config chip;</li>
 *   <li>extra read-only <b>columns</b> after {@code Type} — e.g. Validation's
 *       {@code Coverage} / {@code Present} / {@code Missing};</li>
 *   <li>per-row <b>actions</b> (buttons) — e.g. Validation's "Check DBpedia".</li>
 * </ul>
 *
 * <p>{@link #DEFAULT} reproduces the historic card-config table exactly (multi-check +
 * up/down + expand, no extra columns or actions), so the many existing callers are
 * untouched. This is the seam that let the single-select {@code FieldTreePanel} and the
 * bespoke Validation table fold back into this one component.
 */
public interface FieldRowContributor {

    /** The classic card-config table: multi-check, reorder, expand; nothing extra. */
    FieldRowContributor DEFAULT = new FieldRowContributor() { };

    /** A bare single-select field picker: pick ONE (possibly nested) field, no
     *  checkboxes / reorder / expand / extra columns (the folded-in FieldTreePanel). */
    FieldRowContributor SINGLE = new FieldRowContributor() {
        @Override public SelectionMode selectionMode() {
            return SelectionMode.SINGLE;
        }
    };

    enum SelectionMode { MULTI_CHECK, SINGLE }

    default SelectionMode selectionMode() {
        return SelectionMode.MULTI_CHECK;
    }

    /** The per-field include checkbox — meaningful only for multi-check. */
    default boolean showUse() {
        return selectionMode() == SelectionMode.MULTI_CHECK;
    }

    /** The Up/Down reorder chips — meaningful only for multi-check. */
    default boolean showReorder() {
        return selectionMode() == SelectionMode.MULTI_CHECK;
    }

    /** The Expand nested-config chip. Off for the flat path-row source (already
     *  expanded) and for pickers that don't drill. */
    default boolean showExpand() {
        return selectionMode() == SelectionMode.MULTI_CHECK;
    }

    /** Read-only columns inserted after {@code Type}, in order. */
    default List<ExtraColumn> columns() {
        return List.of();
    }

    /** Action buttons appended per row, in order. */
    default List<RowAction> actions() {
        return List.of();
    }

    /** A read-only column whose cell value is computed from a field's dotted path. */
    interface ExtraColumn {
        String header();

        default int width() {
            return 80;
        }

        Object value(String fieldPath);
    }

    /** A per-row button: its label (may vary per row), whether it's live, and what it
     *  does — all keyed by the row's dotted path. */
    interface RowAction {
        String label(String fieldPath);

        default boolean enabled(String fieldPath) {
            return true;
        }

        void run(String fieldPath);
    }
}
