package objectview.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field whose value sorts <b>numerically</b>, by the leading number of
 * its rendered text — so "1538 K" sorts as 1538 and "26" as 26.
 *
 * <p>Decouples sorting from the value's Java type: the sort needs to know only
 * "this field is numeric", not the value's concrete type (e.g. a host's
 * measured-quantity type). Any field whose display starts with a number can use it.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Numeric {
}
