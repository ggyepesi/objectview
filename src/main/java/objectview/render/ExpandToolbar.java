package objectview.render;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import java.awt.FlowLayout;

/**
 * A small toolbar that drives a {@link RenderContext}'s bulk expand/collapse:
 * <b>Expand all</b> / <b>Collapse all</b>, each honoring a <b>Recursive</b>
 * checkbox.
 *
 * <ul>
 *   <li>Unchecked ("this level") flips the cards themselves — birdseye cards
 *       open to their full fields (see {@link RenderContext#setCollapsibleCards}).</li>
 *   <li>Checked ("recursive") also drills into nested references and
 *       collections, all the way down.</li>
 * </ul>
 *
 * <p>After setting the mode it runs the supplied {@code refresh} so
 * already-built cards update at once; cards built later (e.g. scrolled into a
 * virtualized list) follow the mode on their own. Standalone — a view adds it
 * wherever it likes, with or without a search bar.
 */
public final class ExpandToolbar extends JPanel {

    /**
     * @param context the render context whose bulk state is toggled
     * @param refresh  re-renders the currently-built cards (e.g.
     *                 {@code cardListView::refreshBuiltCards}); may be null
     */
    public ExpandToolbar(RenderContext context, Runnable refresh) {
        super(new FlowLayout(FlowLayout.LEFT, 6, 2));

        JCheckBox recursive = new JCheckBox("Recursive", false);
        recursive.setToolTipText(
                "Also expand/collapse nested references and collections");

        JButton expand = new JButton("Expand all");
        JButton collapse = new JButton("Collapse all");

        expand.addActionListener(e ->
                apply(context, RenderContext.BulkExpand.EXPAND,
                        recursive.isSelected(), refresh));
        collapse.addActionListener(e ->
                apply(context, RenderContext.BulkExpand.COLLAPSE,
                        recursive.isSelected(), refresh));

        add(expand);
        add(collapse);
        add(recursive);
    }

    private static void apply(RenderContext context,
                              RenderContext.BulkExpand mode,
                              boolean recursive,
                              Runnable refresh) {
        context.setBulkExpand(mode, recursive);
        if (refresh != null) {
            refresh.run();
        }
    }
}
