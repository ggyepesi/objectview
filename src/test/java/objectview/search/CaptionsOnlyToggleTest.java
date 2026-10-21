package objectview.search;

import objectview.EdtTests;
import objectview.ViewableAdapter;
import objectview.view.SearchableView;
import objectview.viewconfig.ViewConfig;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The reader chooses in View Config whether top-level cards start open or as captions
 *  only (#407); Apply takes it into the view like any other View setting. */
class CaptionsOnlyToggleTest {

    @Test void aViewThatNeverFoldedLetsTheReaderShowCaptionsOnly() {
        EdtTests.onEdt(() -> {
            Item item = new Item("Metropolis");
            AtomicReference<SearchPanel.ConfigState> saved = new AtomicReference<>();
            SearchableView view = SearchableView.builder(List.of(item)).sample(item)
                    .configListener(saved::set).build();
            var context = view.cardList().getRenderContext();

            assertFalse(box(view).isSelected());
            assertTrue(context.isCardExpanded(item), "the view's default: open");

            box(view).doClick();
            view.search().applyView();

            assertFalse(context.isCardExpanded(item), "now its caption only");
            assertEquals(Boolean.TRUE, saved.get().view().rootsFolded(),
                    "the setting is the view config's, and is published with it");
        });
    }

    @Test void aViewThatFoldsByDefaultStartsWithTheToggleOn() {
        EdtTests.onEdt(() -> {
            Item item = new Item("Metropolis");
            SearchableView view = SearchableView.builder(List.of(item)).sample(item)
                    .collapsible(true).build();
            assertTrue(box(view).isSelected());
            assertFalse(view.cardList().getRenderContext().isCardExpanded(item));
        });
    }

    @Test void aStoredSettingWinsOverTheViewsDefault() {
        EdtTests.onEdt(() -> {
            Item item = new Item("Metropolis");
            ViewConfig stored = ViewConfig.of(Item.class).rootsFolded(true);
            SearchableView view = SearchableView.builder(List.of(item)).sample(item)
                    .collapsible(false)
                    .configState(new SearchPanel.ConfigState(null, null, stored)).build();
            assertTrue(box(view).isSelected());
            assertFalse(view.cardList().getRenderContext().isCardExpanded(item));
        });
    }

    @Test void switchingForgetsTheCardsOpenedUnderTheOtherSetting() {
        EdtTests.onEdt(() -> {
            Item item = new Item("Metropolis");
            SearchableView view = SearchableView.builder(List.of(item)).sample(item)
                    .collapsible(true).build();
            var context = view.cardList().getRenderContext();
            context.toggleCardExpanded(item);
            assertTrue(context.isCardExpanded(item));

            box(view).doClick();                 // open
            view.search().applyView();
            box(view).doClick();                 // captions only again
            view.search().applyView();

            assertFalse(context.isCardExpanded(item),
                    "the setting applies to every card, not only the untouched ones");
        });
    }

    /** The View Config editor's own checkbox, found as a reader finds it. */
    private static javax.swing.JCheckBox box(SearchableView view) {
        javax.swing.JCheckBox found = find(view.search().viewEditor());
        if (found == null) throw new AssertionError("View Config offers no captions-only choice");
        return found;
    }

    private static javax.swing.JCheckBox find(java.awt.Container root) {
        for (java.awt.Component child : root.getComponents()) {
            if (child instanceof javax.swing.JCheckBox box
                    && "Top-level cards: captions only".equals(box.getText())) return box;
            if (child instanceof java.awt.Container nested) {
                javax.swing.JCheckBox found = find(nested);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static final class Item extends ViewableAdapter {
        private final String name;
        private Item(String name) { this.name = name; }
        @Override public String getIdentifier() { return name; }
        @Override public String getDisplayName() { return name; }
    }
}
