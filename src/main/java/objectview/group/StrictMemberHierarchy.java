package objectview.group;

import objectview.Viewable;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * The shared strict-member-containment rule for semantic group hierarchy.
 *
 * <p>A non-empty candidate is placed below its unique smallest strict superset.
 * Empty/equal populations and equally small competing supersets establish no
 * direction. Callers decide which nodes may participate and which candidate pairs
 * are structurally comparable; the membership decision itself lives here once.</p>
 */
public final class StrictMemberHierarchy {

    private StrictMemberHierarchy() { }

    public static <T> Map<T, T> parents(
            Collection<T> candidates,
            Function<T, ? extends Collection<? extends Viewable>> members,
            Predicate<T> participates,
            BiPredicate<T, T> mayContain) {
        if (candidates == null || candidates.isEmpty()) return Map.of();
        List<T> ordered = candidates.stream().filter(Objects::nonNull).distinct().toList();
        Map<T, Set<MemberKey>> memberKeys = new LinkedHashMap<>();
        for (T candidate : ordered) {
            Collection<? extends Viewable> values = members.apply(candidate);
            memberKeys.put(candidate, keys(values));
        }

        Map<T, T> result = new LinkedHashMap<>();
        for (T child : ordered) {
            if (!participates.test(child)) continue;
            Set<MemberKey> childMembers = memberKeys.getOrDefault(child, Set.of());
            if (childMembers.isEmpty()) continue;
            List<T> containers = ordered.stream()
                    .filter(parent -> parent != child && participates.test(parent))
                    .filter(parent -> mayContain.test(parent, child))
                    .filter(parent -> strictlyContains(
                            memberKeys.getOrDefault(parent, Set.of()), childMembers))
                    .toList();
            if (containers.isEmpty()) continue;
            int smallest = containers.stream()
                    .mapToInt(parent -> memberKeys.get(parent).size()).min().orElseThrow();
            List<T> nearest = containers.stream()
                    .filter(parent -> memberKeys.get(parent).size() == smallest).toList();
            if (nearest.size() == 1) result.put(child, nearest.get(0));
        }
        return Map.copyOf(result);
    }

    public static <T> Map<T, T> parents(
            Collection<T> candidates,
            Function<T, ? extends Collection<? extends Viewable>> members,
            Predicate<T> participates) {
        return parents(candidates, members, participates, (parent, child) -> true);
    }

    private static Set<MemberKey> keys(Collection<? extends Viewable> members) {
        if (members == null || members.isEmpty()) return Set.of();
        Set<MemberKey> result = new LinkedHashSet<>();
        for (Viewable member : members) {
            if (member != null) result.add(MemberKey.of(member));
        }
        return Set.copyOf(result);
    }

    private static boolean strictlyContains(Set<MemberKey> parent, Set<MemberKey> child) {
        return parent.size() > child.size() && parent.containsAll(child);
    }

    private record MemberKey(String type, String identifier, Viewable anonymous) {
        private static MemberKey of(Viewable value) {
            String identifier = value.getIdentifier();
            if (identifier == null || identifier.isBlank()) {
                return new MemberKey(value.getClass().getName(), "", value);
            }
            String type = value.identityTypeName();
            if (type == null || type.isBlank()) type = value.typeName();
            return new MemberKey(type == null ? "" : type, identifier, null);
        }
    }
}
