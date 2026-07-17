package objectview.group;

public record GroupNode(ViewableGroup<?> group) {
    public String getName() {
        return group.getDisplayName();
    }

    public String getFullName() {
        return group.getFullName();
    }

    @Override
    public String toString() {
        return group.getDisplayName();
    }
}
