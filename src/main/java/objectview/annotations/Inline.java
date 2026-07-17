package objectview.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Rendering hint for Card, the opposite of {@link Reference}.
 *
 * By default a nested Viewable that lives inside a collection or map is
 * rendered compactly as a clickable reference chip (which opens a detail
 * window). Annotate the field with @Inline to instead expand each
 * nested Viewable fully, in place, within the parent card.
 *
 * Use only where the nested structure is small and bounded (e.g. a log
 * tree of workflow -> steps). Do NOT use it on broad or cyclic graphs such
 * as constellation neighbours -- those rely on the reference default to
 * avoid expanding the whole reachable graph inline.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Inline {
}
