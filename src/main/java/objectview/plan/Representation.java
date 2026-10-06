package objectview.plan;

/** How a ticked field occurrence is represented. Decided once, by {@link PlanResolver}. */
public enum Representation {
    /** The DISPLAY field: painted once, as its object's caption. */
    CAPTION,
    TEXT,
    LINK,
    MEDIA,
    /** An embedded object, opened in place. */
    OBJECT,
    /** A referenced object: a navigation link when its target has its own card,
     * otherwise opened in place like an object. */
    REFERENCE,
    /** {@code field name (size)}, members built only when expanded. */
    COLLECTION,
    /** The schema cannot classify the field; {@link ValueShape} decides from the value. */
    VALUE
}
