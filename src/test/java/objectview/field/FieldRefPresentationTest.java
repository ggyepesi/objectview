package objectview.field;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FieldRefPresentationTest {

    @Test void inlineScalarAndEmbeddedReferenceAreDifferentFacts() {
        FieldRef scalar = FieldRef.described("date", FieldKind.ORDERED,
                FieldKind.ORDERED, "FlexibleDate", false, false, null,
                false, false, true, false, "", false);
        FieldRef structured = FieldRef.described("name", FieldKind.REFERENCE,
                FieldKind.REFERENCE, "Name", true, false, "Name",
                false, false, true, false, "", false);

        assertTrue(scalar.inline());
        assertFalse(scalar.embedded());
        assertFalse(structured.inline());
        assertTrue(structured.embedded());
    }
}
