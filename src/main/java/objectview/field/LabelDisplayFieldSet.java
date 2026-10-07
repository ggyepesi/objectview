package objectview.field;

import objectview.Viewable;

import java.util.List;

/**
 * A dynamic object whose schema names a DISPLAY field its map does not hold: the object
 * keeps that value as its label (a loaded snapshot object stores its display name on
 * itself, not among its property values). Reading the DISPLAY field reads the label —
 * only when the field is read, i.e. ticked; it is not a fallback for an unticked one.
 */
final class LabelDisplayFieldSet implements FieldSet {
    private final FieldSet backing;
    private final String displayField;
    private final Viewable viewable;

    LabelDisplayFieldSet(FieldSet backing, String displayField, Viewable viewable) {
        this.backing = backing;
        this.displayField = displayField;
        this.viewable = viewable;
    }

    @Override public List<FieldRef> fields() { return backing.fields(); }

    @Override public FieldRef field(String name) { return backing.field(name); }

    @Override public FieldRef displayField() { return backing.displayField(); }

    @Override public Object read(String name) {
        Object value = backing.read(name);
        return value == null && displayField.equals(name) ? viewable.getDisplayName() : value;
    }

    @Override public boolean has(String name) {
        return displayField.equals(name) || backing.has(name);
    }

    @Override public void write(String name, Object value) {
        backing.write(name, value);
    }
}
