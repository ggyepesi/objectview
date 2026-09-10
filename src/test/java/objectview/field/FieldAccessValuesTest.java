package objectview.field;

import objectview.ViewableAdapter;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FieldAccessValuesTest {
    @Test void nestedCollectionPathPreservesEveryLeafValue() {
        Parent parent = new Parent(List.of(new Child("A"), new Child("B")));

        assertEquals(List.of("A", "B"),
                FieldAccess.getPathValues(
                        parent, FieldPath.of("children", "name")));
    }

    @Test void nestedReadsUseTheDeclaredSchemaForAnUnstoredDisplayField() {
        SchemaChild first = new SchemaChild("First child");
        SchemaChild second = new SchemaChild("Second child");
        SchemaParent parent = new SchemaParent(List.of(first, second));
        FieldSchema childSchema = () -> List.of(FieldRef.computed(
                "caption", "Caption", FieldKind.TEXT, FieldRole.DISPLAY));

        assertEquals(null, FieldAccess.getPathValues(
                parent, FieldPath.of("children", "caption")),
                "the backing alone does not declare caption");
        assertEquals(List.of("First child", "Second child"),
                FieldAccess.getPathValues(parent,
                        FieldPath.of("children", "caption"),
                        viewable -> viewable instanceof SchemaChild
                                ? childSchema : null));
    }

    private static final class Parent extends ViewableAdapter {
        private final List<Child> children;
        private Parent(List<Child> children) { this.children = children; }
        @Override public String getIdentifier() { return "parent"; }
        @Override public String getDisplayName() { return "Parent"; }
    }

    private static final class Child extends ViewableAdapter {
        private final String name;
        private Child(String name) { this.name = name; }
        @Override public String getIdentifier() { return name; }
        @Override public String getDisplayName() { return name; }
    }

    private static final class SchemaParent extends ViewableAdapter {
        private final List<SchemaChild> children;
        private SchemaParent(List<SchemaChild> children) { this.children = children; }
        @Override public String getIdentifier() { return "parent"; }
        @Override public String getDisplayName() { return "Parent"; }
    }

    private static final class SchemaChild extends ViewableAdapter
            implements DynamicFields {
        private final String display;
        private final Map<String, Object> values = new LinkedHashMap<>();
        private SchemaChild(String display) { this.display = display; }
        @Override public String getIdentifier() { return display; }
        @Override public String getDisplayName() { return display; }
        @Override public Map<String, Object> dynamicFieldValues() { return values; }
    }
}
