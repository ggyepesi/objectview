package objectview.viewconfig;

import java.util.List;

/**
 * Customizes the per-field table presented by {@link ViewConfigEditor}.
 *
 * <p>Row discovery is deliberately outside this interface. A
 * {@link FieldRowSource} produces immutable {@link FieldRow} descriptors; this
 * contributor controls how those rows are presented and interacted with:
 *
 * <ul>
 *   <li>selection mode — classic multi-check or single-row selection;</li>
 *   <li>built-in controls — Use, reorder and nested expansion;</li>
 *   <li>extra read-only columns computed from a row;</li>
 *   <li>per-row actions;</li>
 *   <li>the contributor used by nested editors.</li>
 * </ul>
 *
 * <p>{@link #DEFAULT} reproduces the historic card-config table.
 * {@link #SINGLE} is a minimal single-row picker.
 */
public interface FieldTableContributor {

    /** The classic card-config table. */
    FieldTableContributor DEFAULT = new FieldTableContributor() { };

    /** A bare single-row field picker. */
    FieldTableContributor SINGLE = new FieldTableContributor() {
        @Override
        public SelectionMode selectionMode() {
            return SelectionMode.SINGLE;
        }
    };

    /** The card-config table WITH click-to-target reorder plugged in — used by the
     *  search / sort / view config editors, where field order is meaningful. */
    FieldTableContributor REORDERABLE = new FieldTableContributor() {
        @Override
        public boolean showReorder() {
            return true;
        }
    };

    enum SelectionMode {
        MULTI_CHECK,
        SINGLE
    }

    default SelectionMode selectionMode() {
        return SelectionMode.MULTI_CHECK;
    }

    default boolean showUse() {
        return selectionMode() == SelectionMode.MULTI_CHECK;
    }

    /** Reorder is an opt-in plug-in (see {@link #REORDERABLE}); off by default so only
     *  editors where field order matters (search / sort / view) show the move targets. */
    default boolean showReorder() {
        return false;
    }

    default boolean showExpand() {
        return selectionMode() == SelectionMode.MULTI_CHECK;
    }

    /** Read-only columns inserted after Type, in order. */
    default List<ExtraColumn> columns() {
        return List.of();
    }

    /** Action buttons appended per row, in order. */
    default List<RowAction> actions() {
        return List.of();
    }

    /**
     * Contributor used by a nested editor.
     *
     * <p>The default propagates the same contributor. A root-only contributor may
     * return {@link #DEFAULT}.
     */
    default FieldTableContributor nestedContributor(FieldRow row) {
        return this;
    }

    interface ExtraColumn {
        String header();

        default int width() {
            return 80;
        }

        default Class<?> valueClass() {
            return Object.class;
        }

        Object value(FieldRow row);
    }

    interface RowAction {
        String label(FieldRow row);

        default boolean enabled(FieldRow row) {
            return true;
        }

        /**
         * Runs on Swing's Event Dispatch Thread. Long-running work must be delegated
         * to a worker or executor.
         */
        void run(FieldRow row);
    }
}
