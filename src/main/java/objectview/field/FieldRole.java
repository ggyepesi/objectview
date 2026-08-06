package objectview.field;

/**
 * The semantic role a field plays, declared as METADATA so the machinery reacts to the role
 * — never to a field's name. DISPLAY can be bound to a real field with
 * {@link objectview.annotations.DisplayField}; otherwise the Viewable contract supplies a
 * computed fallback. Discovery, rendering, search, sort and config can therefore react to
 * the role without string-matching {@code "name"} or another domain-specific key.
 */
public enum FieldRole {
    /** An ordinary field — no special semantics. */
    NONE,
    /** The object's identity ({@code getIdentifier()}). */
    IDENTITY,
    /** The object's display title ({@code getDisplayName()}). */
    DISPLAY
}
