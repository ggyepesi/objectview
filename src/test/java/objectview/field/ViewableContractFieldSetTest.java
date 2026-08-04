package objectview.field;

import objectview.ViewableAdapter;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ViewableContractFieldSetTest {

    @Test void projectsTheDisplayNameButNotTheIdentifier() {
        Item item = new Item("item-1", "Item one");
        FieldSet fields = FieldSet.of(item);

        // Display name is a projected, role-tagged field (title + configurable/searchable).
        assertEquals("Item one", fields.read(ViewableContractFieldSet.DISPLAY_KEY));
        assertEquals(FieldRole.DISPLAY,
                fields.field(ViewableContractFieldSet.DISPLAY_KEY).role());

        // Identity is a keying method, never rendered — so it is NOT a field.
        assertNull(fields.field(ViewableContractFieldSet.IDENTITY_KEY));
        assertNull(fields.read(ViewableContractFieldSet.IDENTITY_KEY));
    }

    @Test void aRealBackingFieldWinsEvenForAReservedKey() {
        Item item = new Item("item-1", "Item one");
        item.values.put(ViewableContractFieldSet.DISPLAY_KEY, "stored value");

        FieldSet fields = FieldSet.of(item);
        assertEquals("stored value", fields.read(ViewableContractFieldSet.DISPLAY_KEY));
        assertEquals(FieldRole.NONE,
                fields.field(ViewableContractFieldSet.DISPLAY_KEY).role());
    }

    private static final class Item extends ViewableAdapter implements DynamicFields {
        private final String id;
        private final String display;
        private final Map<String, Object> values = new LinkedHashMap<>();

        private Item(String id, String display) {
            this.id = id;
            this.display = display;
        }

        @Override public String getIdentifier() { return id; }
        @Override public String getDisplayName() { return display; }
        @Override public Map<String, Object> dynamicFieldValues() { return values; }
    }
}
