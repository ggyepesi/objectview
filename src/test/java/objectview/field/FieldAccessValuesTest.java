package objectview.field;

import objectview.ViewableAdapter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FieldAccessValuesTest {
    @Test void nestedCollectionPathPreservesEveryLeafValue() {
        Parent parent = new Parent(List.of(new Child("A"), new Child("B")));

        assertEquals(List.of("A", "B"),
                FieldAccess.getPathValues(
                        parent, FieldPath.of("children", "name")));
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
}
