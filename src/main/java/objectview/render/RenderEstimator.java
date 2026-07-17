package objectview.render;

import objectview.Viewable;
import objectview.viewconfig.ViewConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.*;

public final class RenderEstimator {

    private static final Logger log = LoggerFactory.getLogger(RenderEstimator.class);


    public enum Mode {
        /**
         * Safe/current policy:
         * first encounter renders a panel,
         * later encounters render references.
         */
        SHARED_VISITED,

        /**
         * Dangerous/full unfolding policy:
         * only current ancestors stop cycles.
         * Same object may be rendered again through another path.
         */
        PATH_UNFOLDING
    }

    public record Estimate(
            Mode mode,
            int panels,
            int references,
            int leafValues,
            int collections,
            int maps,
            int maxDepth,
            Map<String, Integer> reachabilityCount
    ) {}

    private RenderEstimator() {}

    public static Estimate estimate(
            Viewable root,
            ViewConfig config,
            Mode mode
                                   ) {
        Counter c = new Counter();

        Set<Object> visited =
                Collections.newSetFromMap(new IdentityHashMap<>());

        Set<Object> ancestors =
                Collections.newSetFromMap(new IdentityHashMap<>());

        visitValue(root, config, visited, ancestors, 0, mode, c);

        return new Estimate(
                mode,
                c.panels,
                c.references,
                c.leafValues,
                c.collections,
                c.maps,
                c.maxDepth,
                new LinkedHashMap<>(c.reachabilityCount));
    }

    private static void visitValue(
            Object value,
            ViewConfig config,
            Set<Object> visited,
            Set<Object> ancestors,
            int depth,
            Mode mode,
            Counter c
                                  ) {
        if (value == null) return;

        c.maxDepth = Math.max(c.maxDepth, depth);

        if (value instanceof Viewable q) {
            countReach(q, c);
            visitViewable(q, config, visited, ancestors, depth, mode, c);
            return;
        }

        if (value instanceof Collection<?> collection) {
            c.collections++;

            for (Object item : collection) {
                visitValue(item, config, visited, ancestors,
                           depth + 1, mode, c);
            }

            return;
        }

        if (value instanceof Map<?, ?> map) {
            c.maps++;

            for (Object item : map.values()) {
                visitValue(item, config, visited, ancestors,
                           depth + 1, mode, c);
            }

            return;
        }

        c.leafValues++;
    }

    private static void visitViewable(
            Viewable q,
            ViewConfig config,
            Set<Object> visited,
            Set<Object> ancestors,
            int depth,
            Mode mode,
            Counter c
                                     ) {
        if (q == null) return;

        if (ancestors.contains(q)) {
            c.references++;
            return;
        }

        if (mode == Mode.SHARED_VISITED && visited.contains(q)) {
            c.references++;
            return;
        }

        visited.add(q);
        ancestors.add(q);
        c.panels++;

        ViewConfig cfg =
                config == null
                        ? ViewConfig.all(q.getClass())
                        : config;

        for (Field field : cfg.visibleFieldsFor(q.getClass())) {
            if ("name".equals(field.getName())) {
                continue;
            }

            try {
                field.setAccessible(true);
                Object value = field.get(q);
                visitValue(value, cfg, visited, ancestors,
                           depth + 1, mode, c);
            } catch (Exception ignored) {
            }
        }

        ancestors.remove(q);
    }

    private static void countReach(Viewable q, Counter c) {
        String key = q.getClass().getSimpleName() + "." + q.getIdentifier();
        c.reachabilityCount.merge(key, 1, Integer::sum);

        c.visits++;

        if (c.visits % c.reportEvery == 0) {
            long ms = (System.nanoTime() - c.startedNanos) / 1_000_000;

            log.debug(
                    "Estimator progress: visits=" + c.visits
                            + " panels=" + c.panels
                            + " references=" + c.references
                            + " collections=" + c.collections
                            + " maxDepth=" + c.maxDepth
                            + " timeMs=" + ms
                            + " current=" + key);
        }
    }

    public static void print(Estimate e) {
        log.debug("Render estimate: " + e.mode());
        log.debug("  panels      = " + e.panels());
        log.debug("  references  = " + e.references());
        log.debug("  leafValues  = " + e.leafValues());
        log.debug("  collections = " + e.collections());
        log.debug("  maps        = " + e.maps());
        log.debug("  maxDepth    = " + e.maxDepth());

        log.debug("  most reached:");

        e.reachabilityCount().entrySet().stream()
         .sorted(Map.Entry.<String, Integer>comparingByValue()
                          .reversed())
         .limit(20)
         .forEach(en ->
                          log.debug("    "
                                                     + en.getKey()
                                                     + " -> "
                                                     + en.getValue()));
    }

    private static final class Counter {
        int panels;
        int references;
        int leafValues;
        int collections;
        int maps;
        int maxDepth;
        long visits = 0;
        long reportEvery = 1000;
        long startedNanos = System.nanoTime();

        final Map<String, Integer> reachabilityCount =
                new LinkedHashMap<>();
    }
}