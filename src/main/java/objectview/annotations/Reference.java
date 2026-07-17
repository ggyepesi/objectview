package objectview.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Rendering hint for Card.
 *
 * Fields annotated with @Reference contain real Viewable relations,
 * but should be rendered compactly as clickable references instead of fully
 * recursively expanded inline.
 *
 * Useful for preventing recursive UI explosion:
 *
 *     Language -> LanguageFamily -> Language -> ...
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Reference {
}
