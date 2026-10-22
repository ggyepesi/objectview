package objectview.render;

import objectview.ViewableAdapter;
import objectview.annotations.Inline;
import objectview.field.FieldProperties;
import objectview.viewconfig.ViewConfig;
import org.junit.jupiter.api.Test;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NestedCardRefreshTest {

    /** {@code @Inline} places the steps in the card; it does not open them (#398).
     *  Each click opens one level: the list shows the step titles, not their details. */
    @Test void inlineWorkflowStepsOpenOneLevelPerClick() throws Exception {
        LogLike root = new LogLike("generate domains", "root detail");
        root.steps.add(new LogLike("generate one domain", "outer detail"));
        RenderContext context = new RenderContext();
        Card[] rendered = new Card[1];

        SwingUtilities.invokeAndWait(() -> rendered[0] = new Card(
                root, logConfig(2), context, false));

        CollectionHeader steps = find(rendered[0], CollectionHeader.class);
        assertNotNull(steps);
        assertFalse(steps.isExpanded(), "the step list starts folded");
        assertNull(findReference(rendered[0], "generate one domain"));

        context.setCollectionExpanded(root.steps, true);
        SwingUtilities.invokeAndWait(() -> rendered[0] = new Card(
                root, logConfig(2), context, false));

        assertNotNull(findReference(rendered[0], "generate one domain"),
                "opening the list shows the step under its title");
        assertFalse(renders(rendered[0], "outer detail"),
                "and not the step's own details, which are the next level");
    }

    @Test
    void innerInlineChipRefreshesItsNestedCardWithoutTurningItIntoARootCard()
            throws Exception {
        LogLike root = new LogLike("generate domains", "root detail");
        LogLike outer = new LogLike("generate one domain", "outer detail");
        LogLike inner = new LogLike("run query", "inner detail");
        root.steps.add(outer);
        outer.steps.add(inner);

        RenderContext context = new RenderContext();
        context.setCollapsibleCards(true);
        context.toggleCardExpanded(root);
        context.setCollectionExpanded(root.steps, true);
        context.setCollectionExpanded(outer.steps, true);
        context.setExpanded(outer, true);

        Card[] rendered = new Card[1];
        SwingUtilities.invokeAndWait(() -> rendered[0] = new Card(
                root, logConfig(3), context, false));

        assertEquals(2, count(rendered[0], Card.class),
                "the root and the expanded outer log entry should be cards");

        SwingUtilities.invokeAndWait(() -> {
            ReferenceRow innerChip = findReference(rendered[0], "run query");
            assertNotNull(innerChip, "the inner log entry should be an expandable chip");
            innerChip.valueClicked(click(innerChip));
        });

        assertEquals(3, count(rendered[0], Card.class),
                "expanding the inner chip must keep the outer entry nested and render the inner body");
        assertNotNull(findCard(rendered[0], inner));

        // Exercise the reverse transition too. Before the fix, the first refresh
        // rebuilt the outer card with top-level collapsible-card rules, so this
        // second lookup/click could not reliably find the same inner entry.
        SwingUtilities.invokeAndWait(() -> {
            ReferenceRow innerChip = findReference(rendered[0], "run query");
            assertNotNull(innerChip);
            innerChip.valueClicked(click(innerChip));
        });

        assertEquals(2, count(rendered[0], Card.class));
        assertNotNull(findReference(rendered[0], "run query"));
    }

    @Test void nestedExpansionRemeasuresWithoutRematerializingTheRootCard()
            throws Exception {
        LogLike root = new LogLike("generate domains", "root detail");
        LogLike outer = new LogLike("generate one domain", "outer detail");
        LogLike inner = new LogLike("run query", "inner detail");
        root.steps.add(outer);
        outer.steps.add(inner);
        RenderContext context = new RenderContext();
        context.setCollectionExpanded(root.steps, true);
        context.setExpanded(outer, true);
        context.setCollectionExpanded(outer.steps, true);
        CardListView list = new CardListView();
        list.setRenderContext(context);
        list.addViewable(root);
        list.createCardsPanel(1);
        list.getVirtualList().setViewConfigResolver(ignored -> logConfig(3));

        javax.swing.JComponent[] before = new javax.swing.JComponent[1];
        SwingUtilities.invokeAndWait(() -> {
            before[0] = list.getVirtualList().buildIfNeeded(root);
            ReferenceRow innerChip = findReference(before[0], "run query");
            assertNotNull(innerChip);
            innerChip.valueClicked(click(innerChip));
        });

        assertSame(before[0], list.getVirtualList().builtCard(root),
                "a nested disclosure must retain the materialized root component");
        list.dispose();
    }

    @Test void collectionDisclosureRebuildsOnlyThatFieldInAVirtualCard()
            throws Exception {
        LogLike root = new LogLike("generate domains", "root detail");
        root.steps.add(new LogLike("generate one domain", "outer detail"));
        RenderContext context = new RenderContext();
        CardListView list = new CardListView();
        list.setRenderContext(context);
        list.addViewable(root);
        list.createCardsPanel(1);
        list.getVirtualList().setViewConfigResolver(ignored -> logConfig(2));

        javax.swing.JComponent[] rootCard = new javax.swing.JComponent[1];
        TextBlock[] unchangedSibling = new TextBlock[1];
        SwingUtilities.invokeAndWait(() -> {
            rootCard[0] = list.getVirtualList().buildIfNeeded(root);
            unchangedSibling[0] = find(rootCard[0], TextBlock.class);
            CollectionHeader header = find(rootCard[0], CollectionHeader.class);
            assertNotNull(unchangedSibling[0]);
            assertNotNull(header);
            header.dispatchEvent(click(header));
        });

        assertSame(rootCard[0], list.getVirtualList().builtCard(root));
        assertSame(unchangedSibling[0], find(rootCard[0], TextBlock.class),
                "opening one collection must not repaint the card's sibling fields");
        assertNotNull(findReference(rootCard[0], "generate one domain"));
        list.dispose();
    }

    private static MouseEvent click(JComponent component) {
        return new MouseEvent(component, MouseEvent.MOUSE_CLICKED,
                System.currentTimeMillis(), 0, 1, 1, 1, false,
                MouseEvent.BUTTON1);
    }

    private static ReferenceRow findReference(Component root, String value) {
        if (root instanceof ReferenceRow row
                && value.equals(row.getClientProperty(
                FieldProperties.FIELD_VALUE_PROPERTY))) {
            return row;
        }
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                ReferenceRow found = findReference(child, value);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static Card findCard(Component root, LogLike value) {
        if (root instanceof Card card && card.getViewable() == value) {
            return card;
        }
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                Card found = findCard(child, value);
                if (found != null) return found;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static <T> T find(Component root, Class<T> type) {
        if (type.isInstance(root)) return (T) root;
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                T found = find(child, type);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static boolean renders(Component root, String text) {
        if (root instanceof TextRow row
                && row.matchesRenderedText(java.util.List.of(text), true)) {
            return true;
        }
        if (root instanceof JComponent component
                && text.equals(component.getClientProperty(
                FieldProperties.FIELD_VALUE_PROPERTY))) {
            return true;
        }
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                if (renders(child, text)) return true;
            }
        }
        return false;
    }

    private static int count(Component root, Class<?> type) {
        int result = type.isInstance(root) ? 1 : 0;
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                result += count(child, type);
            }
        }
        return result;
    }

    private static ViewConfig logConfig(int depth) {
        ViewConfig config = ViewConfig.of(LogLike.class);
        config.setAllFields(false);
        config.addField(objectview.field.ViewableContractFieldSet.DISPLAY_KEY,
                ViewConfig.leaf());
        config.addField("detail", ViewConfig.leaf());
        if (depth > 0) config.addField("steps", logConfig(depth - 1));
        return config;
    }

    private static final class LogLike extends ViewableAdapter {
        private final String name;
        private final String detail;
        @Inline
        private final Collection<LogLike> steps = new ArrayList<>();

        private LogLike(String name, String detail) {
            this.name = name;
            this.detail = detail;
        }

        @Override
        public String getIdentifier() {
            return name;
        }

        @Override
        public String getDisplayName() {
            return name;
        }
    }
}
