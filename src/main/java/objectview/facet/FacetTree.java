package objectview.facet;

import objectview.Viewable;

import java.util.List;
import java.util.Objects;

/**
 * A facet dimension with optional child dimensions.
 */
public record FacetTree<T extends Viewable>(
        Facet<T> facet,
        List<FacetTree<T>> children) {

    public FacetTree {
        Objects.requireNonNull(facet, "facet");
        children = children == null
                ? List.of()
                : List.copyOf(children);
    }

    public FacetTree(Facet<T> facet) {
        this(facet, List.of());
    }
}