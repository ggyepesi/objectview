package objectview.plan;

/**
 * Exhaustive projection of one canonical {@link RenderExecutor.Decision}. Swing, JSON
 * and the mock component tree all enter through this switch, so adding a decision kind
 * cannot be handled by one renderer and silently ignored by another.
 *
 * @param <R> the renderer's result component
 * @param <C> renderer-local context (layout/ancestor state)
 */
public interface DecisionRenderer<R, C> {

    default R renderDecision(RenderExecutor.Decision decision, C context) {
        return switch (decision.kind()) {
            case SKIP -> skip(decision, context);
            case LEAF -> leaf(decision, context);
            case OBJECT -> object(decision, context);
            case NAVIGATION -> navigation(decision, context);
            case BACK_REFERENCE -> backReference(decision, context);
            case COLLECTION -> collection(decision, context);
        };
    }

    R skip(RenderExecutor.Decision decision, C context);
    R leaf(RenderExecutor.Decision decision, C context);
    R object(RenderExecutor.Decision decision, C context);
    R navigation(RenderExecutor.Decision decision, C context);
    R backReference(RenderExecutor.Decision decision, C context);
    R collection(RenderExecutor.Decision decision, C context);
}
