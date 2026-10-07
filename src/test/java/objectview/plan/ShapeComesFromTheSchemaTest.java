package objectview.plan;

import objectview.field.FieldRef;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A type's fields and the types its fields lead to come from the schema, never from
 * the values an instance holds (#363). The shape used to be read off a sample: a null
 * value hid the nested type, a dynamic object listed only the keys it held, and the
 * first member of a collection decided what its field led to.
 */
class ShapeComesFromTheSchemaTest {

    private final HistoryShape h = new HistoryShape();

    @Test void buildingAShapeReadsNoValue() {
        TypeShape person = h.shape(h.wigmund);
        TypeShape offices = person.nested(field(person, "offices"));
        offices.nested(field(offices, "position")).fields();

        assertEquals(List.of(), h.reads, "a shape is the schema's, not the instance's");
    }

    @Test void aMissingValueDoesNotHideTheTypeItsFieldLeadsTo() {
        HistoryShape.Thing nobody = h.new Thing("Person", "nobody");   // no offices at all

        TypeShape offices = h.shape(nobody).nested(field(h.shape(nobody), "offices"));

        assertNotNull(offices, "the declared target type still describes the field");
        assertEquals(List.of("label", "source", "position", "startDate", "endDate"),
                offices.fields().stream().map(FieldRef::name).toList());
    }

    @Test void aKeyTheSchemaDoesNotDeclareIsNotAField() {
        h.wigmund.put("scratch", "left over from a transform");

        List<String> names = h.shape(h.wigmund).fields().stream().map(FieldRef::name).toList();

        assertFalse(names.contains("scratch"), names.toString());
        assertTrue(names.contains("epithet"),
                "a declared field is a field even when this instance has no value: " + names);
    }

    private static FieldRef field(TypeShape shape, String name) {
        return shape.fields().stream().filter(f -> f.name().equals(name))
                .findFirst().orElseThrow(() -> new AssertionError(name));
    }
}
