package objectview.demo;

import objectview.EdtTests;
import objectview.ViewableAdapter;
import org.junit.jupiter.api.Test;

import javax.swing.JButton;
import java.awt.Component;
import java.awt.Container;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A multi-section host projects one chosen set without changing its objects. */
class MultiViewSectionVisibilityTest {

    @Test void hidingASectionKeepsItsLoadedTargetButRemovesItsPanelAndSearch() {
        EdtTests.onEdt(() -> {
            Item star = new Item("Star", "Sirius");
            Item galaxy = new Item("Galaxy", "Andromeda");
            AtomicReference<Set<String>> reported = new AtomicReference<>();
            MultiView multi = new MultiView();
            multi.sectionNoun("Classes");
            multi.addSection("Star", Item.class, List.of(star));
            multi.addSection("Galaxy", Item.class, List.of(galaxy));
            multi.onVisibleSectionsChanged(reported::set);
            multi.build(1);

            assertEquals(Set.of("Star", "Galaxy"), multi.visibleSectionTitles());
            assertTrue(multi.context().isTopLevel(star));
            assertTrue(multi.context().isTopLevel(galaxy));

            multi.visibleSections(Set.of("Star"));

            assertEquals(Set.of("Star"), multi.visibleSectionTitles());
            assertEquals(Set.of("Star"), reported.get());
            assertTrue(multi.context().isTopLevel(star));
            assertTrue(multi.context().isTopLevel(galaxy),
                    "a loaded target remains navigable while its class is hidden");
            assertNotNull(findButton(multi, "Classes shown: 1 of 2…"));

            var targetSearch = multi.sectionViews().get(1).search();
            assertTrue(targetSearch.getParent() == null, "hiding removes the class from shared search");
            assertTrue(multi.context().focusTopLevel(new Item("Galaxy", "Andromeda")),
                    "the typed identity resolves even when the reference is a different object");
            assertEquals(Set.of("Star", "Galaxy"), reported.get());
            assertNotNull(findButton(multi, "Classes shown: 2 of 2…"));
            assertNotNull(targetSearch.getParent(), "following the link restores the search tab");
            assertFalse(multi.context().focusTopLevel(new Item("Star", "Andromeda")),
                    "a different declared type may not reveal the same identifier");
        });
    }

    @Test void closingASectionUpdatesTheSameClassesShownSelection() {
        EdtTests.onEdt(() -> {
            Item star = new Item("Star", "Sirius");
            Item galaxy = new Item("Galaxy", "Andromeda");
            AtomicReference<Set<String>> reported = new AtomicReference<>();
            MultiView multi = new MultiView();
            multi.sectionNoun("Classes");
            multi.addSection("Star", Item.class, List.of(star));
            multi.addSection("Galaxy", "Galaxy (showing 1 of 2)",
                    Item.class, List.of(galaxy));
            multi.onVisibleSectionsChanged(reported::set);
            multi.build(1);

            JButton closeGalaxy = findButtonByTooltip(
                    multi, "Hide Galaxy (showing 1 of 2)");
            assertNotNull(closeGalaxy);
            closeGalaxy.doClick();

            assertEquals(Set.of("Star"), multi.visibleSectionTitles());
            assertEquals(Set.of("Star"), reported.get());
            assertNotNull(findButton(multi, "Classes shown: 1 of 2…"));
            assertTrue(multi.context().isTopLevel(galaxy));
        });
    }

    private static JButton findButton(Container root, String text) {
        for (Component child : root.getComponents()) {
            if (child instanceof JButton button && text.equals(button.getText())) return button;
            if (child instanceof Container nested) {
                JButton found = findButton(nested, text);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static JButton findButtonByTooltip(Container root, String text) {
        for (Component child : root.getComponents()) {
            if (child instanceof JButton button
                    && text.equals(button.getToolTipText())) return button;
            if (child instanceof Container nested) {
                JButton found = findButtonByTooltip(nested, text);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static final class Item extends ViewableAdapter {
        private final String type;
        private final String name;

        private Item(String type, String name) {
            this.type = type;
            this.name = name;
        }

        @Override public String getIdentifier() { return name; }
        @Override public String getDisplayName() { return name; }
        @Override public String typeName() { return type; }
        @Override public String identityTypeName() { return type; }
    }
}
