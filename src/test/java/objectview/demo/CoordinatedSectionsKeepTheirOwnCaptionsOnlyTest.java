package objectview.demo;

import objectview.EdtTests;
import objectview.ViewableAdapter;
import objectview.view.SearchableView;
import org.junit.jupiter.api.Test;

import javax.swing.JCheckBox;
import java.awt.Component;
import java.awt.Container;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The sections of a coordinated view share one render context, and each keeps its own
 * captions-only choice (#407). Applying it on one section once flipped every section,
 * while re-rendering only the one it was applied on.
 */
class CoordinatedSectionsKeepTheirOwnCaptionsOnlyTest {

    @Test void captionsOnlyOnOneSectionLeavesTheOtherOpen() {
        EdtTests.onEdt(() -> {
            Star sirius = new Star("Sirius");
            Galaxy andromeda = new Galaxy("Andromeda");
            MultiView multi = new MultiView();
            multi.addSection("Star", Star.class, List.of(sirius));
            multi.addSection("Galaxy", Galaxy.class, List.of(andromeda));
            multi.build(1);
            SearchableView stars = multi.sectionViews().get(0);
            SearchableView galaxies = multi.sectionViews().get(1);
            Galaxy m31 = andromeda;
            multi.context().setExpanded(m31, false);   // folded by hand, before

            box(stars).doClick();
            stars.search().applyView();

            assertFalse(multi.context().isCardOpen(sirius, stars.search().getViewConfig()),
                    "the section it was applied on shows captions only");
            assertFalse(box(galaxies).isSelected(), "the other section keeps its own choice");
            assertFalse(multi.context().isCardOpen(m31, galaxies.search().getViewConfig()),
                    "a choice made by hand in the other section is not forgotten");
            multi.context().forgetCardChoices(List.of(m31));
            assertTrue(multi.context().isCardOpen(m31, galaxies.search().getViewConfig()),
                    "and without one, its cards start as its own setting says: open");
        });
    }

    private static JCheckBox box(SearchableView view) {
        JCheckBox found = find(view.search().viewEditor());
        if (found == null) throw new AssertionError("View Config offers no captions-only choice");
        return found;
    }

    private static JCheckBox find(Container root) {
        for (Component child : root.getComponents()) {
            if (child instanceof JCheckBox box
                    && "Top-level cards: captions only".equals(box.getText())) return box;
            if (child instanceof Container nested) {
                JCheckBox found = find(nested);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static final class Star extends ViewableAdapter {
        private final String name;
        private Star(String name) { this.name = name; }
        @Override public String getIdentifier() { return name; }
        @Override public String getDisplayName() { return name; }
    }

    private static final class Galaxy extends ViewableAdapter {
        private final String name;
        private Galaxy(String name) { this.name = name; }
        @Override public String getIdentifier() { return name; }
        @Override public String getDisplayName() { return name; }
    }
}
