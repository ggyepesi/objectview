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

    @Test void hierarchyAndOrdinaryGroupFieldsExpandIndependently() {
        Group root = new Group("All");
        Group child = root.getOrCreateChild("Entity relations");
        child.addMember(new Item("P31"), false);
        GroupTreeView tree = new GroupTreeView(root);

        assertTrue(tree.isGroupExpanded(root));
        assertTrue(tree.setGroupExpanded(root, false));
        assertTrue(tree.setGroupDetailsExpanded(root, true));

        assertTrue(tree.isGroupDetailsExpanded(root));
        assertTrue(!tree.isGroupExpanded(root));
        assertTrue(tree.groupDetailsComponent(root) instanceof Card);
        Card card = (Card) tree.groupDetailsComponent(root);
        assertEquals(root, card.renderedInstance());
        assertTrue(card.getComponentCount() > 0,
                "the details disclosure must contain rendered ObjectView fields");
        assertTrue(card.getPreferredSize().height > 0);
    }

    @Test void groupFieldsUseTheirHostsSharedRenderContext() {
        Group root = new Group("All");
        RenderContext shared = new RenderContext();
        GroupTreeView tree = new GroupTreeView(root, shared);

        assertEquals(shared, tree.detailsRenderContext(),
                "links and references in group fields obey the surrounding view");
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

    @Test void enterShowsTheSelectedGroupsInstances() {
        Group root = new Group("All");
        Group child = root.getOrCreateChild("Olympians");
        child.addMember(new Item("Q34201"), false);
        GroupTreeView tree = new GroupTreeView(root);
        AtomicReference<objectview.group.ViewableGroup<?>> shown = new AtomicReference<>();
        tree.setShowGroupHandler(shown::set);

        assertTrue(tree.selectGroup(child, false));
        javax.swing.Action enter = tree.getTree().getActionMap().get("showGroup");
        enter.actionPerformed(new java.awt.event.ActionEvent(
                tree.getTree(), java.awt.event.ActionEvent.ACTION_PERFORMED, "ENTER"));

        assertEquals(child, shown.get());
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
