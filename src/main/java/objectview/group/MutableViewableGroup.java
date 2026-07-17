package objectview.group;

import objectview.Viewable;

import java.util.Collection;
import java.util.Map;

/**
 * The MUTATE contract of a {@link ViewableGroup}, self-typed in {@code G} so a
 * concrete leaf (a host's own group class) sees its own type flow through the
 * fluent/child-returning methods — no casts, no covariance gap. A builder like
 * {@link objectview.facet.FacetGrouper} works against {@code G} so it never names a
 * host's concrete group.
 *
 * @param <T> member type
 * @param <G> the concrete self-type of this group
 */
public interface MutableViewableGroup<
        T extends Viewable,
        G extends MutableViewableGroup<T, G>>
        extends ViewableGroup<T> {

    G getOrCreateChild(String name);

    void addChild(G child);

    void addMember(T member);

    default void addMembers(Collection<? extends T> members) {
        if (members == null) {
            return;
        }
        for (T member : members) {
            addMember(member);
        }
    }

    G role(Role role);

    G keyRef(Viewable keyRef);

    // Covariant reads: a builder working with the concrete G sees G-typed children /
    // parent (the read interface only promises ViewableGroup<T>).
    @Override
    G getChild(String name);

    @Override
    Collection<G> getChildren();

    @Override
    Map<String, G> getChildrenMap();

    @Override
    G getParent();
}
