package objectview.field;

import objectview.media.MediaValue;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** A media value (a renderable image, resolved its own way) classifies as MEDIA, by
 *  its declared type and by a runtime value — so schema/coverage recognize it
 *  uniformly across typed and dynamic domains without knowing the concrete class. */
class FieldKindTest {

    /** A minimal MediaValue standing in for a State flag / dynamic image value. */
    private static final class Flag implements MediaValue {
        @Override public String mediaUrl() { return "https://x/flag.svg"; }
        @Override public String mediaLabel() { return "flag"; }
        @Override public boolean mediaSvg() { return true; }
    }

    @Test
    void mediaValueClassifiesAsMedia() {
        assertEquals(FieldKind.MEDIA, FieldKind.ofClass(Flag.class));
        assertEquals(FieldKind.MEDIA, FieldKind.ofValue(new Flag()));
    }

    @Test
    void ordinaryShapesUnaffected() {
        assertEquals(FieldKind.TEXT, FieldKind.ofValue("x"));
        assertEquals(FieldKind.BOOLEAN, FieldKind.ofClass(Boolean.class));
        assertEquals(FieldKind.ORDERED, FieldKind.ofValue(3));
    }
}
