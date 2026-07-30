package objectview.viewconfig;


import objectview.Viewable;
import objectview.render.GroupView;
import objectview.group.ViewableGroup;

import java.util.List;
import java.util.Map;

public interface DomainViews {
    public void buildViews() throws Exception;
    public GroupView getGroupView();
    public Map<String, ? extends Viewable> getViewables();

    /** Explicit roots of the domain's group graphs. Descendants are ordinary
     * references reachable through {@link ViewableGroup#getChildren()}. */
    default List<? extends ViewableGroup<?>> getRootGroups() {
        GroupView view = getGroupView();
        return view == null ? List.of() : List.of(view.getRootGroup());
    }
}
