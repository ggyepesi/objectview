package objectview.render;

import objectview.group.ViewableGroup;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import java.awt.BorderLayout;
import java.util.Objects;
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
        super(new BorderLayout());
        this.memberView = Objects.requireNonNull(memberView, "memberView");
        this.showOnSelection = showOnSelection;
        groups = new GroupTreeView(Objects.requireNonNull(root, "root"));
        groups.setShowGroupHandler(this::showGroup);
        groups.setSelectionHandler(group -> {
            selectionHandler.accept(group);
            if (this.showOnSelection) showGroup(group);
        });
        JComponent first = membersFirst ? members : groups;
        JComponent second = membersFirst ? groups : members;
        JSplitPane split = new JSplitPane(orientation, first, second);
        split.setResizeWeight(resizeWeight);
        split.setOneTouchExpandable(true);
        add(split, BorderLayout.CENTER);
    }

    public GroupTreeView groups() { return groups; }

    public void setSelectionHandler(Consumer<ViewableGroup<?>> handler) {
        selectionHandler = handler == null ? ignored -> {} : handler;
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
