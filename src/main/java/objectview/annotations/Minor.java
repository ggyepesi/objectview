package objectview.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field as "minor".
 *
 * Minor fields are hidden in compact/default views but may still be:
 *
 * - explicitly enabled in ViewConfig
 * - shown in standalone/detail views
 * - offered in config editors under a separate section
 *
 * Examples:
 *
 *     iso6391
 *     iso6392
 *     glottolog
 *     wikipediaUrl
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Minor {
}
