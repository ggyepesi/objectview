package objectview.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a {@code Viewable}-valued field as <b>provenance</b> (a
 * {@link quiz.source.Source}): where the owner's data came from, not a
 * first-class entity of the domain.
 *
 * <p>Drives two behaviors, both annotation-based (no {@code instanceof}):
 * <ul>
 *   <li><b>Rendering</b> — {@code Card} draws it as a collapsed
 *       reference chip (like {@link Reference}), never force-inlined; a
 *       single {@code @Provenance} replaces a separate {@code @Reference}.</li>
 *   <li><b>Entity discovery</b> — tooling that enumerates the domain's entity
 *       types (e.g. the instances panel that sections objects by
 *       {@code typeName()}) must not give it its own section, and must not
 *       descend into it while discovering entities.</li>
 * </ul>
 *
 * <p>Field-level (like {@link Reference}/{@link Link}) rather than a
 * runtime {@code instanceof}, so the owner declares the relationship and any
 * future {@code Source} implementation is covered automatically.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Provenance {
}
