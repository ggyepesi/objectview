package objectview.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Binds a declared field to {@link objectview.Viewable#getDisplayName()}.
 *
 * <p>The field remains an ordinary, stably named field for table columns and
 * configuration, but carries {@link objectview.field.FieldRole#DISPLAY}. ObjectView
 * therefore consumes it once as the card title / table display column instead of
 * also publishing the synthetic {@code @view:display} field. Use this only when
 * the field is the actual backing value of {@code getDisplayName()}.</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface DisplayField {
}
