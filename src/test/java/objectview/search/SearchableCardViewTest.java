package objectview.search;

import objectview.ViewableAdapter;
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
    }

    @Test void initialViewConfigControlsVirtualizedCards() {
        Item item = new Item("one", "secret");
        ViewConfig viewConfig = ViewConfig.of(Item.class);
        viewConfig.setAllFields(false);
        viewConfig.addField("name", ViewConfig.leaf());
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
        baseView.addField("name", ViewConfig.leaf());
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
}
