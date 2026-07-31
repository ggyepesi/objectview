package objectview.viewconfig;

import objectview.Viewable;
import objectview.group.ViewableGroup;

import java.util.Objects;

/** Explicit domain-level association between a member type and its single group root. */
public record DomainGroupRoot(String memberType, ViewableGroup<?> root) {

    public DomainGroupRoot {
        Objects.requireNonNull(memberType, "memberType");
        Objects.requireNonNull(root, "root");
        if (memberType.isBlank()) {
            throw new IllegalArgumentException("memberType must not be blank");
        }
    }

    public static DomainGroupRoot of(
            Class<? extends Viewable> memberClass, ViewableGroup<?> root) {
        Objects.requireNonNull(memberClass, "memberClass");
        return new DomainGroupRoot(memberClass.getSimpleName(), root);
    }
}
