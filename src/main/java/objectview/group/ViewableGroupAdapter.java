package objectview.group;

import objectview.Viewable;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A plain {@link ViewableGroup} base with materialized storage — the non-CRTP counterpart of
 * {@link DefaultViewableGroup}. Computed groups (a facet result, an operation result) extend
 * this: they populate {@code children}/{@code members} through their own {@code reproduce(...)}
 * and add a rule + member class. Buckets stay ordinary {@code ViewableGroup}s (no self-type),
 * and the derived reads come from the {@link ViewableGroup} interface defaults.
 */
public abstract class ViewableGroupAdapter implements ViewableGroup<Viewable> {

    private final String identifier;
    private final String displayName;
    private Role role = Role.UNIVERSE;
    private Viewable keyRef;
    private ViewableGroup<Viewable> parent;
    private final Map<String, ViewableGroup<Viewable>> children = new LinkedHashMap<>();
    private final Map<String, Viewable> members = new LinkedHashMap<>();

    protected ViewableGroupAdapter(String identifier, String displayName) {
        this.identifier = identifier;
        this.displayName = displayName == null ? identifier : displayName;
    }

    @Override public String getIdentifier() { return identifier; }
    @Override public String getDisplayName() { return displayName; }
    @Override public Role getRole() { return role; }
    @Override public Viewable getKeyRef() { return keyRef; }
    @Override public ViewableGroup<Viewable> getParent() { return parent; }

    @Override public Collection<ViewableGroup<Viewable>> getChildren() {
        return List.copyOf(children.values());
    }

    @Override public Collection<Viewable> getMembers() {
        return List.copyOf(members.values());
    }

    @Override public String getFullName() {
        return parent == null
                ? displayName
                : parent.getFullName() + "/" + displayName;
    }

    // ---- mutators for subclasses (e.g. reproduce() repopulating from a rule) -------------
    protected void role(Role role) { this.role = role == null ? Role.UNIVERSE : role; }
    protected void keyRef(Viewable keyRef) { this.keyRef = keyRef; }
    protected void parent(ViewableGroup<Viewable> parent) { this.parent = parent; }

    protected void putChild(ViewableGroup<Viewable> child) {
        if (child != null) {
            children.put(child.groupKey(), child);
        }
    }

    protected void putMember(Viewable member) {
        if (member != null) {
            members.putIfAbsent(member.getIdentifier(), member);
        }
    }

    protected void clearChildren() { children.clear(); }
    protected void clearMembers() { members.clear(); }
    protected void removeChild(String displayName) { children.remove(displayName); }

}
