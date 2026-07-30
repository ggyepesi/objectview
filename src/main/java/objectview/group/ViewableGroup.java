package objectview.group;

import objectview.Viewable;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A named hierarchical grouping of {@link Viewable} members — the READ contract the
 * rendering side depends on. Mutation lives in {@link MutableViewableGroup}.
 *
 * @param <T> member type
 */
public interface ViewableGroup<T extends Viewable> extends Viewable {

    /**
     * What a node means in a faceted tree:
     * <ul>
     *   <li>{@code UNIVERSE} — the root: all members (a valid scope);</li>
     *   <li>{@code FACET} — a dimension header (League, City): holds the whole
     *       universe transitively, so it's not a useful filter — the UI shows it
     *       as a non-selectable header;</li>
     *   <li>{@code BUCKET} — one facet value (NBA, Boston): the real subset.</li>
     * </ul>
     * Hand-built trees leave every node {@code UNIVERSE} (the default).
     */
    enum Role { UNIVERSE, FACET, BUCKET }

    String getIdentifier();

    String getDisplayName();

    Role getRole();

    /** For a reference bucket: the entity this bucket stands for (its members share
     *  it), so the UI can show that entity's own card. Any Viewable, not necessarily
     *  a member. */
    Viewable getKeyRef();

    Collection<? extends ViewableGroup<T>> getChildren();

    Collection<T> getMembers();

    ViewableGroup<T> getParent();

    String getFullName();

    // ---- derived reads (pure functions of getChildren()/getMembers()) -------------------
    // Defaults so every implementation shares one definition; a concrete group only supplies
    // its children/members/parent and these fall out. MutableViewableGroup re-declares the
    // child-returning ones covariantly (returning its self-type G).

    /** The child whose display name is {@code name}, or null. */
    default ViewableGroup<T> getChild(String name) {
        if (name == null) {
            return null;
        }
        for (ViewableGroup<T> child : getChildren()) {
            if (child != null && name.equals(child.getDisplayName())) {
                return child;
            }
        }
        return null;
    }

    /** Children keyed by display name (first wins). */
    default Map<String, ? extends ViewableGroup<T>> getChildrenMap() {
        Map<String, ViewableGroup<T>> map = new LinkedHashMap<>();
        for (ViewableGroup<T> child : getChildren()) {
            if (child != null) {
                map.putIfAbsent(child.getDisplayName(), child);
            }
        }
        return map;
    }

    /** Members keyed by identifier (first wins). */
    default Map<String, T> getMemberMap() {
        Map<String, T> map = new LinkedHashMap<>();
        for (T member : getMembers()) {
            if (member != null) {
                map.putIfAbsent(member.getIdentifier(), member);
            }
        }
        return map;
    }

    default boolean contains(String memberName) {
        return getMemberMap().containsKey(memberName);
    }
}
