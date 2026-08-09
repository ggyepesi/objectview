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
import static org.junit.jupiter.api.Assertions.assertNotNull;

class NestedCardRefreshTest {

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
        context.setExpanded(outer, true);

        Card[] rendered = new Card[1];
        SwingUtilities.invokeAndWait(() -> rendered[0] = new Card(
                root, ViewConfig.all(LogLike.class), context, false));

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

    private static int count(Component root, Class<?> type) {
        int result = type.isInstance(root) ? 1 : 0;
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                result += count(child, type);
            }
        }
        return result;
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
