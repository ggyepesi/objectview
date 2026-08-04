package objectview.field;

import objectview.ViewableAdapter;
import objectview.annotations.Link;
import objectview.annotations.Provenance;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProvenanceFieldTest {
    @Test void provenanceIsAnOrdinaryFieldWithSpecializedHints() {
        Item item = new Item();
        FieldRef sourceField = item.fields().field("origin");
        assertTrue(sourceField.provenance());
        assertFalse(sourceField.structural());
        assertSame(item.origin, item.fields().read("origin"));
        assertTrue(item.origin.fields().field("record").link());
    }

    private static final class Item extends ViewableAdapter {
        @Provenance
        private final TestSource origin = new TestSource();

        @Override public String getIdentifier() { return "item"; }
        @Override public String getDisplayName() { return "Item"; }
    }

    private static final class TestSource extends ViewableAdapter {
        @Link
        private final String record = "Q42|https://www.wikidata.org/wiki/Q42";

        @Override public String getIdentifier() { return "Q42"; }
        @Override public String getDisplayName() { return "Wikidata"; }
    }
}
