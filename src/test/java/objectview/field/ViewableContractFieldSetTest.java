package objectview.field;

import objectview.ViewableAdapter;
import objectview.annotations.DisplayField;
import objectview.annotations.Hidden;
import objectview.render.Card;
import objectview.viewconfig.ViewConfig;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

    @Test void annotatedBackingFieldOwnsDisplayRoleWithoutBeingDuplicated() {
        BoundItem item = new BoundItem("Visible label");
        FieldSet fields = FieldSet.of(item);

        assertEquals(FieldRole.DISPLAY, fields.field("label").role());
        assertEquals("Visible label", fields.read("label"));
        assertFalse(fields.fields().stream().anyMatch(field ->
                ViewableContractFieldSet.DISPLAY_KEY.equals(field.name())));
        // Generic reference/search paths may retain the stable contract alias,
        // but it is not another enumerable/configurable field.
        assertEquals("Visible label", fields.read(ViewableContractFieldSet.DISPLAY_KEY));
    }

    @Test void twoDisplayFieldsPickTheLastDeclared() {
        // More than one @DisplayField is a small user error, not a crash: the last
        // declared wins, deterministically, and no synthetic display field is added.
        FieldSet fields = FieldSet.of(new AmbiguousItem());
        assertEquals("second", ViewableContractFieldSet.displayKey(fields));
        assertFalse(fields.fields().stream().anyMatch(field ->
                ViewableContractFieldSet.DISPLAY_KEY.equals(field.name())));
    }

    @Test void schemaCanBindADynamicFieldToTheSameDisplayRole() {
        SchemaBoundItem item = new SchemaBoundItem("Dynamic label");
        FieldSet fields = FieldSet.of(item);

        assertEquals(FieldRole.DISPLAY, fields.field("caption").role());
        assertFalse(fields.fields().stream().anyMatch(field ->
                ViewableContractFieldSet.DISPLAY_KEY.equals(field.name())));
        assertEquals("Dynamic label", fields.read(ViewableContractFieldSet.DISPLAY_KEY));
    }

    @Test void displayFieldCannotAlsoBeHidden() {
        ViewableAdapter.clearReflectionCaches();
        assertThrows(IllegalStateException.class,
                () -> FieldSet.of(new HiddenDisplayItem()).fields());
        ViewableAdapter.clearReflectionCaches();
    }

    @Test void explicitPhysicalDisplaySelectionStillProducesTheCardTitle() {
        BoundItem item = new BoundItem("Visible label");
        ViewConfig config = ViewConfig.of(BoundItem.class);
        config.setAllFields(false);
        config.addField("label", ViewConfig.leaf());

        Card card = new Card(item, config, false);

        assertEquals("Visible label", card.getTitle());
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

    private static final class BoundItem extends ViewableAdapter {
        @DisplayField private final String label;
        private BoundItem(String label) { this.label = label; }
        @Override public String getIdentifier() { return label; }
        @Override public String getDisplayName() { return label; }
    }

    private static final class AmbiguousItem extends ViewableAdapter {
        @DisplayField private final String first = "first";
        @DisplayField private final String second = "second";
        @Override public String getIdentifier() { return first; }
        @Override public String getDisplayName() { return first; }
    }

    private static final class HiddenDisplayItem extends ViewableAdapter {
        @Hidden @DisplayField private final String label = "hidden";
        @Override public String getIdentifier() { return label; }
        @Override public String getDisplayName() { return label; }
    }

    private static final class SchemaBoundItem extends ViewableAdapter
            implements DynamicFields {
        private final Map<String, Object> values = new LinkedHashMap<>();
        private SchemaBoundItem(String caption) { values.put("caption", caption); }
        @Override public String getIdentifier() { return "dynamic"; }
        @Override public String getDisplayName() { return String.valueOf(values.get("caption")); }
        @Override public Map<String, Object> dynamicFieldValues() { return values; }
        @Override public FieldSchema dynamicFieldSchema() {
            return () -> java.util.List.of(FieldRef.computed(
                    "caption", "caption", FieldKind.TEXT, FieldRole.DISPLAY));
        }
    }
}
