package objectview.viewconfig;

import objectview.field.FieldPath;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ViewConfigEditorPathTest {

    @Test void resolvesSubtypeConfigKeyWithoutExposingItAsAFieldSegment() {
        ViewConfigEditor.ResolvedFieldPath path = ViewConfigEditor.resolveFieldPath(
                "State", FieldPath.parse(
                        "@subtype:USState.admissionDate.year"));

        assertEquals("USState", path.owner());
        assertEquals(FieldPath.parse("admissionDate.year"), path.path());
    }

    @Test void ordinaryPathKeepsItsBaseOwner() {
        ViewConfigEditor.ResolvedFieldPath path =
                ViewConfigEditor.resolveFieldPath(
                        "State", FieldPath.parse("capital.name"));

        assertEquals("State", path.owner());
        assertEquals(FieldPath.parse("capital.name"), path.path());
    }
}
