package objectview.utils.swing;

import javax.swing.Box;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.GridBagConstraints;
import java.awt.Insets;

public final class GridBagUtils {
    private GridBagUtils() {}

    /** The default padding for a form row's cells. */
    private static final Insets ROW_INSETS = new Insets(4, 4, 4, 4);

    /** Padding around a card in a vertical card stack. */
    private static final Insets CARD_INSETS = new Insets(3, 3, 3, 3);

    /**
     * Constraints for a full-width cell spanning {@code gridwidth} columns, with
     * the standard form padding, left-aligned and stretched horizontally. The
     * common label/field-form default — the shorthand the workbench panels reuse.
     */
    public static GridBagConstraints row(int gridx, int gridy, int gridwidth) {
        return spanning(
                gridx,
                gridy,
                gridwidth,
                1.0,
                0.0,
                GridBagConstraints.WEST,
                GridBagConstraints.HORIZONTAL,
                ROW_INSETS);
    }

    /** A two-cell form row with the standard padding (insets 4, WEST, HORIZONTAL)
     *  — the common case that needs no custom template. */
    public static void labeledRow(
            JPanel form,
            int gridy,
            String label,
            JComponent field) {
        labeledRow(form, defaultRowTemplate(), gridy, new JLabel(label), field);
    }

    /** {@link #labeledRow(JPanel, int, String, JComponent)} with a pre-built label. */
    public static void labeledRow(
            JPanel form,
            int gridy,
            JLabel label,
            JComponent field) {
        labeledRow(form, defaultRowTemplate(), gridy, label, field);
    }

    private static GridBagConstraints defaultRowTemplate() {
        GridBagConstraints template = new GridBagConstraints();
        template.insets = ROW_INSETS;
        template.anchor = GridBagConstraints.WEST;
        template.fill = GridBagConstraints.HORIZONTAL;
        return template;
    }

    /**
     * Adds a two-cell form row: a label in column 0 (no horizontal weight) and a
     * field in column 1 (takes the slack). The row inherits the {@code template}'s
     * insets/anchor/fill, so callers control padding and alignment per form. Each
     * cell gets its own cloned constraints, so the template is left untouched.
     */
    public static void labeledRow(
            JPanel form,
            GridBagConstraints template,
            int gridy,
            String label,
            JComponent field) {
        labeledRow(form, template, gridy, new JLabel(label), field);
    }

    /** {@link #labeledRow(JPanel, GridBagConstraints, int, String, JComponent)}
     *  with a pre-built label (e.g. one whose text changes at runtime). */
    public static void labeledRow(
            JPanel form,
            GridBagConstraints template,
            int gridy,
            JLabel label,
            JComponent field) {

        GridBagConstraints labelCell = (GridBagConstraints) template.clone();
        labelCell.gridx = 0;
        labelCell.gridy = gridy;
        labelCell.gridwidth = 1;
        labelCell.weightx = 0;
        form.add(label, labelCell);

        GridBagConstraints fieldCell = (GridBagConstraints) template.clone();
        fieldCell.gridx = 1;
        fieldCell.gridy = gridy;
        fieldCell.gridwidth = 1;
        fieldCell.weightx = 1.0;
        form.add(field, fieldCell);
    }

    /** Adds a component spanning both form columns, with the standard row padding
     *  and horizontal stretch (see {@link #row(int, int, int)}). */
    public static void wideRow(JPanel form, int gridy, JComponent component) {
        form.add(component, row(0, gridy, 2));
    }

