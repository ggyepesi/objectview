package objectview.field;

import objectview.ViewableAdapter;
import objectview.provenance.Source;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProvenanceFieldTest {

    @Test void sourceIsAnOrdinaryNestedFieldWithSpecializedHints() {
        Item item = new Item();
        TestSource source = new TestSource("Q42");
        item.source(source);

        FieldRef sourceField = item.fields().field("source");
        assertTrue(sourceField.provenance());
        assertFalse(sourceField.structural());
        assertSame(source, item.fields().read("source"));

        FieldRef qid = source.fields().field("qid");
        assertTrue(qid.link());
        assertEquals("Q42|https://www.wikidata.org/wiki/Q42",
                source.fields().read("qid"));
    }

    private static final class Item extends ViewableAdapter {
        @Override public String getIdentifier() { return "item"; }
        @Override public String getDisplayName() { return "Item"; }
        @Override public FieldSet fields() { return FieldSet.of(this); }
    }

    private static final class TestSource extends Source {
        private TestSource(String qid) {
            super("Wikidata", qid, "https://www.wikidata.org/wiki/" + qid);
        }
    }
}
