package objectview.field;

import objectview.ViewableAdapter;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ViewableContractFieldSetTest {

    @Test void exposesStableRoleTaggedKeysThroughTheOrdinaryFieldSet() {
        Item item = new Item("item-1", "Item one");
        FieldSet fields = FieldSet.of(item);

        assertEquals("Item one", fields.read(ViewableContractFieldSet.DISPLAY_KEY));
        assertEquals("item-1", fields.read(ViewableContractFieldSet.IDENTITY_KEY));
        assertEquals(FieldRole.DISPLAY,
                fields.field(ViewableContractFieldSet.DISPLAY_KEY).role());
        assertEquals(FieldRole.IDENTITY,
                fields.field(ViewableContractFieldSet.IDENTITY_KEY).role());
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
