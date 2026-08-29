package objectview.render;

import objectview.ViewableAdapter;
import objectview.group.DefaultViewableGroup;
import org.junit.jupiter.api.Test;

import javax.swing.JLabel;
import javax.swing.JSplitPane;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GroupMembersViewTest {
    @Test void oneComponentOwnsTreeSelectionAndMemberReplacement() {
        Group root = new Group("All");
        Group child = root.getOrCreateChild("Entity relations");
        child.addMember(new Item("P31"), false);
        AtomicReference<String> rendered = new AtomicReference<>();
        AtomicReference<String> selected = new AtomicReference<>();

        GroupMembersView view = new GroupMembersView(
                root,
                group -> {
                    rendered.set(group.getDisplayName());
                    return new JLabel(group.getDisplayName());
                },
                JSplitPane.HORIZONTAL_SPLIT, false, 0.25, false);
        view.setSelectionHandler(group -> selected.set(group.getDisplayName()));

        assertTrue(view.selectGroup(child, true));
        assertEquals("Entity relations", selected.get());
        assertEquals("Entity relations", rendered.get());
    }

    private static final class Group extends DefaultViewableGroup<Item, Group> {
        private Group(String name) { super(name); }
        @Override protected Group newChild(String name) { return new Group(name); }
    }

    private static final class Item extends ViewableAdapter {
        private final String id;
        private Item(String id) { this.id = id; }
        @Override public String getIdentifier() { return id; }
        @Override public String getDisplayName() { return id; }
    }
}
