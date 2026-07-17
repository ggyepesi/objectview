package objectview.facet;

import objectview.Viewable;

import java.util.ArrayList;
import java.util.List;

/**
 * Mutable builder for the immutable {@link FacetTree} records. Assemble a dimension
 * tree incrementally (fluent group-by APIs add children as they go), then {@link
 * #build()} freezes it into immutable {@code FacetTree}s.
 *
 * @param <T> member type
 */
public final class FacetTreeBuilder<T extends Viewable> {

    private final Facet<T> facet;
    private final List<FacetTreeBuilder<T>> children = new ArrayList<>();

    public FacetTreeBuilder(Facet<T> facet) {
        this.facet = facet;
    }

    /** The (mutable) child builders — add to this to nest a dimension. */
    public List<FacetTreeBuilder<T>> children() {
        return children;
    }

    public FacetTree<T> build() {
        List<FacetTree<T>> kids = new ArrayList<>();
        for (FacetTreeBuilder<T> c : children) {
            kids.add(c.build());
        }
        return new FacetTree<>(facet, kids);
    }

    /** Reverse of {@link #build()}: an editable copy of an immutable tree. */
    public static <T extends Viewable> FacetTreeBuilder<T> from(FacetTree<T> tree) {
        FacetTreeBuilder<T> b = new FacetTreeBuilder<>(tree.facet());
        for (FacetTree<T> c : tree.children()) {
            b.children().add(from(c));
        }
        return b;
    }

    public static <T extends Viewable> List<FacetTree<T>> buildAll(
            List<FacetTreeBuilder<T>> nodes) {
        List<FacetTree<T>> out = new ArrayList<>();
        for (FacetTreeBuilder<T> n : nodes) {
            out.add(n.build());
        }
        return out;
    }
}
