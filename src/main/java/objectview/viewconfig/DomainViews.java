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
        return getGroupRootBindings().stream().map(DomainGroupRoot::root).toList();
    }

    /** Standalone browser over the domain's declared group roots. */
    default GroupView getGroupView() {
        ViewableGroup<?> root = MultiRootGroup.of(getRootGroups(), "All");
        return root == null ? null : new GroupView(root);
    }

    /** Explicit member-type/root associations. */
    List<DomainGroupRoot> getGroupRootBindings();
}
