package objectview.viewconfig;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ViewConfigEditorPathTest {

    @Test void resolvesSubtypeConfigKeyWithoutExposingItAsAFieldSegment() {
        ViewConfigEditor.ResolvedFieldPath path = ViewConfigEditor.resolveFieldPath(
                "State", "@subtype:USState.admissionDate.year");

        assertEquals("USState", path.owner());
        assertEquals("admissionDate.year", path.path());
    }

    @Test void ordinaryPathKeepsItsBaseOwner() {
        ViewConfigEditor.ResolvedFieldPath path =
                ViewConfigEditor.resolveFieldPath("State", "capital.name");

        assertEquals("State", path.owner());
        assertEquals("capital.name", path.path());
    }
}
