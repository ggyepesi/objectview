package objectview.group;

import objectview.Viewable;

import java.util.Collection;
import java.util.Map;

/**
 * A named hierarchical grouping of {@link Viewable} members — the READ contract the
 * rendering side depends on. Mutation lives in {@link MutableViewableGroup}.
 *
 * @param <T> member type
 */
public interface ViewableGroup<T extends Viewable> {

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

    ViewableGroup<T> getChild(String name);

    Collection<? extends ViewableGroup<T>> getChildren();

    Map<String, ? extends ViewableGroup<T>> getChildrenMap();

    Collection<T> getMembers();

    Map<String, T> getMemberMap();

    ViewableGroup<T> getParent();

    String getFullName();

    boolean contains(String memberName);
}
