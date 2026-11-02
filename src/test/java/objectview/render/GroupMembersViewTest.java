package objectview.render;

import objectview.ViewableAdapter;
import objectview.group.DefaultViewableGroup;
import objectview.group.GroupNode;
import org.junit.jupiter.api.Test;

import javax.swing.JLabel;
import javax.swing.JButton;
import javax.swing.JSplitPane;
import java.awt.Component;
import java.awt.Container;
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
        assertEquals("Entity relations (1)", new GroupNode(child).toString());
    }

    @Test void selectionDrivenMemberViewHasNoRedundantShowButton() {
        Group root = new Group("All");
        Group child = root.getOrCreateChild("Colliding name");
        AtomicReference<String> rendered = new AtomicReference<>();

        GroupMembersView view = new GroupMembersView(
                root,
                group -> {
                    rendered.set(group.getDisplayName());
                    return new JLabel(group.getDisplayName());
                },
                JSplitPane.HORIZONTAL_SPLIT, false, 0.25, true);

        assertTrue(view.selectGroup(child, false));
        assertEquals("Colliding name", rendered.get());
        assertTrue(buttons(view, "Show instances").isEmpty());
    }

    private static java.util.List<JButton> buttons(Component component, String text) {
        java.util.List<JButton> found = new java.util.ArrayList<>();
        if (component instanceof JButton button && text.equals(button.getText())) {
            found.add(button);
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                found.addAll(buttons(child, text));
            }
        }
        return found;
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
