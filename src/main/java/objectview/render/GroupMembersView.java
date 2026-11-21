package objectview.render;

import objectview.Viewable;
import objectview.group.ViewableGroup;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import java.awt.BorderLayout;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Shared group tree + member-content host. The caller owns member rendering (usually a
 * virtualized searchable card list); this component owns group selection, replacement and
 * split layout so ModelBuilder and TransformApp do not compose those differently.
 */
public final class GroupMembersView extends JPanel {
    private final GroupTreeView groups;
    private final JPanel members = new JPanel(new BorderLayout());
    private final Function<ViewableGroup<?>, JComponent> memberView;
    private final boolean showOnSelection;
    private Consumer<ViewableGroup<?>> selectionHandler = ignored -> {};

    public GroupMembersView(
            ViewableGroup<?> root,
            Function<ViewableGroup<?>, JComponent> memberView,
            int orientation,
            boolean membersFirst,
            double resizeWeight,
            boolean showOnSelection) {
        this(root, memberView, orientation, membersFirst, resizeWeight,
                showOnSelection, new RenderContext());
    }

    /**
     * Builds the tree/details pane and the caller's member pane over one rendering
     * context. The caller must give that same context to its member renderer; then a
     * member reference in the group's ordinary fields navigates to the parallel
     * top-level member card instead of expanding an unrelated inline copy.
     */
    public GroupMembersView(
            ViewableGroup<?> root,
            Function<ViewableGroup<?>, JComponent> memberView,
            int orientation,
            boolean membersFirst,
            double resizeWeight,
            boolean showOnSelection,
            RenderContext renderContext) {
        super(new BorderLayout());
        this.memberView = Objects.requireNonNull(memberView, "memberView");
        this.showOnSelection = showOnSelection;
        groups = new GroupTreeView(Objects.requireNonNull(root, "root"),
                Objects.requireNonNull(renderContext, "renderContext"));
        groups.setShowGroupHandler(this::showGroup);
        groups.setSelectionHandler(group -> {
            selectionHandler.accept(group);
            if (this.showOnSelection) showGroup(group);
        });
        registerMemberRevealers(root, renderContext);
        groups.setShowButtonVisible(!showOnSelection);
        JComponent first = membersFirst ? members : groups;
        JComponent second = membersFirst ? groups : members;
        JSplitPane split = new JSplitPane(orientation, first, second);
        split.setResizeWeight(resizeWeight);
        split.setOneTouchExpandable(true);
        add(split, BorderLayout.CENTER);
    }

    /**
     * A member outside the selected group's panel still counts as a top-level target,
     * because this host can reveal it. Choose a concrete child group before its
     * aggregate parent, select that group, build its member panel, and only then let
     * {@link RenderContext#focusTopLevel(Object)} resolve the target card. A member
     * already in the lead group's panel needs no reveal: navigation then keeps the
     * current group, its member panel and any multi-group selection.
     */
    private void registerMemberRevealers(
            ViewableGroup<?> root, RenderContext renderContext) {
        Map<Viewable, ViewableGroup<?>> ownerByMember = new IdentityHashMap<>();
        collectMemberOwners(root, ownerByMember,
                Collections.newSetFromMap(new IdentityHashMap<>()));
        renderContext.addTopLevels(ownerByMember.keySet());
        ownerByMember.forEach((member, owner) ->
                renderContext.registerTopLevelRevealer(
                        member, () -> revealMemberGroup(member, owner)));
    }

    private void revealMemberGroup(Viewable member, ViewableGroup<?> owner) {
        ViewableGroup<?> current = groups.selectedGroup();
        if (current != null && current.getMembers().contains(member)) return;
        boolean changed = groups.selectedGroup() != owner;
        groups.selectGroup(owner, false);
        // A changed selection already invokes showGroup when this host follows
        // selection. Every other case needs the explicit show exactly once.
        if (!showOnSelection || !changed) showGroup(owner);
    }

    /** Post-order makes the first owner a more specific descendant rather than a root
     * whose members are the union of its children. */
    private static void collectMemberOwners(
            ViewableGroup<?> group,
            Map<Viewable, ViewableGroup<?>> ownerByMember,
            Set<ViewableGroup<?>> seen) {
        if (group == null || !seen.add(group)) return;
        for (ViewableGroup<?> child : group.getChildren()) {
            collectMemberOwners(child, ownerByMember, seen);
        }
        for (Viewable member : group.getMembers()) {
            ownerByMember.putIfAbsent(member, group);
        }
    }

    public GroupTreeView groups() { return groups; }

    public void setSelectionHandler(Consumer<ViewableGroup<?>> handler) {
        selectionHandler = handler == null ? ignored -> {} : handler;
    }

    public void setSelectionSetHandler(
            Consumer<List<ViewableGroup<?>>> handler) {
        groups.setSelectionSetHandler(handler);
    }

    public void showGroup(ViewableGroup<?> group) {
        if (group == null) return;
        JComponent content = memberView.apply(group);
        members.removeAll();
        if (content != null) members.add(content, BorderLayout.CENTER);
        members.revalidate();
        members.repaint();
    }

    public boolean selectGroup(ViewableGroup<?> group, boolean show) {
        return groups.selectGroup(group, show);
    }
}
