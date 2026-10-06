package objectview.plan;

import objectview.field.FieldKind;
import objectview.field.FieldRef;
import objectview.field.FieldRole;
import objectview.field.FieldSet;
import objectview.viewconfig.ViewConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Shorthand is rewritten into plain ticks once, where a config enters (directive 24). */
class ViewConfigDesugarTest {

    private final HistoryShape history = new HistoryShape();
    private final TypeShape person = history.shape(history.wigmund);

    @Test void theDefaultTicksEveryTopLevelFieldAndNothingBelowThem() {
        ViewConfig defaults = ViewDefaults.newView(person);

        assertEquals(List.of("name", "offices", "epithet"),
                List.copyOf(defaults.getFields().keySet()));
        assertTrue(defaults.getFieldConfig("offices").getFields().isEmpty(),
                "selecting offices never ticks source, position or anything else under it");
        assertTrue(ViewConfigDesugar.isLiteral(defaults));
    }

    @Test void explicitTicksKeepTheirOrderAndTheDisplayAliasBecomesTheRealField() {
        ViewConfig config = ViewConfig.leaf();
        config.addField("offices", ViewConfig.leaf());
        config.addField("@view:display", ViewConfig.leaf());

        ViewConfig literal = ViewConfigDesugar.literal(config, person);

        assertEquals(List.of("offices", "name"), List.copyOf(literal.getFields().keySet()));
    }

    /** The view config's move before/after reorders the card: ticked fields keep the
     *  order they were arranged in, and what "all fields" adds follows in schema order. */
    @Test void arrangedTicksComeFirstAndShorthandAddsTheRestInSchemaOrder() {
        ViewConfig config = ViewConfig.leaf();
        config.setAllFields(true);
        config.addField("epithet", ViewConfig.leaf());
        config.addField("offices", ViewConfig.leaf());

        ViewConfig literal = ViewConfigDesugar.literal(config, person);

        assertEquals(List.of("epithet", "offices", "name"),
                List.copyOf(literal.getFields().keySet()));
        assertEquals(List.of("epithet", "offices"),
                new PlanResolver().resolve(literal,
                        FieldSet.of(history.wigmund, HistoryShape.schema(history.wigmund)),
                        "Person", true).body().stream()
                        .map(ObjectPlan.FieldPlan::name).toList(),
                "the plan's body follows the config; the caption is not a body field");
    }

    @Test void shorthandBelowAnExplicitFieldIsExpandedAgainstThatFieldsShape() {
        ViewConfig everyOfficeField = ViewConfig.leaf();
        everyOfficeField.setAllFields(true);
        ViewConfig config = ViewConfig.leaf();
        config.addField("offices", everyOfficeField);

        ViewConfig offices = ViewConfigDesugar.literal(config, person).getFieldConfig("offices");

        assertEquals(List.of("label", "source", "position", "startDate", "endDate"),
                List.copyOf(offices.getFields().keySet()));
        assertTrue(offices.getFieldConfig("position").getFields().isEmpty());
        assertTrue(offices.getFieldConfig("source").getFields().isEmpty());
    }

    @Test void shorthandNeverIncludesIdentityOrStructuralFieldsAndMinorOnlyWithItsSwitch() {
        TypeShape shape = shape(
                field("title", FieldRole.DISPLAY, false, false),
                field("id", FieldRole.IDENTITY, false, false),
                field("plumbing", FieldRole.NONE, true, false),
                field("note", FieldRole.NONE, false, true),
                field("year", FieldRole.NONE, false, false));

        ViewConfig all = ViewConfig.leaf();
        all.setAllFields(true);
        assertEquals(List.of("title", "year"),
                List.copyOf(ViewConfigDesugar.literal(all, shape).getFields().keySet()));

        all.setAllMinorFields(true);
        assertEquals(List.of("title", "note", "year"),
                List.copyOf(ViewConfigDesugar.literal(all, shape).getFields().keySet()));
    }

    @Test void aTickedStructuralFieldStaysTicked() {
        TypeShape shape = shape(field("plumbing", FieldRole.NONE, true, false));
        ViewConfig config = ViewConfig.leaf();
        config.addField("plumbing", ViewConfig.leaf());

        assertTrue(ViewConfigDesugar.literal(config, shape).hasField("plumbing"));
    }

    @Test void onlyALiteralConfigIsResolved() {
        ViewConfig shorthand = ViewConfig.leaf();
        shorthand.setAllFields(true);

        assertFalse(ViewConfigDesugar.isLiteral(shorthand));
        assertThrows(IllegalArgumentException.class, () -> new PlanResolver().resolve(
                shorthand, FieldSet.of(history.wigmund), "Person", false));
    }

    private static FieldRef field(String name, FieldRole role, boolean structural,
                                  boolean minor) {
        return FieldRef.described(name, name, role, FieldKind.TEXT, FieldKind.TEXT,
                "String", false, false, null, structural, minor,
                false, false, false, "", false);
    }

    private static TypeShape shape(FieldRef... fields) {
        return new TypeShape() {
            @Override public List<FieldRef> fields() { return List.of(fields); }
            @Override public TypeShape nested(FieldRef field) { return null; }
        };
    }
}
