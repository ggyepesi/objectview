package objectview.facet;

import objectview.group.MutableViewableGroup;
import objectview.Viewable;
import objectview.group.ViewableGroup.Role;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Builds or tags a group tree from members and facet declarations, replacing
 * per-dataset hand-built trees.
 *
 * <p>The implementation is generic in the member type {@code T} and the concrete
 * group type {@code G}, so it never names a host-specific concrete group. A host
 * passes its own root factory, for example {@code MyGroup::new}, and gets
 * that same concrete type back.
 *
 * <p>Typical shape:
 *
 * <pre>
 * root (UNIVERSE, all members)
 *   -> facet node (FACET, no direct members)
 *      -> bucket node (BUCKET, matching members)
 * </pre>
 *
 * <p>Roles are assigned so a UI can treat facet nodes as non-selectable headers
 * and buckets or the universe as selectable scopes.
 *
 * <p>Methods that create a root accept a {@code newRoot} factory. Methods that
 * graft onto an existing group do not need one because groups create correctly
 * typed children through {@link MutableViewableGroup#getOrCreateChild(String)}.
 */
public final class FacetGrouper {

    private FacetGrouper() {
    }

    /**
     * Builds a fresh tree containing one independent branch for each facet.
     */
    public static <
            T extends Viewable,
            G extends MutableViewableGroup<T, G>>
    G group(
            Function<String, G> newRoot,
            String rootName,
            Collection<? extends T> members,
            List<Facet<T>> facets) {

        Objects.requireNonNull(newRoot, "newRoot");
        Objects.requireNonNull(rootName, "rootName");
        Objects.requireNonNull(members, "members");

        G root = Objects.requireNonNull(
                                newRoot.apply(rootName),
                                "newRoot returned null")
                        .role(Role.UNIVERSE);

        addMembers(root, members);
        return addFacets(root, members, facets);
    }

    /**
     * Builds a fresh nested drill-down tree.
     *
     * <p>The facets are applied in order, with every facet partitioning the
     * buckets produced by the previous facet.
     */
    public static <
            T extends Viewable,
            G extends MutableViewableGroup<T, G>>
    G groupNested(
            Function<String, G> newRoot,
            String rootName,
            Collection<? extends T> members,
            List<Facet<T>> facets) {

        Objects.requireNonNull(newRoot, "newRoot");
        Objects.requireNonNull(rootName, "rootName");
        Objects.requireNonNull(members, "members");

        G root = Objects.requireNonNull(
                                newRoot.apply(rootName),
                                "newRoot returned null")
                        .role(Role.UNIVERSE);

        addMembers(root, members);
        nest(root, members, facets, 0);
        return root;
    }

    /**
     * Grafts one nested drill-down chain of facets onto an existing root.
     */
    public static <
            T extends Viewable,
            G extends MutableViewableGroup<T, G>>
    void graftNested(
            G root,
            Collection<? extends T> members,
            List<Facet<T>> chain) {

        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(members, "members");

        nest(root, members, chain, 0);
    }

    /**
     * Grafts a tree of facet dimensions onto an existing parent.
     */
    public static <
            T extends Viewable,
            G extends MutableViewableGroup<T, G>>
    void graftTree(
            G parent,
            Collection<? extends T> members,
            List<FacetTree<T>> dimensions) {

        Objects.requireNonNull(parent, "parent");
        Objects.requireNonNull(members, "members");

        if (dimensions == null || dimensions.isEmpty()) {
            return;
        }

        for (FacetTree<T> dimension : dimensions) {
            if (dimension == null || dimension.facet() == null) {
                continue;
            }

            Facet<T> facet = dimension.facet();
            G facetNode = parent
                    .getOrCreateChild(facet.label())
                    .role(Role.FACET);

            Map<String, G> buckets = new LinkedHashMap<>();
            Map<String, List<T>> bucketMembers = new LinkedHashMap<>();

            populateBuckets(
                    facetNode,
                    members,
                    facet,
                    buckets,
                    bucketMembers);

            for (Map.Entry<String, G> entry : buckets.entrySet()) {
                List<T> membersOfBucket = bucketMembers.get(entry.getKey());

                graftTree(
                        entry.getValue(),
                        membersOfBucket != null
                                ? membersOfBucket
                                : List.of(),
                        dimension.children());
            }
        }
    }

    /**
     * Grafts independent facet dimensions onto an existing root.
     *
     * <p>Each facet becomes a {@link Role#FACET} child. Its values become
     * {@link Role#BUCKET} children. Reference buckets carry their key entity.
     */
    public static <
            T extends Viewable,
            G extends MutableViewableGroup<T, G>>
    G addFacets(
            G root,
            Collection<? extends T> members,
            List<Facet<T>> facets) {

        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(members, "members");

        if (facets == null || facets.isEmpty()) {
            return root;
        }

        for (Facet<T> facet : facets) {
            if (facet == null) {
                continue;
            }

            G facetNode = root
                    .getOrCreateChild(facet.label())
                    .role(Role.FACET);

            populateBuckets(
                    facetNode,
                    members,
                    facet,
                    null,
                    null);
        }

        return root;
    }

    /**
     * Tags an existing hand-built tree by structure:
     *
     * <ul>
     *   <li>the root becomes {@link Role#UNIVERSE}</li>
     *   <li>internal descendants become {@link Role#FACET}</li>
     *   <li>leaf descendants become {@link Role#BUCKET}</li>
     * </ul>
     */
    public static <
            T extends Viewable,
            G extends MutableViewableGroup<T, G>>
    G assignRoles(G root) {

        Objects.requireNonNull(root, "root");

        assignRoles(root, 0);
        return root;
    }

    /**
     * Re-parents a root's direct children under one named facet node and then
     * assigns roles to the resulting tree.
     *
     * <p>If a child with {@code facetLabel} already exists, it is reused. That
     * node is not added beneath itself.
     */
    public static <
            T extends Viewable,
            G extends MutableViewableGroup<T, G>>
    G wrapChildrenAsFacet(
            G root,
            String facetLabel) {

        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(facetLabel, "facetLabel");

        List<G> existingChildren = new ArrayList<>(root.getChildren());
        G facet = root.getOrCreateChild(facetLabel);

        for (G child : existingChildren) {
            if (child == null || child == facet) {
                continue;
            }

            /*
             * Remove the child from the old parent before adding it to the new
             * parent. This avoids the child temporarily appearing in both maps.
             */
            root.getChildrenMap().remove(child.getIdentifier());
            facet.addChild(child);
        }

        return assignRoles(root);
    }

    /**
     * Partitions members by {@code facets[depth]} under {@code parent}, then
     * recursively applies the next facet to each resulting bucket.
     */
    private static <
            T extends Viewable,
            G extends MutableViewableGroup<T, G>>
    void nest(
            G parent,
            Collection<? extends T> members,
            List<Facet<T>> facets,
            int depth) {

        if (facets == null || depth >= facets.size()) {
            return;
        }

        Facet<T> facet = facets.get(depth);
        if (facet == null) {
            nest(parent, members, facets, depth + 1);
            return;
        }

        G facetNode = parent
                .getOrCreateChild(facet.label())
                .role(Role.FACET);

        Map<String, G> buckets = new LinkedHashMap<>();
        Map<String, List<T>> bucketMembers = new LinkedHashMap<>();

        populateBuckets(
                facetNode,
                members,
                facet,
                buckets,
                bucketMembers);

        for (Map.Entry<String, G> entry : buckets.entrySet()) {
            List<T> membersOfBucket = bucketMembers.get(entry.getKey());

            nest(
                    entry.getValue(),
                    membersOfBucket != null
                            ? membersOfBucket
                            : List.of(),
                    facets,
                    depth + 1);
        }
    }

    /**
     * Creates or reuses buckets for one facet and assigns members to them.
     *
     * @param buckets optional map receiving created buckets by key name
     * @param bucketMembers optional map receiving members grouped by key name
     */
    private static <
            T extends Viewable,
            G extends MutableViewableGroup<T, G>>
    void populateBuckets(
            G facetNode,
            Collection<? extends T> members,
            Facet<T> facet,
            Map<String, G> buckets,
            Map<String, List<T>> bucketMembers) {

        for (T member : members) {
            if (member == null) {
                continue;
            }

            Collection<FacetKey> keys = facet.keys().apply(member);
            if (keys == null || keys.isEmpty()) {
                continue;
            }

            for (FacetKey key : keys) {
                if (key == null || !key.isUsable()) {
                    continue;
                }

                G bucket = facetNode
                        .getOrCreateChild(key.name())
                        .role(Role.BUCKET);

                if (key.ref() != null) {
                    bucket.keyRef(key.ref());
                }

                bucket.addMember(member);

                if (buckets != null) {
                    buckets.putIfAbsent(key.name(), bucket);
                }

                if (bucketMembers != null) {
                    bucketMembers
                            .computeIfAbsent(
                                    key.name(),
                                    ignored -> new ArrayList<>())
                            .add(member);
                }
            }
        }
    }

    /**
     * Adds all non-null members to a group.
     */
    private static <
            T extends Viewable,
            G extends MutableViewableGroup<T, G>>
    void addMembers(
            G group,
            Collection<? extends T> members) {

        for (T member : members) {
            if (member != null) {
                group.addMember(member);
            }
        }
    }

    private static <
            T extends Viewable,
            G extends MutableViewableGroup<T, G>>
    void assignRoles(G group, int depth) {

        boolean leaf = group.getChildren().isEmpty();

        group.role(
                depth == 0
                        ? Role.UNIVERSE
                        : leaf
                          ? Role.BUCKET
                          : Role.FACET);

        for (G child : group.getChildren()) {
            assignRoles(child, depth + 1);
        }
    }
}