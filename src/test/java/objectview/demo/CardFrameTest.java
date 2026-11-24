package objectview.demo;

import objectview.ViewableAdapter;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CardFrameTest {

    @Test
    void detachedWindowNamesTheLogicalClassOfItsInstance() {
        Item item = new Item("Ratatoskr", "NorseAnimal");

        assertEquals("Ratatoskr — Class: NorseAnimal", CardFrame.titleOf(item));
    }

    @Test
    void detachedWindowNamesEveryDirectClassInStableOrder() {
        Item item = new Item("Jörð", "NorseDeity", "Jotunn");

        assertEquals("Jörð — Class: Jotunn, NorseDeity", CardFrame.titleOf(item));
    }

    private static final class Item extends ViewableAdapter {
        private final String name;
        private final Set<String> classes;

        private Item(String name, String... classes) {
            this.name = name;
            this.classes = Set.of(classes);
        }

        @Override
        public String getIdentifier() {
            return name;
        }

        @Override
        public String getDisplayName() {
            return name;
        }

        @Override
        public Set<String> directClassNames() {
            return classes;
        }
    }
}
