package objectview.plan;

import objectview.Viewable;
import objectview.field.FieldRef;
import objectview.field.FieldSchema;
import objectview.field.FieldSet;
import objectview.viewconfig.ViewConfig;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * The one owner of rendering decisions after desugaring and resolution: presence,
 * disclosure, cycles, navigation and caption. It answers one object level at a time,
 * so a sink that builds components per object (Card) and the mock walk
 * ({@link #render}) consume the same decisions. An unticked field is never read.
 */
public final class RenderExecutor {

    private final PlanResolver resolver;
    private final Function<Viewable, FieldSchema> schemas;
    private final Predicate<Viewable> topLevel;
    private final Disclosure disclosure;
    private final Function<Object, String> valueLinker;

    /**
     * @param schemas    the authoritative schema of an object, or null for none
     * @param topLevel   whether an object has its own card in this view
     * @param disclosure the reader's open/folded choices
     */
    public RenderExecutor(PlanResolver resolver,
                          Function<Viewable, FieldSchema> schemas,
                          Predicate<Viewable> topLevel,
                          Disclosure disclosure) {
        this(resolver, schemas, topLevel, disclosure, ignored -> null);
    }

    /**
     * @param valueLinker turns a selected value into its external destination, or null.
     *                    It applies equally when that value is painted as an ordinary
     *                    leaf and when a DISPLAY field supplies an object's caption.
     */
    public RenderExecutor(PlanResolver resolver,
                          Function<Viewable, FieldSchema> schemas,
                          Predicate<Viewable> topLevel,
                          Disclosure disclosure,
                          Function<Object, String> valueLinker) {
        this.resolver = resolver;
        this.schemas = schemas == null ? value -> null : schemas;
        this.topLevel = topLevel == null ? value -> false : topLevel;
        this.disclosure = disclosure == null ? Disclosure.INITIAL : disclosure;
        this.valueLinker = valueLinker == null ? ignored -> null : valueLinker;
    }

    /** One object under its literal config: its fields, plan and ticked caption. */
    public record Level(Viewable target, ViewConfig config, FieldSet fields,
                        ObjectPlan plan, String caption, String captionUrl) {
        /** Whether a ticked field other than the caption has something to show. A blank
         * or absent value renders nothing, so it cannot justify an expander; a collection
         * always shows its size. Reads ticked fields only, stopping at the first present
         * one, and only when a renderer asks. */
        public boolean hasBody() {
            for (ObjectPlan.FieldPlan field : plan.body()) {
                Object value = fields.read(field.name());
                if (value instanceof Collection<?> || value instanceof java.util.Map<?, ?>) {
                    return true;
                }
                if (value instanceof CharSequence text ? !text.toString().isBlank()
                        : value != null) {
                    return true;
                }
            }
            return false;
        }

        /** The DISPLAY field's name when its value is the caption, else null. */
        public String captionField() {
            return caption == null ? null : plan.caption().name();
        }

        /** Where the caption occurs when this level occurs at {@code at}, else null. */
        public RenderSink.Occurrence captionAt(RenderSink.Occurrence at) {
            return captionField() == null ? null
                    : at.field(captionField(), plan.caption().label());
        }
    }

    public enum Kind { SKIP, LEAF, OBJECT, NAVIGATION, BACK_REFERENCE, COLLECTION }

    /**
     * One decision. {@code object} is set for OBJECT, NAVIGATION and BACK_REFERENCE;
     * {@code open} for OBJECT and COLLECTION; {@code size} for COLLECTION;
     * {@code skip} for SKIP. Collection members are decided on request by
     * {@link #member}, so a folded collection reads none.
     */
    public record Decision(Kind kind, RenderSink.Occurrence at, ObjectPlan.FieldPlan field,
                           Representation representation, Object value, Level object,
                           boolean open, int size, RenderSink.SkipReason skip) {}

    /** The level of {@code target} under {@code literal}; reads its DISPLAY only when
     * ticked. */
    public Level level(Viewable target, ViewConfig literal) {
        FieldSchema schema = schemas.apply(target);
        FieldSet fields = FieldSet.of(target, schema);
        ObjectPlan plan = resolver.resolve(literal, fields, target.typeName(), schema != null);
        String caption = null;
        String captionUrl = null;
        if (plan.caption() != null) {
            Object value = fields.read(plan.caption().name());
            if (value != null && !String.valueOf(value).isBlank()) {
                caption = String.valueOf(value);
                captionUrl = valueLinker.apply(value);
            }
        }
        return new Level(target, literal, fields, plan, caption, captionUrl);
    }

    /** Whether a root occurrence starts open. */
    public boolean rootOpen(Viewable root) {
        return disclosure.isExpanded(root, true);
    }

    /** The decisions for {@code level}'s fields: ticked ones in config order, then the
     * unticked ones as diagnostics (never read). {@code ancestors} holds the objects on
     * the current path, {@code level}'s own object included. */
    public List<Decision> fields(Level level, RenderSink.Occurrence owner,
                                 Set<?> ancestors) {
        List<Decision> out = new ArrayList<>();
        for (ObjectPlan.FieldPlan field : level.plan().body()) {
            RenderSink.Occurrence at = owner.field(field.name(), field.label());
            Object value = level.fields().read(field.name());
            if (value == null) {
                out.add(skip(at, field, RenderSink.SkipReason.ABSENT));
            } else {
                out.add(decide(field, field.representation(), value, at, false, ancestors));
            }
        }
        for (FieldRef unticked : level.plan().unticked()) {
            out.add(skip(owner.field(unticked.name(), unticked.label()), null,
                    RenderSink.SkipReason.OFF));
        }
        return out;
    }

    /** The decision for one ticked field whose value the caller already holds (a
     * table cell renders one field of a row). */
    public Decision field(ObjectPlan.FieldPlan field, Object value,
                          RenderSink.Occurrence at, Set<?> ancestors) {
        if (value == null) return skip(at, field, RenderSink.SkipReason.ABSENT);
        return decide(field, field.representation(), value, at, false, ancestors);
    }

    /** The decision for one member of an open collection. */
    public Decision member(ObjectPlan.FieldPlan collection, Object item,
                           RenderSink.Occurrence at, Set<?> ancestors) {
        if (item == null) return skip(at, collection, RenderSink.SkipReason.ABSENT);
        Representation shape = item instanceof Viewable && collection.field().embedded()
                ? Representation.OBJECT : ValueShape.of(item);
        return decide(collection, shape, item, at, true, ancestors);
    }

    private Decision decide(ObjectPlan.FieldPlan field, Representation representation,
                            Object value, RenderSink.Occurrence at, boolean member,
                            Set<?> ancestors) {
        if (representation == Representation.VALUE) representation = ValueShape.of(value);
        switch (representation) {
            case COLLECTION -> {
                int size = ValueShape.members(value).size();
                boolean open = size > 0
                        && disclosure.isExpanded(value, Disclosure.initiallyOpen(field, value));
                return new Decision(Kind.COLLECTION, at, field, representation, value,
                        null, open, size, null);
            }
            case OBJECT, REFERENCE -> {
                if (!(value instanceof Viewable target)) {
                    return new Decision(Kind.LEAF, at, field, Representation.TEXT, value,
                            null, false, 0, null);
                }
                Level object = level(target, field.child());
                // Rule 7: a scalar reference to an object with its own card is a link,
                // even back to the card it sits in; a collection member is the
                // collection's projection.
                if (!member && representation == Representation.REFERENCE
                        && topLevel.test(target)) {
                    return new Decision(Kind.NAVIGATION, at, field, representation, value,
                            object, false, 0, null);
                }
                // Rule 8: an object already on the current path is not rendered again.
                if (ancestors.contains(target)) {
                    return new Decision(Kind.BACK_REFERENCE, at, field, representation,
                            value, object, false, 0, null);
                }
                boolean open = disclosure.isExpanded(target,
                        Disclosure.initiallyOpen(field, target, member));
                return new Decision(Kind.OBJECT, at, field, representation, value,
                        object, open, 0, null);
            }
            default -> {
                return new Decision(Kind.LEAF, at, field, representation, value,
                        null, false, 0, null);
            }
        }
    }

    private static Decision skip(RenderSink.Occurrence at, ObjectPlan.FieldPlan field,
                                 RenderSink.SkipReason reason) {
        return new Decision(Kind.SKIP, at, field, null, null, null, false, 0, reason);
    }

    // --- the walk over these decisions, for sinks without their own component tree ---

    /** Renders {@code root} as a top-level card under its literal {@code config}. */
    public void render(Viewable root, ViewConfig config, RenderSink sink) {
        Set<Viewable> ancestors = Collections.newSetFromMap(new IdentityHashMap<>());
        RenderSink.Occurrence at = RenderSink.Occurrence.root();
        Level level = level(root, config);
        boolean open = rootOpen(root);
        RenderSink.ObjectOccurrence occurrence = objectOccurrence(
                at, null, Representation.OBJECT, level, open);
        sink.beginObject(occurrence);
        if (open) {
            ancestors.add(root);
            walk(level, at, sink, ancestors);
        } else {
            sink.defer(at, RenderSink.DeferReason.COLLAPSED);
        }
        sink.endObject(occurrence);
    }

    private void walk(Level level, RenderSink.Occurrence owner, RenderSink sink,
                      Set<Viewable> ancestors) {
        Walk walk = new Walk(sink, ancestors);
        for (Decision decision : fields(level, owner, ancestors)) {
            walk.renderDecision(decision, null);
        }
    }

    /** The walk for a sink without its own component tree: the same exhaustive
     * dispatch Card and the web sink use, turning each decision into sink calls and
     * descending into open objects and collections. */
    private final class Walk implements DecisionRenderer<Void, Void> {
        private final RenderSink sink;
        private final Set<Viewable> ancestors;

        Walk(RenderSink sink, Set<Viewable> ancestors) {
            this.sink = sink;
            this.ancestors = ancestors;
        }

        @Override public Void skip(Decision decision, Void unused) {
            sink.skip(decision);
            return null;
        }

        @Override public Void leaf(Decision decision, Void unused) {
            sink.leaf(decision);
            return null;
        }

        @Override public Void navigation(Decision decision, Void unused) {
            sink.navigation(decision);
            return null;
        }

        @Override public Void backReference(Decision decision, Void unused) {
            sink.backReference(decision);
            return null;
        }

        @Override public Void object(Decision decision, Void unused) {
            Level object = decision.object();
            RenderSink.ObjectOccurrence occurrence = objectOccurrence(
                    decision.at(), decision.field(), decision.representation(), object,
                    decision.open());
            sink.beginObject(occurrence);
            if (decision.open()) {
                ancestors.add(object.target());
                walk(object, decision.at(), sink, ancestors);
                ancestors.remove(object.target());
            } else {
                sink.defer(decision.at(), RenderSink.DeferReason.COLLAPSED);
            }
            sink.endObject(occurrence);
            return null;
        }

        @Override public Void collection(Decision decision, Void unused) {
            sink.beginCollection(decision);
            if (decision.open()) {
                int index = 0;
                for (Object item : ValueShape.members(decision.value())) {
                    renderDecision(member(decision.field(), item,
                            decision.at().member(index++), ancestors), null);
                }
            } else if (decision.size() > 0) {
                sink.defer(decision.at(), RenderSink.DeferReason.COLLAPSED);
            }
            sink.endCollection(decision);
            return null;
        }
    }

    private static RenderSink.ObjectOccurrence objectOccurrence(
            RenderSink.Occurrence at, ObjectPlan.FieldPlan field,
            Representation representation, Level level, boolean open) {
        return new RenderSink.ObjectOccurrence(
                at, field, representation, level, level.captionAt(at), open);
    }

    /** The members of a collection value, for a sink that lays them out itself. */
    public static Collection<?> members(Object collection) {
        return ValueShape.members(collection);
    }
}
