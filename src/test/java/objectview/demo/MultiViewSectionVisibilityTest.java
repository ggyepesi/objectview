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

    @Test void hidingASectionRemovesItsPanelSearchAndTopLevelStatusOnly() {
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
            assertFalse(multi.context().isTopLevel(galaxy),
                    "a hidden class has no card to navigate to");
            assertFalse(multi.context().focusTopLevel(galaxy),
                    "a retained section revealer must not reopen a hidden class");
            JButton chooser = findButton(multi, "Classes shown: 1 of 2…");
            assertNotNull(chooser);
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
