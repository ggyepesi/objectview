package objectview.text;

public interface TextSelectable {
    void clearSelectionFromManager();

    /** True when this component currently holds a non-empty text selection (the
     *  user has dragged out a range), as opposed to merely being the last one
     *  clicked. Lets callers avoid rebuilding a component mid-selection. */
    boolean hasActiveSelection();
}
