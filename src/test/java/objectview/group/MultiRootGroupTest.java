package objectview.group;

import objectview.ViewableAdapter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class MultiRootGroupTest {

    private static final class Item extends ViewableAdapter {
        private final String name;
        private Item(String name) { this.name = name; }
        @Override public String getIdentifier() { return name; }
        @Override public String getDisplayName() { return name; }
    }

    @Test void doesNotInferSyntheticRootMembershipFromChildren() {
        TestGroup first = new TestGroup("first");
        TestGroup second = new TestGroup("second");
        first.member(new Item("one"));
        second.member(new Item("two"));

        ViewableGroup<?> root = MultiRootGroup.of(List.of(first, second), "All");

        assertEquals(List.of(), root.getMembers());
        assertEquals(List.of(first, second), root.getChildren());
    }

    @Test void acceptsAnExplicitSyntheticRootScope() {
        TestGroup first = new TestGroup("first");
        TestGroup second = new TestGroup("second");
        Item all = new Item("all");

        ViewableGroup<?> root = MultiRootGroup.of(
                List.of(first, second), "All", List.of(all));

        assertEquals(List.of(all), root.getMembers());
    }

    @Test void aSingleNaturalRootIsReturnedUnchanged() {
        TestGroup only = new TestGroup("only");
        assertSame(only, MultiRootGroup.of(
                List.of(only), "All", List.of(new Item("ignored"))));
    }

    @Test void childMembershipDoesNotPropagateToItsParent() {
        MutableTestGroup root = new MutableTestGroup("root");
        MutableTestGroup child = root.getOrCreateChild("child");
        Item item = new Item("member");

        child.addMember(item, false);

        assertEquals(List.of(item), child.getMembers().stream().toList());
        assertEquals(List.of(), root.getMembers().stream().toList());
    }

    @Test void defaultAddMemberPreservesAncestorPropagation() {
        MutableTestGroup root = new MutableTestGroup("root");
        MutableTestGroup child = root.getOrCreateChild("child");
        Item item = new Item("member");

        child.addMember(item);

        assertEquals(List.of(item), child.getMembers().stream().toList());
        assertEquals(List.of(item), root.getMembers().stream().toList());
    }

    private static final class TestGroup extends ViewableGroupAdapter {
        private TestGroup(String name) { super(name, name); }
        private void member(Item item) { putMember(item); }
    }

    private static final class MutableTestGroup
            extends DefaultViewableGroup<Item, MutableTestGroup> {
        private MutableTestGroup(String name) { super(name); }
        @Override protected MutableTestGroup newChild(String name) {
            return new MutableTestGroup(name);
        }
    }
}
