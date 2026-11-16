package objectview.render;

import objectview.field.FieldPath;
import objectview.field.LinkedTextSequence;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LinkedTextSequenceTest {
    @Test void oneTextRowRetainsIndependentDestinationsAndOrdinaryTextBehaviour() {
        LinkedTextSequence value = LinkedTextSequence.of(
                LinkedTextSequence.link("Q1", "https://example.test/Q1"),
                LinkedTextSequence.text(" — "),
                LinkedTextSequence.link("P40", "https://example.test/P40"),
                LinkedTextSequence.text(" → literal"));

        TextRow row = new TextRow("Statement", FieldPath.ROOT, value);

        assertEquals("Q1 — P40 → literal", value.toString());
        assertEquals(List.of("https://example.test/Q1", "https://example.test/P40"),
                row.linkedUrls());
        assertTrue(row.matchesRenderedText(List.of("P40", "literal"), false),
                "linked text remains ordinary searchable text");
    }
}
