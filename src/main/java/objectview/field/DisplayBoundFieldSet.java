package objectview.field;

import objectview.Viewable;

import java.util.List;

/**
 * A backing with a real DISPLAY-role field. The reserved contract key remains a
 * readable alias for generic reference/search paths, but is not enumerated as a
 * second field.
 */
final class DisplayBoundFieldSet implements FieldSet {
    private final FieldSet backing;
    private final Viewable viewable;

    DisplayBoundFieldSet(FieldSet backing, Viewable viewable) {
        this.backing = backing;
        this.viewable = viewable;
    }

    @Override public List<FieldRef> fields() { return backing.fields(); }

    @Override public FieldRef field(String name) {
        return ViewableContractFieldSet.DISPLAY_KEY.equals(name)
                ? ViewableContractFieldSet.displayFieldRef()
                : backing.field(name);
    }

    @Override public Object read(String name) {
        return ViewableContractFieldSet.DISPLAY_KEY.equals(name)
                ? viewable.getDisplayName() : backing.read(name);
    }

    @Override public boolean has(String name) {
        return ViewableContractFieldSet.DISPLAY_KEY.equals(name) || backing.has(name);
    }

    @Override public void write(String name, Object value) {
        if (ViewableContractFieldSet.DISPLAY_KEY.equals(name)) {
            throw new UnsupportedOperationException(
                    "The display-contract alias is read-only");
        }
        backing.write(name, value);
    }
}
