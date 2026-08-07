package objectview.render;

import objectview.field.FieldKind;
import objectview.field.FieldRef;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/** A card renders explicitly-configured fields in the config's order (so the view
 *  config's move before/after reorders the card); implied fields keep schema order. */
class CardFieldOrderTest {

    private static FieldRef f(String name) {
        return FieldRef.of(name, FieldKind.TEXT, "String", false, false, false);
    }

    private static List<String> names(List<FieldRef> fields) {
        return fields.stream().map(FieldRef::name).toList();
    }

    private static Set<String> order(String... names) {
        return new LinkedHashSet<>(List.of(names));
    }

    @Test void configuredFieldsRenderInConfigOrder() {
        List<FieldRef> schemaOrder = List.of(f("a"), f("b"), f("c"));
        assertEquals(List.of("c", "a", "b"),
                names(Card.orderFieldsByConfig(schemaOrder, order("c", "a", "b"))));
    }

    @Test void impliedFieldsKeepSchemaOrderAfterConfiguredOnes() {
        List<FieldRef> schemaOrder = List.of(f("a"), f("b"), f("c"), f("d"));
        // Only c and a are named (reordered); b and d are implied -> appended in schema order.
        assertEquals(List.of("c", "a", "b", "d"),
                names(Card.orderFieldsByConfig(schemaOrder, order("c", "a"))));
    }

    @Test void anEmptyOrderLeavesTheListUntouched() {
        List<FieldRef> schemaOrder = List.of(f("a"), f("b"));
        // All-fields config names nothing: return the very same list, schema order intact.
        assertSame(schemaOrder, Card.orderFieldsByConfig(schemaOrder, Set.of()));
    }

    @Test void aConfiguredNameAbsentFromTheSchemaIsSkipped() {
        List<FieldRef> schemaOrder = List.of(f("a"), f("b"));
        assertEquals(List.of("b", "a"),
                names(Card.orderFieldsByConfig(schemaOrder, order("gone", "b", "a"))));
    }
}
