package objectview.field;

/**
 * The semantic role a field plays, declared as METADATA so the machinery reacts to the role
 * — never to a field's name. IDENTITY and DISPLAY are the {@link objectview.Viewable} contract
 * ({@code getIdentifier()} / {@code getDisplayName()}) surfaced as ordinary fields through a
 * computed {@link FieldSet} layer, so discovery, reading, rendering, search, sort and config
 * all treat them uniformly instead of string-matching {@code "qid"}/{@code "name"}.
 */
public enum FieldRole {
    /** An ordinary field — no special semantics. */
    NONE,
    /** The object's identity ({@code getIdentifier()}). */
    IDENTITY,
    /** The object's display title ({@code getDisplayName()}). */
    DISPLAY
}
