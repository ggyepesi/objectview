package objectview.plan;

import java.util.IdentityHashMap;
import java.util.Map;

/** The reader's open/folded choices without a UI: expand, collapse and re-expand re-run
 * the same plan through {@link RenderExecutor}. */
public final class MockDisclosure implements Disclosure {

    private final Map<Object, Boolean> choices = new IdentityHashMap<>();

    public MockDisclosure expand(Object key) { choices.put(key, true); return this; }

    public MockDisclosure collapse(Object key) { choices.put(key, false); return this; }

    @Override public boolean isExpanded(Object key, boolean initiallyOpen) {
        Boolean choice = choices.get(key);
        return choice == null ? initiallyOpen : choice;
    }
}
