package objectview.text;

public final class TextSelection {

    private int anchorLine = -1;
    private int anchorOffset = -1;
    private int focusLine = -1;
    private int focusOffset = -1;

    public void clear() {
        anchorLine = -1;
        anchorOffset = -1;
        focusLine = -1;
        focusOffset = -1;
    }

    public void setAnchor(int line, int offset) {
        anchorLine = line;
        anchorOffset = offset;
        focusLine = line;
        focusOffset = offset;
    }

    public void setFocus(int line, int offset) {
        focusLine = line;
        focusOffset = offset;
    }

    public boolean hasAnchor() {
        return anchorLine >= 0 && anchorOffset >= 0;
    }

    public boolean isEmpty() {
        return !hasAnchor()
                || (anchorLine == focusLine && anchorOffset == focusOffset);
    }

    int startLine() {
        return compare(anchorLine, anchorOffset, focusLine, focusOffset) <= 0
                ? anchorLine
                : focusLine;
    }

    int startOffset() {
        return compare(anchorLine, anchorOffset, focusLine, focusOffset) <= 0
                ? anchorOffset
                : focusOffset;
    }

    int endLine() {
        return compare(anchorLine, anchorOffset, focusLine, focusOffset) <= 0
                ? focusLine
                : anchorLine;
    }

    int endOffset() {
        return compare(anchorLine, anchorOffset, focusLine, focusOffset) <= 0
                ? focusOffset
                : anchorOffset;
    }

    public boolean intersectsLine(int lineIndex) {
        return !isEmpty()
                && lineIndex >= startLine()
                && lineIndex <= endLine();
    }

    public int selectedStartForLine(int lineIndex) {
        if (!intersectsLine(lineIndex)) {
            return -1;
        }

        return lineIndex == startLine() ? startOffset() : 0;
    }

    public int selectedEndForLine(int lineIndex, int lineLength) {
        if (!intersectsLine(lineIndex)) {
            return -1;
        }

        return lineIndex == endLine() ? endOffset() : lineLength;
    }

    private static int compare(
            int lineA,
            int offsetA,
            int lineB,
            int offsetB) {

        int lineCmp = Integer.compare(lineA, lineB);

        return lineCmp != 0
                ? lineCmp
                : Integer.compare(offsetA, offsetB);
    }
}
