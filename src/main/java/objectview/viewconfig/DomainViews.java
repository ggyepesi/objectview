package objectview.viewconfig;


import objectview.Viewable;
import objectview.render.GroupView;
import objectview.group.ViewableGroup;
import objectview.group.MultiRootGroup;

import java.util.List;
import java.util.Map;

public interface DomainViews {
    public void buildViews() throws Exception;
    public Map<String, ? extends Viewable> getViewables();

    /** Explicit roots of the domain's group graphs. Descendants are ordinary
     * references reachable through {@link ViewableGroup#getChildren()}. */
    default List<? extends ViewableGroup<?>> getRootGroups() {
        return List.of();
    }

    /** Standalone compatibility browser; domains expose model roots, not stored Swing UI. */
    default GroupView getGroupView() {
        ViewableGroup<?> root = MultiRootGroup.of(getRootGroups(), "All");
        return root == null ? null : new GroupView(root);
    }

    /** Explicit member-type/root associations. Legacy builders with one primary member
     *  type are adapted from their viewables; new multi-type builders should override. */
    default List<DomainGroupRoot> getGroupRootBindings() {
        String memberType = getViewables().values().stream()
                .filter(java.util.Objects::nonNull)
                .map(Viewable::typeName)
                .findFirst().orElse(null);
        if (memberType == null) {
            return List.of();
        }
        return getRootGroups().stream()
                .filter(java.util.Objects::nonNull)
                .map(root -> new DomainGroupRoot(memberType, root))
                .toList();
    }
}
