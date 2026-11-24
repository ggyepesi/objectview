package objectview.render;

import objectview.ViewableAdapter;
import objectview.group.DefaultViewableGroup;
import objectview.group.GroupNode;
import org.junit.jupiter.api.Test;

import javax.swing.JLabel;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
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

    @Test void structuralGroupsCanExposeTheirNestedHierarchyInitially() {
        ExpandingGroup root = new ExpandingGroup("All characters");
        ExpandingGroup giant = root.getOrCreateChild("Giants");
        giant.getOrCreateChild("Jotunn");

        GroupTreeView tree = new GroupTreeView(root);

        assertEquals(3, tree.getTree().getRowCount());
        assertTrue(tree.isGroupExpanded(giant));
    }

    @Test void enabledGroupFieldsFollowTheSelectedTreeNode() {
        Group root = new Group("All");
        Group child = root.getOrCreateChild("Entity relations");
        GroupTreeView tree = new GroupTreeView(root);

        assertTrue(tree.setGroupDetailsExpanded(root, true));
        assertTrue(tree.selectGroup(child, false));

        assertTrue(tree.isGroupDetailsExpanded(child),
                "Show group fields is one toggle whose card follows selection");
        assertEquals(child, ((Card) tree.groupDetailsComponent(child)).renderedInstance());
    }

    @Test void groupFieldsUseTheirHostsSharedRenderContext() {
        Group root = new Group("All");
        RenderContext shared = new RenderContext();
        GroupTreeView tree = new GroupTreeView(root, shared);

        assertEquals(shared, tree.detailsRenderContext(),
                "links and references in group fields obey the surrounding view");
    }

    @Test void groupAndParallelMembersCanUseOneRenderContext() {
        Group root = new Group("All");
        RenderContext shared = new RenderContext();
        GroupMembersView view = new GroupMembersView(
                root, ignored -> new JLabel("members"), JSplitPane.HORIZONTAL_SPLIT,
                false, 0.3, true, shared);

        assertEquals(shared, view.groups().detailsRenderContext(),
                "member references in group fields must see the member panel's cards");
    }

    @Test void aReferenceToAnotherGroupRevealsItsMemberPanelBeforeNavigation() {
        Group root = new Group("All");
        Group first = root.getOrCreateChild("First group");
        Group second = root.getOrCreateChild("Second group");
        Item one = new Item("Q1");
        Item two = new Item("Q2");
        first.addMember(one, true);
        second.addMember(two, true);
        RenderContext shared = new RenderContext();
        AtomicReference<objectview.group.ViewableGroup<?>> shown = new AtomicReference<>();
        GroupMembersView view = new GroupMembersView(
                root,
                group -> {
                    shown.set(group);
                    return new JLabel(group.getDisplayName());
                },
                JSplitPane.HORIZONTAL_SPLIT, false, 0.3, true, shared);
        shared.addTopLevelResolver(target -> shown.get() != null
                && shown.get().getMembers().contains(target) ? new JPanel() : null);

        assertTrue(view.selectGroup(first, true));
        assertTrue(shared.focusTopLevel(two));

        assertEquals(second, view.groups().selectedGroup(),
                "navigation selects a concrete group that contains the target");
        assertEquals(second, shown.get(),
                "the target group's member panel exists before card resolution");
    }

    @Test void aReferenceToAMemberAlreadyShownKeepsTheCurrentGroup() {
        Group root = new Group("All");
        Group first = root.getOrCreateChild("First group");
        Group second = root.getOrCreateChild("Second group");
        Item one = new Item("Q1");
        Item two = new Item("Q2");
        first.addMember(one, true);
        second.addMember(two, true);
        RenderContext shared = new RenderContext();
        AtomicReference<objectview.group.ViewableGroup<?>> shown = new AtomicReference<>();
        java.util.concurrent.atomic.AtomicInteger builds =
                new java.util.concurrent.atomic.AtomicInteger();
        GroupMembersView view = new GroupMembersView(
                root,
                group -> {
                    shown.set(group);
                    builds.incrementAndGet();
                    return new JLabel(group.getDisplayName());
                },
                JSplitPane.HORIZONTAL_SPLIT, false, 0.3, true, shared);
        shared.addTopLevelResolver(target -> shown.get() != null
                && shown.get().getMembers().contains(target) ? new JPanel() : null);
        view.groups().setMultipleSelectionEnabled(true);

        assertTrue(view.selectGroup(root, true));
        int before = builds.get();
        assertTrue(shared.focusTopLevel(two));
        assertEquals(root, view.groups().selectedGroup(),
                "the overview already shows the target; navigation must not narrow it");
        assertEquals(before, builds.get(), "the shown member panel is not rebuilt");

        view.groups().getTree().setSelectionRows(new int[] { 1, 2 });
        java.util.List<objectview.group.ViewableGroup<?>> batch =
                view.groups().selectedGroups();
        Object leadMember = view.groups().selectedGroup().getMembers().iterator().next();
        assertTrue(shared.focusTopLevel(leadMember));
        assertEquals(batch, view.groups().selectedGroups(),
                "following a link to a shown member keeps a multi-group selection");
    }

    @Test void manualShowUsesTheDisplayedGroupWhenRevealingAReference() {
        Group root = new Group("All");
        Group first = root.getOrCreateChild("First group");
        Group second = root.getOrCreateChild("Second group");
        Item one = new Item("Q1");
        Item two = new Item("Q2");
        first.addMember(one, true);
        second.addMember(two, true);
        RenderContext shared = new RenderContext();
        AtomicReference<objectview.group.ViewableGroup<?>> shown = new AtomicReference<>();
        GroupMembersView view = new GroupMembersView(
                root,
                group -> {
                    shown.set(group);
                    return new JLabel(group.getDisplayName());
                },
                JSplitPane.HORIZONTAL_SPLIT, false, 0.3, false, shared);
        shared.addTopLevelResolver(target -> shown.get() != null
                && shown.get().getMembers().contains(target) ? new JPanel() : null);

        assertTrue(view.selectGroup(first, true));
        assertTrue(view.selectGroup(second, false));
        assertEquals(first, shown.get(),
                "selection alone does not replace an explicit Show instances panel");

        assertTrue(shared.focusTopLevel(two));
        assertEquals(second, shown.get(),
                "navigation builds the selected target's actual member panel first");
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

    @Test void batchSelectionKeepsOneLeadGroupForInspection() {
        Group root = new Group("All");
        Group first = root.getOrCreateChild("First group");
        Group second = root.getOrCreateChild("Second group");
        AtomicReference<java.util.List<objectview.group.ViewableGroup<?>>> selected =
                new AtomicReference<>(java.util.List.of());
        AtomicReference<objectview.group.ViewableGroup<?>> rendered =
                new AtomicReference<>();
        GroupMembersView view = new GroupMembersView(
                root, group -> {
                    rendered.set(group);
                    return new JLabel(group.getDisplayName());
                },
                JSplitPane.HORIZONTAL_SPLIT, false, 0.3, true);
        view.groups().setMultipleSelectionEnabled(true);
        view.setSelectionSetHandler(selected::set);

        view.groups().getTree().setSelectionRows(new int[] { 1, 2 });

        assertEquals(java.util.List.of(first, second), selected.get());
        assertTrue(selected.get().contains(view.groups().selectedGroup()));
        assertEquals(view.groups().selectedGroup(), rendered.get(),
                "one lead node still drives the parallel inspection panel");
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

    @Test void groupNameSearchNavigatesTheOutlineWithoutInspectingMemberFields() {
        Group root = new Group("All");
        root.getOrCreateChild("Aesir");
        Group ravens = root.getOrCreateChild("Hugin and Munin");
        GroupTreeView tree = new GroupTreeView(root);

        find(tree, JTextField.class).setText("munin");

        assertEquals(ravens, tree.selectedGroup());
        assertEquals("1 of 1", labels(tree).stream()
                .map(JLabel::getText).filter("1 of 1"::equals).findFirst().orElseThrow());
    }

    @Test void sortingGroupNamesIsAReversibleOutlineChoice() {
        OrderedGroup root = new OrderedGroup("All");
        OrderedGroup vanir = root.child("Vanir");
        OrderedGroup aesir = root.child("Aesir");
        GroupTreeView tree = new GroupTreeView(root);

        assertEquals(vanir, groupAt(tree, 1));
        findToggle(tree, "Sort groups by name").doClick();
        assertEquals(aesir, groupAt(tree, 1));
        findToggle(tree, "Sort groups by name").doClick();
        assertEquals(vanir, groupAt(tree, 1));
    }

    @Test void aFieldValueGroupAppearsBelowItsUniqueStrictMemberSuperset() {
        Group root = new Group("All characters");
        Group giants = root.getOrCreateChild("Giants");
        Group jotunn = root.getOrCreateChild("Jotunn");
        Item ymir = new Item("Q214081");
        Item thrym = new Item("Q132587");
        giants.addMember(ymir, false);
        giants.addMember(thrym, false);
        jotunn.addMember(thrym, false);

        GroupTreeView tree = new GroupTreeView(root);
        assertTrue(tree.setGroupExpanded(giants, true));
        javax.swing.tree.TreePath jotunnPath = tree.getTree().getPathForRow(2);

        assertEquals(giants, groupAt(tree, 1));
        assertEquals(jotunn, tree.getViewableGroup(
                (javax.swing.tree.DefaultMutableTreeNode)
                        jotunnPath.getLastPathComponent()));
        assertEquals(giants, tree.getViewableGroup(
                (javax.swing.tree.DefaultMutableTreeNode)
                        jotunnPath.getParentPath().getLastPathComponent()));
    }

    @Test void competingEqualSizeSupersetsDoNotInventAParent() {
        Group root = new Group("All characters");
        Group giants = root.getOrCreateChild("Giants");
        Group frost = root.getOrCreateChild("Frost beings");
        Group jotunn = root.getOrCreateChild("Jotunn");
        Item thrym = new Item("Q132587");
        giants.addMember(thrym, false);
        giants.addMember(new Item("Q214081"), false);
        frost.addMember(thrym, false);
        frost.addMember(new Item("Q1"), false);
        jotunn.addMember(thrym, false);

        GroupTreeView tree = new GroupTreeView(root);

        assertEquals(4, tree.getTree().getRowCount());
        assertEquals(jotunn, groupAt(tree, 3));
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

    private static objectview.group.ViewableGroup<?> groupAt(
            GroupTreeView tree, int row) {
        javax.swing.tree.DefaultMutableTreeNode node =
                (javax.swing.tree.DefaultMutableTreeNode) tree.getTree()
                        .getPathForRow(row).getLastPathComponent();
        return tree.getViewableGroup(node);
    }

    private static JToggleButton findToggle(Container root, String text) {
        for (Component child : root.getComponents()) {
            if (child instanceof JToggleButton button && text.equals(button.getText())) {
                return button;
            }
            if (child instanceof Container nested) {
                JToggleButton found = findToggleOrNull(nested, text);
                if (found != null) return found;
            }
        }
        throw new AssertionError("No toggle: " + text);
    }

    private static JToggleButton findToggleOrNull(Container root, String text) {
        for (Component child : root.getComponents()) {
            if (child instanceof JToggleButton button && text.equals(button.getText())) {
                return button;
            }
            if (child instanceof Container nested) {
                JToggleButton found = findToggleOrNull(nested, text);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static java.util.List<JLabel> labels(Container root) {
        java.util.List<JLabel> result = new java.util.ArrayList<>();
        for (Component child : root.getComponents()) {
            if (child instanceof JLabel label) result.add(label);
            if (child instanceof Container nested) result.addAll(labels(nested));
        }
        return result;
    }

    private static <T extends Component> T find(Container root, Class<T> type) {
        for (Component child : root.getComponents()) {
            if (type.isInstance(child)) return type.cast(child);
            if (child instanceof Container nested) {
                T found = findOrNull(nested, type);
                if (found != null) return found;
            }
        }
        throw new AssertionError("No " + type.getSimpleName());
    }

    private static <T extends Component> T findOrNull(Container root, Class<T> type) {
        for (Component child : root.getComponents()) {
            if (type.isInstance(child)) return type.cast(child);
            if (child instanceof Container nested) {
                T found = findOrNull(nested, type);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static final class Group extends DefaultViewableGroup<Item, Group> {
        private Group(String name) { super(name); }
        @Override protected Group newChild(String name) { return new Group(name); }
    }

    private static final class ExpandingGroup
            extends DefaultViewableGroup<Item, ExpandingGroup> {
        private ExpandingGroup(String name) { super(name); }
        @Override protected ExpandingGroup newChild(String name) {
            return new ExpandingGroup(name);
        }
        @Override public boolean initiallyExpanded() { return true; }
    }

    private static final class OrderedGroup
            extends objectview.group.ViewableGroupAdapter {
        private OrderedGroup(String name) { super(name, name); }
        private OrderedGroup child(String name) {
            OrderedGroup child = new OrderedGroup(name);
            child.parent(this);
            putChild(child);
            return child;
        }
    }

    private static final class Item extends ViewableAdapter {
        private final String id;
        private Item(String id) { this.id = id; }
        @Override public String getIdentifier() { return id; }
        @Override public String getDisplayName() { return id; }
    }
}
