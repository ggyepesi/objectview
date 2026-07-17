package objectview.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

// Annotate fields that should appear in lightweight ListView.
@Retention(RetentionPolicy.RUNTIME)
public @interface ListField {
    int order() default 0;
    String title() default "";
}