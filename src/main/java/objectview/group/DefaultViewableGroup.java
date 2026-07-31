package objectview.group;

import objectview.Viewable;
import objectview.ViewableAdapter;
import objectview.annotations.Minor;
import objectview.annotations.Reference;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/**
 * Default reflection-backed implementation of a {@link MutableViewableGroup},
 * self-typed in {@code G} so a concrete subclass's child/fluent methods return the
 * subclass type. Abstract because it can't {@code new} a {@code G} — a leaf supplies
 * {@link #newChild(String)} (a host's own group class supplies the concrete type).
 *
 * @param <T> member type
 * @param <G> the concrete self-type
 */
public abstract class DefaultViewableGroup<
        T extends Viewable,
        G extends DefaultViewableGroup<T, G>>
        extends ViewableAdapter
        implements MutableViewableGroup<T, G> {

    private final String name;

    // The facet-tree role is transform structure, not data — hidden so it doesn't
    // render as a `role: FACET` row mixed into the results.
    @Minor
    private Role role = Role.UNIVERSE;

    @Minor
    private Viewable keyRef;

    // A normal object reference. Cycles are handled by the shared identity/reference
    // graph machinery, just like Language <-> LanguageFamily.
    @Reference
    private G parent;

    @Reference
    private final Map<String, G> children = new TreeMap<>();

    @Reference
    private final Map<String, T> members = new TreeMap<>();

    protected DefaultViewableGroup(String name) {
        this.name = name;
    }

    @SuppressWarnings("unchecked")
    protected final G self() {
        return (G) this;
    }

    // Make this node the parent of `child`. Assigns through an upcast local (typed as
    // the declaring class) because a private field can't be reached through a G-typed
    // reference; and it's called on `this`, not on the child, for the same reason.
    private void adopt(G child) {
        DefaultViewableGroup<T, G> node = child;
        node.parent = self();
    }

    /** Create a new child group of the concrete self-type {@code G}. */
    protected abstract G newChild(String name);

    @Override
    public String getIdentifier() {
        // A local label is not an identity: two branches may both contain a child named
        // "A". Qualify it by ancestry so B/A and C/A remain distinct snapshot entities.
        if (parent == null) {
            return name;
        }
        String parentId = parent.getIdentifier();
        return parentId == null || parentId.isBlank() ? name : parentId + "." + name;
    }

    @Override
    public String getDisplayName() {
        return name;
    }

    @Override
    public String getReferenceLabel() {
        return getFullName();
    }

    @Override
    public Role getRole() {
        return role;
    }

    @Override
    public G role(Role role) {
        this.role = role == null ? Role.UNIVERSE : role;
        return self();
    }

    @Override
    public Viewable getKeyRef() {
        return keyRef;
    }

    @Override
    public G keyRef(Viewable keyRef) {
        this.keyRef = keyRef;
        return self();
    }

    @Override
    public G getOrCreateChild(String name) {
        G child = children.computeIfAbsent(name, this::newChild);
        adopt(child);
        return child;
    }

    @Override
    public void addChild(G child) {
        if (child == null || child.getIdentifier() == null) {
            return;
        }
        // The map is addressed by the LOCAL child label (getChild("A")); the child's
        // public identifier becomes ancestry-qualified after adoption.
        children.putIfAbsent(child.getDisplayName(), child);
        adopt(child);
    }

    @Override
    public void addMember(T member, boolean addToAncestors) {
        if (member == null || member.getIdentifier() == null) {
            return;
        }
        boolean addedHere = members.putIfAbsent(member.getIdentifier(), member) == null;
        if (addedHere && addToAncestors && parent != null) {
            parent.addMember(member, true);
        }
    }

    @Override
    public boolean contains(String memberName) {
        return members.containsKey(memberName);
    }

    @Override
    public G getChild(String name) {
        return children.get(name);
    }

    @Override
    public Collection<G> getChildren() {
        return children.values();
    }

    @Override
    public Map<String, G> getChildrenMap() {
        return children;
    }

    @Override
    public Collection<T> getMembers() {
        return members.values();
    }

    @Override
    public Map<String, T> getMemberMap() {
        return Collections.unmodifiableMap(members);
    }

    @Override
    public G getParent() {
        return parent;
    }

    @Override
    public String getFullName() {
        return parent == null
                ? getDisplayName()
                : parent.getFullName() + "/" + getDisplayName();
    }

    @Override
    public String toString() {
        return getDisplayName();
    }
}
