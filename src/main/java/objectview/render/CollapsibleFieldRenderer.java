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
        int count = representedValue instanceof Collection<?> collection
                ? collection.size()
                : representedValue instanceof Map<?, ?> map ? map.size() : 0;
        return create(fieldName, fieldPath, representedValue, expansionKey,
                count, context, body);
    }

    public static JComponent create(
            String fieldName, FieldPath fieldPath,
            Object representedValue, Object expansionKey, int count,
            RenderContext context, Supplier<JComponent> body) {
        if (count <= 0) return null;
        Object key = expansionKey == null ? representedValue : expansionKey;
        boolean defaultExpanded = count <= RenderContext.COLLECTION_AUTO_EXPAND_MAX;
        boolean expanded = context != null
                && context.isCollectionExpanded(key, defaultExpanded);
        CollectionHeader header = new CollectionHeader(
                fieldName, fieldPath, count, expanded, key, representedValue,
                defaultExpanded, context);
        if (!expanded || body == null) return header;
        JComponent items = body.get();
        if (items == null) return header;

        ExpandedCollectionPanel wrap = new ExpandedCollectionPanel();
        wrap.setOpaque(false);
        wrap.arm(() -> {
            context.setCollectionExpanded(key, false);
            RenderRefreshHost.refreshAncestor(wrap);
        });
        wrap.add(header, GridBagUtils.weighted(
                0, 0, 1.0, 0.0,
                GridBagConstraints.NORTHWEST,
                GridBagConstraints.HORIZONTAL, new Insets(0, 0, 0, 0)));
        wrap.add(items, GridBagUtils.weighted(
                0, 1, 1.0, 0.0,
                GridBagConstraints.NORTHWEST,
                GridBagConstraints.HORIZONTAL, new Insets(0, 16, 2, 0)));
        return wrap;
    }

    /** The same whole-height collapse affordance used by expanded cards and query-log
     * entries, now owned by the shared collection presentation. */
    private static final class ExpandedCollectionPanel extends JPanel {
        private ExpandedCollectionPanel() {
            super(new GridBagLayout());
        }

        private void arm(Runnable collapse) {
            setBorder(BorderFactory.createEmptyBorder(
                    0, CollapseGutter.WIDTH, 0, 0));
            CollapseGutter.install(this, collapse);
        }

        @Override protected void paintComponent(Graphics graphics) {
            CollapseGutter.INSTANCE.paint(graphics, this, getHeight());
            super.paintComponent(graphics);
        }
    }
}