    /**
     * Adds a full-width card to a single-column vertical stack: left-aligned at the
     * top of its cell, stretched horizontally, with the standard card padding. Pair
     * with a trailing {@link #verticalGlue} so the cards pack to the top.
     */
    public static void stackedCard(JPanel column, int gridy, JComponent card) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = gridy;
        c.weightx = 1.0;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.NORTHWEST;
        c.insets = CARD_INSETS;
        column.add(card, c);
    }

    /**
     * Adds a trailing glue cell that absorbs the leftover vertical space, pushing
     * the preceding rows to the top of {@code column}. The last thing added to a
     * stacked layout.
     */
    public static void verticalGlue(JPanel column, int gridy) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = gridy;
        c.weightx = 1.0;
        c.weighty = 1.0;
        c.fill = GridBagConstraints.BOTH;
        column.add(Box.createGlue(), c);
    }

    /**
     * Constraints for a single cell. Named for what its numbers MEAN: every parameter
     * here is a number, and an {@code int} widens to a {@code double}, so an argument
     * list intended for {@link #spanning} type-checks perfectly against a weight-first
     * signature — a gridwidth silently becomes a weight, and the weight after it an
     * anchor. AWT then reports "illegal anchor value" from the layout pass, nowhere
     * near the call. Distinct names are what make that mistake fail to compile.
     */
    public static GridBagConstraints weighted(
            int gridx,
            int gridy,
            double weightx,
            double weighty,
            int anchor,
            int fill) {

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = gridx;
        gbc.gridy = gridy;
        gbc.weightx = weightx;
        gbc.weighty = weighty;
        gbc.anchor = requireAnchor(anchor);
        gbc.fill = requireFill(fill);
        return gbc;
    }

    /** {@link #weighted(int, int, double, double, int, int)} with explicit padding. */
    public static GridBagConstraints weighted(
            int gridx,
            int gridy,
            double weightx,
            double weighty,
            int anchor,
            int fill,
            Insets insets) {

        GridBagConstraints gbc = weighted(gridx, gridy, weightx, weighty, anchor, fill);
        gbc.insets = insets;
        return gbc;
    }

    /** A cell spanning {@code gridwidth} columns. */
    public static GridBagConstraints spanning(
            int gridx,
            int gridy,
            int gridwidth,
            double weightx,
            double weighty,
            int anchor,
            int fill,
            Insets insets) {

        GridBagConstraints gbc = weighted(
                gridx,
                gridy,
                weightx,
                weighty,
                anchor,
                fill,
                insets);

        gbc.gridwidth = gridwidth;

        return gbc;
    }

    /** A cell spanning {@code gridwidth} columns and {@code gridheight} rows. */
    public static GridBagConstraints spanning(
            int gridx,
            int gridy,
            int gridwidth,
            int gridheight,
            double weightx,
            double weighty,
            int anchor,
            int fill,
            Insets insets) {

        GridBagConstraints gbc = spanning(gridx, gridy, gridwidth, weightx, weighty,
                                     anchor, fill, insets);
        gbc.gridheight = gridheight;
        return gbc;
    }

    private static final java.util.Set<Integer> ANCHORS = java.util.Set.of(
            GridBagConstraints.CENTER, GridBagConstraints.NORTH,
            GridBagConstraints.NORTHEAST, GridBagConstraints.EAST,
            GridBagConstraints.SOUTHEAST, GridBagConstraints.SOUTH,
            GridBagConstraints.SOUTHWEST, GridBagConstraints.WEST,
            GridBagConstraints.NORTHWEST, GridBagConstraints.PAGE_START,
            GridBagConstraints.PAGE_END, GridBagConstraints.LINE_START,
            GridBagConstraints.LINE_END, GridBagConstraints.FIRST_LINE_START,
            GridBagConstraints.FIRST_LINE_END, GridBagConstraints.LAST_LINE_START,
            GridBagConstraints.LAST_LINE_END, GridBagConstraints.BASELINE,
            GridBagConstraints.BASELINE_LEADING, GridBagConstraints.BASELINE_TRAILING,
            GridBagConstraints.ABOVE_BASELINE, GridBagConstraints.ABOVE_BASELINE_LEADING,
            GridBagConstraints.ABOVE_BASELINE_TRAILING, GridBagConstraints.BELOW_BASELINE,
            GridBagConstraints.BELOW_BASELINE_LEADING,
            GridBagConstraints.BELOW_BASELINE_TRAILING);

    /** Rejects a non-anchor HERE, in the caller's own stack. GridBagLayout accepts any
     *  int until it lays the container out, and then throws on the EDT with nothing to
     *  say about where the value came from. */
    private static int requireAnchor(int anchor) {
        if (!ANCHORS.contains(anchor)) {
            throw new IllegalArgumentException(anchor + " is not a GridBagConstraints "
                    + "anchor (WEST, NORTHWEST, CENTER, …). A gridwidth or a weight "
                    + "passed in this position is the usual cause — see spanning().");
        }
        return anchor;
    }

    private static int requireFill(int fill) {
        if (fill != GridBagConstraints.NONE && fill != GridBagConstraints.BOTH
                && fill != GridBagConstraints.HORIZONTAL
                && fill != GridBagConstraints.VERTICAL) {
            throw new IllegalArgumentException(fill + " is not a GridBagConstraints fill "
                    + "(NONE, BOTH, HORIZONTAL, VERTICAL).");
        }
        return fill;
    }
}