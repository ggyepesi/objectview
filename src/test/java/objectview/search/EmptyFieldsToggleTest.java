package objectview.search;

import objectview.EdtTests;
import objectview.ViewableAdapter;
import objectview.field.FieldPath;
import objectview.field.FieldProperties;
import objectview.render.Card;
import objectview.render.TextBlock;
import objectview.view.SearchableView;
import org.junit.jupiter.api.Test;

import javax.swing.JCheckBox;
import java.awt.Component;
import java.awt.Container;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The empty-field presentation choice is an ordinary persisted View setting (#568). */
class EmptyFieldsToggleTest {

    @Test void aReaderCanHideEmptySelectedFieldsAndApplyThatChoice() {
        EdtTests.onEdt(() -> {
            Item item = new Item();
            AtomicReference<SearchPanel.ConfigState> saved = new AtomicReference<>();
            SearchableView view = SearchableView.builder(List.of(item)).sample(item)
                    .configListener(saved::set).build();

            JCheckBox box = find(view.search().viewEditor());
            assertNotNull(box, "View Config offers the empty-field choice");
            assertTrue(box.isSelected(), "compatible default: show empty fields");

            box.doClick();
            view.search().applyView();

            assertEquals(Boolean.FALSE, saved.get().view().emptyFieldsVisible());
        });
    }

    @Test void applyActuallyRemovesEmptyRowsAndTheirDisclosureFromTheCard() {
        EdtTests.onEdt(() -> {
            Item item = new Item();
            SearchableView view = SearchableView.builder(List.of(item)).sample(item).build();

            Card shown = card(view, item);
            assertTrue(text(shown).hasMatchingRow(
                    FieldPath.of("note"), List.of(), true));
            assertNotNull(field(shown, "values"));

            find(view.search().viewEditor()).doClick();
            view.search().applyView();

            Card hidden = card(view, item);
            TextBlock remaining = find(hidden, TextBlock.class);
            assertTrue(remaining == null || !remaining.hasMatchingRow(
                    FieldPath.of("note"), List.of(), true));
            assertNull(field(hidden, "values"));
        });
    }

    private static Card card(SearchableView view, Item item) {
        return (Card) view.cardList().getVirtualList().ensureVisible(item);
    }

    private static TextBlock text(Container root) {
        TextBlock found = find(root, TextBlock.class);
        if (found == null) throw new AssertionError("No rendered text block");
        return found;
    }

    private static Component field(Container root, String name) {
        for (Component child : root.getComponents()) {
            if (child instanceof javax.swing.JComponent component
                    && name.equals(component.getClientProperty(
                    FieldProperties.FIELD_NAME_PROPERTY))) return component;
            if (child instanceof Container nested) {
                Component found = field(nested, name);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static <T> T find(Container root, Class<T> type) {
        for (Component child : root.getComponents()) {
            if (type.isInstance(child)) return type.cast(child);
            if (child instanceof Container nested) {
                T found = find(nested, type);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static JCheckBox find(Container root) {
        for (Component child : root.getComponents()) {
            if (child instanceof JCheckBox box
                    && "Show empty fields".equals(box.getText())) return box;
            if (child instanceof Container nested) {
                JCheckBox found = find(nested);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static final class Item extends ViewableAdapter {
        @SuppressWarnings("unused") private final String note = "   ";
        @SuppressWarnings("unused") private final List<String> values = List.of(" ");
        @Override public String getIdentifier() { return "item"; }
        @Override public String getDisplayName() { return "item"; }
    }
}
