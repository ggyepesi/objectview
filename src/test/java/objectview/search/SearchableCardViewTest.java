package objectview.search;

import objectview.ViewableAdapter;
import objectview.field.DynamicFields;
import objectview.viewconfig.FieldTypeSource;
import objectview.render.Card;
import objectview.viewconfig.ViewConfig;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class SearchableCardViewTest {

    @Test void wiresCardsSearchAndContextOnce() {
        Item item = new Item("one");
        SearchableCardView view = SearchableCardView.builder(List.of(item))
                .sample(item)
                .hiddenFields(Set.of("internal"))
                .collapsible(true)
                .build();

        assertNotNull(view.cards().getVirtualList());
        assertNotNull(view.search());
        assertTrue(view.renderContext().collapsibleCards());
        assertTrue(view.search().getViewConfig().isAllMinorFields(),
                "an expanded instance starts with its complete field set");
    }

    @Test void initialViewConfigControlsVirtualizedCards() {
        Item item = new Item("one", "secret");
        ViewConfig viewConfig = ViewConfig.of(Item.class);
        viewConfig.setAllFields(false);
        viewConfig.addField(
                objectview.field.ViewableContractFieldSet.DISPLAY_KEY, ViewConfig.leaf());
        AtomicReference<SearchPanel.ConfigState> retained = new AtomicReference<>();

        SearchableCardView view = SearchableCardView.builder(List.of(item))
                .sample(item)
                .configState(new SearchPanel.ConfigState(null, null, viewConfig))
                .configListener(retained::set)
                .build();
        view.search().applyView();

        SearchableCardView replacement = SearchableCardView.builder(List.of(item))
                .sample(item)
                .configState(retained.get())
                .build();

        Card card = (Card) replacement.cards().getVirtualList().buildIfNeeded(item);
        assertTrue(componentText(card).contains("one"));
        assertFalse(componentText(card).contains("secret"));
    }

    @Test void subtypeAdditionalFieldsShareOneHierarchyConfig() {
        Item base = new Item("base", "base-secret");
        SubItem subtype = new SubItem("sub", "sub-secret");
        ViewConfig baseView = ViewConfig.of(Item.class);
        baseView.setAllFields(false);
        baseView.addField(
                objectview.field.ViewableContractFieldSet.DISPLAY_KEY, ViewConfig.leaf());
        ViewConfig disabledSubtype = ViewConfig.of(SubItem.class);
        disabledSubtype.setAllFields(false);
        SearchPanel.ConfigState state = new SearchPanel.ConfigState(
                null, null, baseView, java.util.Map.of(), java.util.Map.of(),
                java.util.Map.of("SubItem", disabledSubtype));

        SearchableCardView view = SearchableCardView.builder(List.of(base, subtype))
                .sample(base)
                .configState(state)
                .subtypeConfigs(List.of(new SearchPanel.SubtypeConfig(
                        "SubItem", "Item", subtype, null, Set.of("internal"),
                        value -> value instanceof SubItem)))
                .build();

        Card card = (Card) view.cards().getVirtualList().buildIfNeeded(subtype);
        assertTrue(componentText(card).contains("sub"));
        assertFalse(componentText(card).contains("sub-secret"));
        assertTrue(view.search().configState().subtypeView().containsKey("SubItem"));
    }

    @Test void schemaIsInstalledBeforeNullSampleDynamicConfigIsMaterialized() {
        DynamicItem item = new DynamicItem("visible-value");
        FieldTypeSource schema = new FieldTypeSource() {
            @Override public FieldTypeInfo field(String name) {
                return "detail".equals(name)
                        ? new FieldTypeInfo("String", false, false, null, null,
                                null, objectview.field.FieldRole.NONE,
                                objectview.field.FieldKind.TEXT,
                                objectview.field.FieldKind.TEXT)
                        : null;
            }

            @Override public List<String> fieldNames() { return List.of("detail"); }
        };

        // Snapshot-backed views intentionally have no synthetic sample; fields come
        // from their saved schema. They must still survive the initial config fold.
        SearchableCardView view = SearchableCardView.builder(List.of(item))
                .fieldTypes(schema)
                .collapsible(true)
                .build();

        assertTrue(view.search().getViewConfig().hasField("detail"),
                view.search().getViewConfig().getFields().keySet().toString());
        Card collapsed = (Card) view.cards().getVirtualList().buildIfNeeded(item);
        JLabel toggle = findLabel(collapsed, "▶ ");
        assertNotNull(toggle);
        java.awt.event.MouseEvent click = new java.awt.event.MouseEvent(
                toggle, java.awt.event.MouseEvent.MOUSE_PRESSED,
                System.currentTimeMillis(), 0, 2, 2, 1, false,
                java.awt.event.MouseEvent.BUTTON1);
        for (java.awt.event.MouseListener listener : toggle.getMouseListeners()) {
            listener.mousePressed(click);
        }
        Card card = (Card) view.cards().getVirtualList().buildIfNeeded(item);
        assertTrue(card.getComponentCount() > 1,
                "a saved dynamic field must not vanish before its schema is installed");
    }

    private static String componentText(Component component) {
        StringBuilder text = new StringBuilder();
        if (component instanceof JLabel label) text.append(label.getText()).append('\n');
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                text.append(componentText(child));
            }
        }
        return text.toString();
    }

    private static JLabel findLabel(Component component, String text) {
        if (component instanceof JLabel label && text.equals(label.getText())) return label;
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                JLabel found = findLabel(child, text);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static class Item extends ViewableAdapter {
        private final String name;
        private final String internal;
        private Item(String name) { this(name, ""); }
        private Item(String name, String internal) {
            this.name = name;
            this.internal = internal;
        }
        @Override public String getIdentifier() { return name; }
        @Override public String getDisplayName() { return name; }
    }

    private static final class SubItem extends Item {
        private SubItem(String name, String internal) { super(name, internal); }
    }

    private static final class DynamicItem extends ViewableAdapter
            implements DynamicFields {
        private final java.util.Map<String, Object> values =
                new java.util.LinkedHashMap<>();

        private DynamicItem(String detail) { values.put("detail", detail); }
        @Override public String getIdentifier() { return "dynamic"; }
        @Override public String getDisplayName() { return "Dynamic"; }
        @Override public java.util.Map<String, Object> dynamicFieldValues() {
            return values;
        }
    }
}
