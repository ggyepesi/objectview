package objectview.render;

import objectview.Viewable;
import objectview.viewconfig.ViewConfig;

import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import java.awt.Component;

/**
 * "Expand all below" / "Collapse all below" on one node (#398) — the one place their
 * menu items are made, for a reference chip and a root card's title alike. Each is an
 * explicit command that names its target (directive 10) and always has a visible
 * result: the node re-renders, and a command stopped by the limit says so.
 */
final class DisclosureCommands {

    private DisclosureCommands() { }

    /** Adds both commands for {@code target}, rendered under {@code config};
     *  {@code refresh} re-renders whatever shows it. */
    static void addTo(JPopupMenu menu, Component host, RenderContext context,
                      Viewable target, ViewConfig config, Runnable refresh) {
        if (context == null || target == null) return;
        menu.addSeparator();
        JMenuItem expand = new JMenuItem("Expand all below");
        expand.addActionListener(e -> {
            RenderContext.DisclosureCommandResult result =
                    context.expandAllBelow(target, config);
            refresh.run();
            if (result.limited()) {
                JOptionPane.showMessageDialog(host,
                        "Opened the first " + RenderContext.EXPAND_ALL_LIMIT
                                + " levels below \"" + target.getDisplayName()
                                + "\"; more remain folded. Open them where you need them.",
                        "Expand all below", JOptionPane.INFORMATION_MESSAGE);
            }
        });
        JMenuItem collapse = new JMenuItem("Collapse all below");
        collapse.addActionListener(e -> {
            context.collapseAllBelow(target, config);
            refresh.run();
        });
        menu.add(expand);
        menu.add(collapse);
    }
}
