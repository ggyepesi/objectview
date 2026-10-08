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

    @Test void theDefaultTicksEveryTopLevelFieldAndTheDisplayOfEachObjectBelowThem() {
        ViewConfig defaults = ViewDefaults.newView(person);

        assertEquals(List.of("name", "offices", "epithet"),
                List.copyOf(defaults.getFields().keySet()));
        assertEquals(List.of("label"),
                List.copyOf(defaults.getFieldConfig("offices").getFields().keySet()),
                "the default ticks the office's DISPLAY, never source or position");
        assertTrue(defaults.getFieldConfig("offices").getFieldConfig("label")
                .getFields().isEmpty(), "nothing deeper than the DISPLAY");
        assertTrue(ViewConfigDesugar.isLiteral(defaults));
    }

    /** A quiz key or search selection enters a nested value only when it is ticked,
     *  so its shorthand includes an object with nothing under it. */
    @Test void aFieldSelectionsShorthandTicksNothingBelowTheObjectsItIncludes() {
        ViewConfig all = ViewConfig.leaf();
        all.setAllFields(true);

        assertTrue(ViewConfigDesugar.selection(all, person)
                .getFieldConfig("offices").getFields().isEmpty());
    }

    /** An {@code @Inline} object is part of its owner: the default ticks its own
     *  fields one level deep, and an object below it gets its DISPLAY alone. A
     *  referenced object gets its DISPLAY alone. */
    @Test void theDefaultShowsAnInlineObjectsOwnFieldsAndAReferencesCaption() {
        TypeShape country = shape(field("title", FieldRole.DISPLAY, false, false));
        TypeShape name = new TypeShape() {
            @Override public List<FieldRef> fields() {
                return List.of(field("givenName", FieldRole.NONE, false, false),
                        object("country", false), object("familyName", false));
            }
            @Override public TypeShape nested(FieldRef field) {
                return field.name().equals("country") ? country : null;
            }
        };
        TypeShape person = new TypeShape() {
            @Override public List<FieldRef> fields() {
                return List.of(object("structuredName", true), object("citizenship", false));
            }
            @Override public TypeShape nested(FieldRef field) {
                return field.name().equals("structuredName") ? name : country;
            }
        };

        ViewConfig defaults = ViewDefaults.newView(person);

        ViewConfig structured = defaults.getFieldConfig("structuredName");
        assertEquals(List.of("givenName", "country", "familyName"),
                List.copyOf(structured.getFields().keySet()));
        assertEquals(List.of("title"),
                List.copyOf(structured.getFieldConfig("country").getFields().keySet()),
                "below the inline object, an object gets its DISPLAY alone");
        // A family name is a Wikidata entity of no declared type: it still reads as its
        // caption (Afonso Costa's family name rendered as nothing on the web).
        assertEquals(List.of(objectview.field.ViewableContractFieldSet.DISPLAY_KEY),
                List.copyOf(structured.getFieldConfig("familyName").getFields().keySet()));
        assertEquals(List.of("title"),
                List.copyOf(defaults.getFieldConfig("citizenship").getFields().keySet()));
    }

    /** The field editors read a domain schema as a field-type source, which nests only
     *  into targets that declare fields. A vocabulary target (a person's Type, a given
     *  name) declares none, and the default ticked nothing below it: TransformApp showed
     *  "type (1)" open and empty where the web showed "human". */
    @Test void aReferenceToATypeWithNoFieldsStillDefaultsToItsCaption() {
        objectview.viewconfig.FieldTypeSource person = new objectview.viewconfig.FieldTypeSource() {
            @Override public FieldTypeInfo field(String name) {
                return "type".equals(name) ? new FieldTypeInfo("List<Type>", false, false,
                        null, null, "type", FieldRole.NONE, FieldKind.COLLECTION,
                        FieldKind.REFERENCE) : null;
            }
            @Override public List<String> fieldNames() { return List.of("type"); }
        };

        ViewConfig defaults = ViewDefaults.newView(TypeShape.of(person));

        assertEquals(List.of(objectview.field.ViewableContractFieldSet.DISPLAY_KEY),
                List.copyOf(defaults.getFieldConfig("type").getFields().keySet()));
    }

    private static FieldRef object(String name, boolean inline) {
        return FieldRef.described(name, name, FieldRole.NONE, FieldKind.REFERENCE,
                FieldKind.REFERENCE, "Object", true, false, null, false, false,
                inline, inline, false, "", false);
    }

    /** Ticking an object by hand never ticks its children. In a View an object is a
     *  branch, so one ticked alone is dropped where the config enters; a quiz key keeps
     *  it, as its caption. */
    @Test void anObjectTickedByHandTicksNothingUnderIt() {
        ViewConfig config = ViewConfig.leaf();
        config.addField("offices", ViewConfig.leaf());

        assertFalse(ViewConfigDesugar.literal(config, person).hasField("offices"));
        assertTrue(ViewConfigDesugar.selection(config, person)
                .getFieldConfig("offices").getFields().isEmpty());
    }

    @Test void explicitTicksKeepTheirOrderAndTheDisplayAliasBecomesTheRealField() {
        ViewConfig config = ViewConfig.leaf();
        config.addField("offices", labels());
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
        config.addField("offices", labels());

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
        assertEquals(List.of("name"),
                List.copyOf(offices.getFieldConfig("position").getFields().keySet()),
                "an object the shorthand includes gets its DISPLAY alone");
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

    /** An office's DISPLAY: something ticked under offices, so it is a View branch. */
    private static ViewConfig labels() {
        ViewConfig offices = ViewConfig.leaf();
        offices.addField("label", ViewConfig.leaf());
        return offices;
    }
}
