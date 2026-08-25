package objectview.viewconfig;

import objectview.ViewableAdapter;
import objectview.annotations.Minor;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The minor BLOCK row and the "All minor fields" checkbox are two controls, and they
 * answer two different questions. The checkbox governs whatever a context reports as
 * minor, schema-declared fields included. The block stands for the minor set as a
 * configurable group, and follows only what a class DECLARES minor — otherwise a
 * schema-declared field would carry two controls over the same thing.
 */
class MinorBlockRowTest {

    private static class Town extends ViewableAdapter {
        private final String name = "Eger";
        @Minor private final String postcode = "3300";
        @Override public String getIdentifier() { return name; }
        @Override public String getDisplayName() { return name; }
    }

    private static class Plain extends ViewableAdapter {
        private final String name = "Eger";
        private final String postcode = "3300";   // NOT annotated @Minor
        @Override public String getIdentifier() { return name; }
        @Override public String getDisplayName() { return name; }
    }

    private static List<FieldRow> rowsFor(Object sample) {
        return ConfigFieldRowSource.INSTANCE.rows(new FieldRowContext(
                ViewConfig.all(sample.getClass().asSubclass(objectview.Viewable.class)),
                (objectview.Viewable) sample, false, false, Set.of(), null));
    }

    private static boolean hasBlock(List<FieldRow> rows) {
        return rows.stream().anyMatch(FieldRow::isMinorBlock);
    }

    @Test void aDeclaredMinorFieldRaisesTheBlock() {
        assertTrue(hasBlock(rowsFor(new Town())),
                "@Minor is a declaration, so the block has a group to stand for");
    }

    @Test void noMinorFieldsMeansNoBlock() {
        assertFalse(hasBlock(rowsFor(new Plain())),
                "an empty group is not a group");
    }

    @Test void theMinorOnlyTableNeverCarriesTheBlock() {
        List<FieldRow> minorOnly = ConfigFieldRowSource.INSTANCE.rows(new FieldRowContext(
                ViewConfig.all(Town.class), new Town(), true, false, Set.of(), null));
        assertFalse(hasBlock(minorOnly),
                "the minor-only table IS the block's contents — it cannot contain itself");
    }

    @Test void hidingTheOnlyMinorFieldRemovesTheBlock() {
        List<FieldRow> hidden = ConfigFieldRowSource.INSTANCE.rows(new FieldRowContext(
                ViewConfig.all(Town.class), new Town(), false, false,
                Set.of("postcode"), null));
        assertFalse(hasBlock(hidden),
                "a block over nothing the reader can see is a dead control");
    }

    /** A field this class does not annotate, that a schema declares minor. */
    private static final class SchemaMinor implements FieldTypeSource {
        @Override public FieldTypeInfo field(String name) {
            return "postcode".equals(name)
                    ? new FieldTypeInfo("String", false, true, null, null,
                            null, null, null, null)
                    : null;
        }
    }

    @Test void aSchemaDeclaredMinorFieldDoesNotRaiseTheBlock() {
        // The distinguishing case: minor by schema, not by annotation. The checkbox
        // governs it; a block row would be a second control over the same field.
        List<FieldRow> rows = ConfigFieldRowSource.INSTANCE.rows(new FieldRowContext(
                ViewConfig.all(Plain.class), new Plain(), false, false,
                Set.of(), new SchemaMinor()));

        assertFalse(hasBlock(rows),
                "a schema-declared minor field is checkbox-governed only");
    }

    @Test void theCheckboxStillSeesWhatTheBlockDeclines() {
        // hasMinorFields and the block ask different questions, and must keep
        // disagreeing here: the checkbox needs something to govern.
        FieldRowContext context = new FieldRowContext(
                ViewConfig.all(Plain.class), new Plain(), false, false,
                Set.of(), new SchemaMinor());

        assertTrue(ConfigFieldRowSource.INSTANCE.hasMinorFields(context),
                "the 'All minor fields' checkbox must still appear for a schema minor");
        assertFalse(hasBlock(ConfigFieldRowSource.INSTANCE.rows(context)));
    }
}
