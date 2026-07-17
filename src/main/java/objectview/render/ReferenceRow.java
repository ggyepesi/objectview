package objectview.render;

import objectview.demo.CardFrame;
import objectview.search.SearchPanel;
import objectview.Viewable;
import objectview.viewconfig.ViewConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * A reference to another {@link Viewable}, rendered on top of {@link TextRow}
 * (inheriting selection, search highlight, wrapping, keyboard + popup copy) with a
 * leading expand/collapse triangle. Left-click toggles in-place expansion — or jumps
 * to the target's card when it's itself a top-level card ({@code navigate}); the copy
 * menu can open it in its own window.
 *
 * <p>Collapsed, it's just a painted text row sharing the static mouse handler — no
 * per-instance listeners, tooltips, or component baggage — so a card full of
 * references stays cheap to build. It only becomes a real nested panel once expanded
 * (handled by {@link Card}), which is the rare case.
 */
public class ReferenceRow extends TextRow {

    private static final Logger log = LoggerFactory.getLogger(ReferenceRow.class);


    private static final int TRI_W = 12;
    private static final Color VALUE_COLOR = new Color(0, 80, 180);
    private static final Color TRI_COLOR = new Color(120, 120, 120);

    private final Viewable target;
    private final RenderContext renderContext;
    private final ViewConfig openConfig;
    private final String openTitle;
    private final boolean expanded;
    // When the target is itself a top-level card, the row navigates to it rather
    // than expanding in place (avoids the per-target expand flag being shared
    // between the card and a chip for the same object).
    private final boolean navigate;

    public ReferenceRow(String fieldName,
                        List<String> fieldPath,
                        Viewable target,
                        RenderContext renderContext,
                        ViewConfig openConfig,
                        String openTitle,
                        boolean expanded) {
        this(fieldName, fieldPath, target, renderContext, openConfig,
                openTitle, expanded, false);
    }

    public ReferenceRow(String fieldName,
                        List<String> fieldPath,
                        Viewable target,
                        RenderContext renderContext,
                        ViewConfig openConfig,
                        String openTitle,
                        boolean expanded,
                        boolean navigate) {
        super(fieldName,
                fieldPath == null ? List.of() : new ArrayList<>(fieldPath),
                List.of(name(target)));
        Card.RenderStats.referenceRows++;

        this.target = target;
        this.renderContext = renderContext;
        this.openConfig = openConfig;
        this.openTitle = openTitle;
        this.expanded = expanded;
        this.navigate = navigate;

        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setToolTipText(navigate
                ? "Click to jump to its card · right-click for more"
                : expanded
                ? "Click to collapse · right-click for more"
                : "Click to expand in place · right-click for more");

        // The searchable value is the TARGET's name, so the path must end in
        // "name" to match the model search's leaf path (ViewableFieldPaths emits
        // e.g. ["episodes","name"]). Otherwise the row is never highlighted.
        List<String> searchPath = new ArrayList<>(
                fieldPath == null ? List.of() : fieldPath);
        searchPath.add("name");
        putClientProperty(SearchPanel.FIELD_PATH_PROPERTY, searchPath);
    }

    @Override
    protected Color valueColor() {
        return VALUE_COLOR;
    }

    @Override
    protected int leadingGlyphWidth() {
        return TRI_W;
    }

    @Override
    protected void paintLeadingGlyph(Graphics2D g2, int x, int baseline, int ascent) {
        int triMid = baseline - ascent / 2;
        g2.setColor(TRI_COLOR);
        if (navigate) {
            g2.drawString("→", x, baseline);   // →
        } else if (expanded) {
            g2.fillPolygon(
                    new int[]{x, x + 8, x + 4},
                    new int[]{triMid - 2, triMid - 2, triMid + 3},
                    3);
        } else {
            g2.fillPolygon(
                    new int[]{x + 1, x + 1, x + 6},
                    new int[]{triMid - 4, triMid + 4, triMid},
                    3);
        }
    }

    // Plain click: expand/collapse, or navigate to the target's own card. (Open
    // in a window stays on the right-click menu.)
    @Override
    protected void valueClicked(MouseEvent e) {
        if (NAV_DEBUG) {
            log.debug("[nav] ref valueClicked navigate=" + navigate
                    + " target='" + (target == null ? "null" : target.getDisplayName())
                    + "'");
        }
        if (navigate) {
            openOrFocus();
        } else {
            toggleExpansion();
        }
    }

    @Override
    protected void addExtraCopyMenuItems(JPopupMenu menu) {
        if (target == null) {
            return;
        }
        menu.addSeparator();
        JMenuItem open = new JMenuItem("Open in window");
        open.addActionListener(e -> openFullObject());
        menu.add(open);
    }

    private void toggleExpansion() {
        if (renderContext == null || target == null) {
            return;
        }
        // Flip from this chip's own effective state, so a reference that renders
        // expanded-by-default (its map entry may be absent) still collapses on the
        // first click rather than needing two.
        renderContext.setExpanded(target, !expanded);
        refreshRootCard();
    }

    // Rebuilds the whole card in place (Card#refresh) so the toggled
    // reference re-renders as a chip or an inline panel.
    private void refreshRootCard() {
        Card root = null;
        for (Container c = getParent(); c != null; c = c.getParent()) {
            if (c instanceof Card qp) {
                root = qp;
            }
        }
        if (root != null) {
            root.refresh();
        }
    }

    private void openOrFocus() {
        if (target == null) {
            return;
        }
        boolean focused = renderContext != null && renderContext.focusTopLevel(target);
        if (NAV_DEBUG) {
            log.debug("[nav] openOrFocus target='" + target.getDisplayName()
                    + "' focused=" + focused
                    + (focused ? "" : " -> opening a window"));
        }
        if (focused) {
            return;
        }
        openFullObject();
    }

    private void openFullObject() {
        if (target == null) {
            return;
        }
        new CardFrame(
                target,
                ViewConfig.allWithMinorFields(target.getClass())
                          .setAddListener(true)
                          .setThumb(true));
    }

    private static String name(Viewable target) {
        String n = target == null ? null : target.getName();
        return n == null ? "" : n;
    }
}
