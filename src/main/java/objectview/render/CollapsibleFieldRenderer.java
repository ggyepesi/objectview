package objectview.render;

import objectview.field.FieldPath;
import objectview.utils.swing.GridBagUtils;

import javax.swing.JComponent;
import javax.swing.BorderFactory;
import javax.swing.JPanel;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.Collection;
import java.util.Map;
import java.util.function.Supplier;

/** Shared collapsible collection/map presentation used by cards and table cells. */
public final class CollapsibleFieldRenderer {
    private CollapsibleFieldRenderer() {}

    public static JComponent create(
            String fieldName, FieldPath fieldPath,
            Object representedValue, Object expansionKey,
            RenderContext context, Supplier<JComponent> body) {
        return create(fieldName, fieldPath, representedValue, expansionKey,
                context, false, body);
    }

    /**
     * Renders one collection through the shared disclosure component. Initial
     * disclosure is presentation policy supplied by the field renderer; it is not
     * inferred from field selection or collection size here.
     */
    public static JComponent create(
            String fieldName, FieldPath fieldPath,
            Object representedValue, Object expansionKey,
            RenderContext context, boolean defaultExpanded,
            Supplier<JComponent> body) {
        int count = representedValue instanceof Collection<?> collection
                ? collection.size()
                : representedValue instanceof Map<?, ?> map ? map.size() : 0;
        return create(fieldName, fieldPath, representedValue, expansionKey,
                count, context, defaultExpanded, body);
    }

    public static JComponent create(
            String fieldName, FieldPath fieldPath,
            Object representedValue, Object expansionKey, int count,
            RenderContext context, Supplier<JComponent> body) {
        return create(fieldName, fieldPath, representedValue, expansionKey,
                count, context, false, body);
    }

    public static JComponent create(
            String fieldName, FieldPath fieldPath,
            Object representedValue, Object expansionKey, int count,
            RenderContext context, boolean defaultExpanded,
            Supplier<JComponent> body) {
        if (count < 0) return null;
        Object key = expansionKey == null ? representedValue : expansionKey;
        boolean expanded = count > 0 && context != null
                && context.isCollectionExpanded(key, defaultExpanded);
        return create(fieldName, fieldPath, representedValue, key, count,
                expanded, defaultExpanded, context, body);
    }

    /** A collection whose open state is already decided (by the render executor);
     * {@code defaultExpanded} is what the header's toggle flips from. */
    public static JComponent create(
            String fieldName, FieldPath fieldPath,
            Object representedValue, Object key, int count,
            boolean expanded, boolean defaultExpanded,
            RenderContext context, Supplier<JComponent> body) {
        if (count < 0) return null;
        return new CollapsibleFieldPanel(
                fieldName, fieldPath, representedValue, key, count,
                expanded, defaultExpanded, context, body);
    }

    /** Local refresh boundary for one collection field. A disclosure click replaces
     * only this header/body pair; the containing card and its sibling fields keep their
     * component identity and transient state. The outer virtual list is merely told to
     * remeasure the retained card. */
    private static final class CollapsibleFieldPanel extends JPanel
            implements RenderRefreshHost {
        private final String fieldName;
        private final FieldPath fieldPath;
        private final Object representedValue;
        private final Object key;
        private final int count;
        private final boolean defaultExpanded;
        private final RenderContext context;
        private final Supplier<JComponent> body;
        private boolean expanded;

        private CollapsibleFieldPanel(
                String fieldName, FieldPath fieldPath,
                Object representedValue, Object key, int count,
                boolean expanded, boolean defaultExpanded,
                RenderContext context, Supplier<JComponent> body) {
            super(new GridBagLayout());
            this.fieldName = fieldName;
            this.fieldPath = fieldPath;
            this.representedValue = representedValue;
            this.key = key;
            this.count = count;
            this.defaultExpanded = defaultExpanded;
            this.context = context;
            this.body = body;
            setOpaque(false);
            rebuild(expanded);
        }

        @Override public void refreshRenderedContent() {
            boolean expanded = currentCount() > 0 && context != null
                    && context.isCollectionExpanded(key, defaultExpanded);
            rebuild(expanded);
            revalidate();
            repaint();
            Card card = (Card) javax.swing.SwingUtilities.getAncestorOfClass(
                    Card.class, this);
            if (card != null) card.notifyOwnerLayoutChanged();
        }

        private void rebuild(boolean expanded) {
            removeAll();
            this.expanded = expanded;
            if (expanded && context != null) {
                setBorder(BorderFactory.createEmptyBorder(
                        0, CollapseGutter.WIDTH, 0, 0));
                CollapseGutter.install(this, () -> {
                    context.setCollectionExpanded(key, false);
                    RenderRefreshHost.refreshAncestor(this);
                });
            } else {
                setBorder(null);
                CollapseGutter.uninstall(this);
            }
            int displayedCount = currentCount();
            CollectionHeader header = new CollectionHeader(
                    fieldName, fieldPath, displayedCount, expanded, key, representedValue,
                    defaultExpanded, context);
            add(header, GridBagUtils.weighted(
                    0, 0, 1.0, 0.0,
                    GridBagConstraints.NORTHWEST,
                    GridBagConstraints.HORIZONTAL, new Insets(0, 0, 0, 0)));
            if (!expanded || body == null) return;
            JComponent items = body.get();
            if (items == null) return;
            add(items, GridBagUtils.weighted(
                    0, 1, 1.0, 0.0,
                    GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL,
                    new Insets(0, 16, 2, 0)));
        }

        private int currentCount() {
            return representedValue instanceof Collection<?> collection
                    ? collection.size()
                    : representedValue instanceof Map<?, ?> map ? map.size() : count;
        }

        @Override protected void paintComponent(Graphics graphics) {
            if (expanded) CollapseGutter.INSTANCE.paint(graphics, this, getHeight());
            super.paintComponent(graphics);
        }
    }
}
