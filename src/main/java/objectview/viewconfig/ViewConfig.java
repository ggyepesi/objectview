package objectview.viewconfig;

import objectview.Viewable;

import java.util.*;

public class ViewConfig {

    // Explicitly configured fields. These are shown regardless of minor/non-minor.
    private final Map<String, ViewConfig> fields = new LinkedHashMap<>();
    // Editor memory for fields hidden by a parent gate. Rendering deliberately ignores
    // these; reopening/rebuilding an editor can restore the exact child choices.
    private final Map<String, ViewConfig> rememberedFields = new LinkedHashMap<>();
    private Class<? extends Viewable> cls;

    // Means: include all non-minor fields by default.
    private boolean allFields = true;

    // Means: include all @Minor fields by default too.
    private boolean allMinorFields = false;
    // Editor-only gate state. Null means migrate from the legacy allMinorFields /
    // explicit-field representation; rendering uses only the effective fields above.
    private Boolean minorFieldsVisible;

    private boolean addListener = true;
    private boolean thumb = false;
    // Render images with their answer text blurred out (quiz query panels).
    private boolean blurImages = false;
    private AnswerType answerType = AnswerType.AUTO;

    public static ViewConfig of(Class<? extends Viewable> cls) {
        ViewConfig c = new ViewConfig();
        c.cls = cls;
        c.allFields = true;
        return c;
    }

    /** The full default as shorthand: every field, minor ones included. It is
     * rewritten into ticks where it enters ({@code ViewConfigDesugar}). */
    public static ViewConfig allWithMinorFields(Class<? extends Viewable> cls) {
        return of(cls).setAllMinorFields(true);
    }

    public static ViewConfig leaf() {
        ViewConfig cfg = new ViewConfig();
        cfg.setAllFields(false);
        cfg.setAllMinorFields(false);
        return cfg;
    }

    public ViewConfig copy() {
        ViewConfig c = new ViewConfig();

        c.cls = this.cls;
        c.allFields = this.allFields;
        c.allMinorFields = this.allMinorFields;
        c.minorFieldsVisible = this.minorFieldsVisible;
        c.addListener = this.addListener;
        c.thumb = this.thumb;
        c.blurImages = this.blurImages;
        c.answerType = this.answerType;

        for (Map.Entry<String, ViewConfig> e : fields.entrySet()) {
            c.fields.put(e.getKey(), e.getValue().copy());
        }
        for (Map.Entry<String, ViewConfig> e : rememberedFields.entrySet()) {
            c.rememberedFields.put(e.getKey(), e.getValue().copy());
        }

        return c;
    }

    /** Adds explicitly selected fields from a subtype configuration while retaining
     * this configuration's base-field choices and display flags. */
    public ViewConfig withAdditionalFields(ViewConfig additional) {
        ViewConfig merged = copy();
        if (additional == null) return merged;
        for (Map.Entry<String, ViewConfig> entry : additional.fields.entrySet()) {
            merged.fields.put(entry.getKey(), entry.getValue().copy());
        }
        return merged;
    }

    public boolean hasField(String name) {
        return getFieldConfig(name) != null;
    }

    public ViewConfig getFieldConfig(String name) {
        return name == null ? null : fields.get(name);
    }

    public void addField(String name, ViewConfig config) {
        fields.put(name, config);
    }

    public Map<String, ViewConfig> getFields() {
        return fields;
    }

    public ViewConfig getRememberedFieldConfig(String name) {
        return name == null ? null : rememberedFields.get(name);
    }

    public void rememberField(String name, ViewConfig config) {
        if (name != null && config != null) rememberedFields.put(name, config);
    }

    public Map<String, ViewConfig> getRememberedFields() {
        return rememberedFields;
    }

    public boolean isAllFields() {
        return allFields;
    }

    public void setAllFields(boolean v) {
        allFields = v;
    }

    public boolean isAllMinorFields() {
        return allMinorFields;
    }

    public ViewConfig setAllMinorFields(boolean allMinorFields) {
        this.allMinorFields = allMinorFields;
        return this;
    }

    public Boolean minorFieldsVisible() {
        return minorFieldsVisible;
    }

    public ViewConfig minorFieldsVisible(Boolean visible) {
        minorFieldsVisible = visible;
        return this;
    }

    public Class<? extends Viewable> getCls() {
        return cls;
    }

    public ViewConfig setCls(Class<? extends Viewable> c) {
        cls = c;
        return this;
    }

    public boolean isAddListener() {
        return addListener;
    }

    public ViewConfig setAddListener(boolean v) {
        addListener = v;
        return this;
    }

    public boolean isThumb() {
        return thumb;
    }

    public ViewConfig setThumb(boolean v) {
        thumb = v;
        return this;
    }

    public boolean isBlurImages() {
        return blurImages;
    }

    public ViewConfig setBlurImages(boolean v) {
        blurImages = v;
        return this;
    }

    public AnswerType getAnswerType() {
        return answerType;
    }

    public void setAnswerType(AnswerType t) {
        answerType = t;
    }

    @Override
    public String toString() {
        return "Config{" + "cls=" + (cls == null ? "?" : cls.getSimpleName()) +
                ", allFields=" + allFields + ", allMinorFields=" + allMinorFields +
                ", addListener=" + addListener + ", thumb=" + thumb + ", type=" +
                answerType + ", fields=" + fields.keySet() + '}';
    }

    public enum AnswerType {
        AUTO, TEXT, NUMBER, BOOLEAN, MULTIPLE_CHOICE, IMAGE
    }
}
