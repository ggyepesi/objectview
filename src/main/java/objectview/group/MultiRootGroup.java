package objectview.group;

import objectview.Viewable;
import objectview.field.FieldRef;
import objectview.field.FieldSet;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * A synthetic {@code UNIVERSE} over several independent group roots, so a caller that may
 * yield more than one explicit root still hands out a single {@link ViewableGroup}. It is
 * read-only and does not reparent the roots: its children ARE the given roots. Its members
 * are supplied explicitly by the application and are never inferred from those children.
 *
 * <p>Use {@link #of} — it returns the sole root directly when there is one, this wrapper
 * only when there are several, and {@code null} when there are none. The wrapper is shared
 * by every representation (live {@code Viewable} roots and dynamic snapshot roots alike),
 * so the "forest of roots" shape lives in exactly one place.
 */
public final class MultiRootGroup implements ViewableGroup<Viewable> {

    private final String label;
    private final List<ViewableGroup<Viewable>> roots;
    private final List<Viewable> members;

    private MultiRootGroup(
            String label,
            List<ViewableGroup<Viewable>> roots,
            Collection<? extends Viewable> members) {
        this.label = label == null || label.isBlank() ? "Groups" : label;
        this.roots = List.copyOf(roots);
        this.members = members == null ? List.of() : List.copyOf(members);
    }

    /**
     * The single group standing for {@code roots}: the sole root when there is one, a
     * {@code MultiRootGroup} labelled {@code label} when there are several, or {@code null}
     * when empty. Nulls in {@code roots} are ignored.
     */
    public static ViewableGroup<?> of(
            Collection<? extends ViewableGroup<?>> roots, String label) {
        return of(roots, label, List.of());
    }

    /**
     * As {@link #of(Collection, String)}, with the synthetic root's membership supplied
     * explicitly by the application. Membership is never inferred from child groups.
     */
    public static ViewableGroup<?> of(
            Collection<? extends ViewableGroup<?>> roots,
            String label,
            Collection<? extends Viewable> members) {
        if (roots == null || roots.isEmpty()) {
            return null;
        }
        List<ViewableGroup<Viewable>> list = new ArrayList<>();
        for (ViewableGroup<?> root : roots) {
            if (root != null) {
                list.add(cast(root));
            }
        }
        if (list.isEmpty()) {
            return null;
        }
        return list.size() == 1 ? list.get(0) : new MultiRootGroup(label, list, members);
    }

    // A ViewableGroup<X> is read-compatible as a ViewableGroup<Viewable>: its members are
    // Viewables and nothing here writes them back typed.
    @SuppressWarnings("unchecked")
    private static ViewableGroup<Viewable> cast(ViewableGroup<?> group) {
        return (ViewableGroup<Viewable>) group;
    }

    @Override public String getIdentifier() { return label; }
    @Override public String getDisplayName() { return label; }
    @Override public String getFullName() { return label; }
    @Override public Role getRole() { return Role.UNIVERSE; }
    @Override public Viewable getKeyRef() { return null; }
    @Override public ViewableGroup<Viewable> getParent() { return null; }

    @Override public Collection<ViewableGroup<Viewable>> getChildren() {
        return roots;
    }

    @Override public Collection<Viewable> getMembers() {
        return members;
    }

    /** Read-only: only the structural {@code children} field, so a walker sees the roots
     *  and never reflects this wrapper's own Java fields. */
    @Override public FieldSet fields() {
        return new FieldSet() {
            @Override public Object read(String name) {
                return "children".equals(name) ? getChildren() : null;
            }
            @Override public boolean has(String name) {
                return "children".equals(name);
            }
            @Override public void write(String name, Object value) {
                throw new UnsupportedOperationException("read-only group");
            }
            @Override public List<FieldRef> fields() {
                return List.of();
            }
        };
    }
}
