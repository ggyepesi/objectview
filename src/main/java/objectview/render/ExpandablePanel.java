package objectview.render;

import objectview.utils.swing.GridBagUtils;

import javax.swing.*;
import java.awt.*;
import java.util.function.Supplier;

public class ExpandablePanel extends JPanel {

    private final Supplier<JComponent> collapsedHeaderSupplier;
    private final Supplier<JComponent> expandedHeaderSupplier;
    private final Supplier<JComponent> bodySupplier;

    private boolean expanded;
    private JComponent header;
    private JComponent body;

    public ExpandablePanel(
            boolean initiallyExpanded,
            Supplier<JComponent> collapsedHeaderSupplier,
            Supplier<JComponent> expandedHeaderSupplier,
            Supplier<JComponent> bodySupplier) {

        super(new GridBagLayout());

        this.expanded = initiallyExpanded;
        this.collapsedHeaderSupplier = collapsedHeaderSupplier;
        this.expandedHeaderSupplier = expandedHeaderSupplier;
        this.bodySupplier = bodySupplier;

        setOpaque(false);
        rebuildLocal();
    }

    public boolean isExpanded() {
        return expanded;
    }

    public void toggle() {
        expanded = !expanded;
        rebuildLocal();
        localRelayout();
    }

    private void rebuildLocal() {
        removeAll();

        header = expanded
                ? expandedHeaderSupplier.get()
                : collapsedHeaderSupplier.get();

        if (header != null) {
            header.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mouseClicked(java.awt.event.MouseEvent e) {
                    if (SwingUtilities.isLeftMouseButton(e)
                            && e.getClickCount() == 1
                            && !e.isShiftDown()) {
                        e.consume();
                        toggle();
                    }
                }
            });

            add(header, GridBagUtils.gbc(
                    0, 0,
                    1.0, 0.0,
                    GridBagConstraints.NORTHWEST,
                    GridBagConstraints.HORIZONTAL,
                    new Insets(0, 0, 0, 0)));
        }

        if (expanded) {
            body = bodySupplier.get();

            if (body != null) {
                add(body, GridBagUtils.gbc(
                        0, 1,
                        1.0, 0.0,
                        GridBagConstraints.NORTHWEST,
                        GridBagConstraints.HORIZONTAL,
                        new Insets(0, 16, 4, 0)));
            }
        } else {
            body = null;
        }
    }

    private void localRelayout() {
        invalidate();
        doLayout();
        repaint();

        Container p = getParent();
        while (p instanceof JComponent jc
                && !(p instanceof JViewport)) {
            jc.invalidate();
            jc.doLayout();
            jc.repaint();
            p = p.getParent();
        }

        JViewport viewport =
                (JViewport) SwingUtilities.getAncestorOfClass(
                        JViewport.class,
                        this);

        if (viewport != null) {
            viewport.revalidate();
            viewport.repaint();
        }
    }
}