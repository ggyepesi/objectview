package objectview.field;

import objectview.Viewable;

import java.util.Map;

/**
 * A {@link Viewable} that carries runtime-discovered fields (a property map)
 * in addition to, or instead of, its declared Java fields — e.g. generated
 * Wikidata objects whose schema isn't known at compile time.
 *
 * <p>The web layer (rendering, field listing, faceting) treats these map
 * entries as first-class fields, so a dynamically generated dataset behaves
 * like a hand-written one without bespoke code per domain.
 */
public interface DynamicFields {

    /** Field name → value. Values may be scalars, {@link Viewable} references,
     *  or collections/maps of either. */
    Map<String, Object> dynamicFieldValues();
}
