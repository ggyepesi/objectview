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

    private static final class Item extends ViewableAdapter {
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
}
