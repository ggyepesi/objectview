package objectview.field;

import objectview.ViewableAdapter;
import objectview.annotations.Label;
import objectview.annotations.Role;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReflectionFieldRoleTest {

    @Test
    void declaredFieldCarriesItsLabelAndSemanticRole() {
        FieldRef field = FieldSet.of(new Sourced()).field("source");

        assertEquals("Wikidata source", field.label());
        assertEquals(FieldRole.PROVENANCE, field.role());
        assertEquals("qid", FieldSet.of(new Sourced()).read("source"));
    }

    private static final class Sourced extends ViewableAdapter {
        @Label("Wikidata source")
        @Role(FieldRole.PROVENANCE)
        private String source = "qid";

        @Override public String getIdentifier() { return "id"; }
        @Override public String getDisplayName() { return "name"; }
    }
}
