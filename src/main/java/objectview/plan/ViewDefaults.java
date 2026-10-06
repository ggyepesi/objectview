package objectview.plan;

import objectview.viewconfig.ViewConfig;

/**
 * The one default for a new View config: every top-level field, minor ones included,
 * and nothing below them. It is expressed in the shorthand it replaces and desugared at
 * once, so the default is literal from the start.
 */
public final class ViewDefaults {

    private ViewDefaults() {}

    public static ViewConfig newView(TypeShape shape) {
        ViewConfig everything = ViewConfig.leaf();
        everything.setAllFields(true);
        everything.setAllMinorFields(true);
        return ViewConfigDesugar.literal(everything, shape);
    }
}
