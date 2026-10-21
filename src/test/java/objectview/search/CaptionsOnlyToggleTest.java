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

/** The reader chooses whether top-level cards start open or as captions only (#407). */
class CaptionsOnlyToggleTest {

    @Test void aViewThatNeverFoldedLetsTheReaderShowCaptionsOnly() {
        EdtTests.onEdt(() -> {
            Item item = new Item("Metropolis");
            AtomicReference<SearchPanel.ConfigState> saved = new AtomicReference<>();
            SearchableView view = SearchableView.builder(List.of(item)).sample(item)
                    .configListener(saved::set).build();
            var context = view.cardList().getRenderContext();

            assertFalse(view.search().captionsOnlyBox().isSelected());
            assertTrue(context.isCardExpanded(item), "the view's default: open");

            view.search().captionsOnlyBox().doClick();

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
            assertTrue(view.search().captionsOnlyBox().isSelected());
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
            assertTrue(view.search().captionsOnlyBox().isSelected());
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

            view.search().captionsOnlyBox().doClick();   // open
            view.search().captionsOnlyBox().doClick();   // captions only again

            assertFalse(context.isCardExpanded(item),
                    "the setting applies to every card, not only the untouched ones");
        });
    }

    private static final class Item extends ViewableAdapter {
        private final String name;
        private Item(String name) { this.name = name; }
        @Override public String getIdentifier() { return name; }
        @Override public String getDisplayName() { return name; }
    }
}
