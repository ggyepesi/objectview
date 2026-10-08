package objectview.viewconfig;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

public class ViewConfigJsonIO {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    public static File defaultFileFor(Class<?> cls) {
        return new File("data/viewconfig/" + cls.getSimpleName() + ".json");
    }

    /** Keyed by the domain TYPE name (e.g. "Element"), so one config drives both
     *  the desktop typed instances and the web's dynamic objects of that type. */
    public static File fileForType(String typeName) {
        return new File("data/viewconfig/" + typeName + ".json");
    }

    /** Reads a saved config as raw JSON, or null if absent/unreadable. */
    public static JsonConfig loadJson(File file) {
        if (file == null || !file.isFile()) {
            return null;
        }
        try {
            return MAPPER.readValue(file, JsonConfig.class);
        } catch (Exception e) {
            return null;
        }
    }

    public static void save(File file, ViewConfig config) {
        try {
            file.getParentFile().mkdirs();
            MAPPER.writeValue(file, toJson(config));
        } catch (Exception e) {
            throw new RuntimeException("Cannot save view config: " + file, e);
        }
    }

    /** The saved config of a domain type as a {@link ViewConfig}, or null when none is
     *  saved. Shorthand is kept as saved; it is rewritten where the config enters. */
    public static ViewConfig loadForType(String typeName) {
        return typeName == null || typeName.isBlank()
                ? null : fromJson(loadJson(fileForType(typeName)));
    }

    /** The {@link ViewConfig} a saved JSON config describes, or null for null. */
    public static ViewConfig fromJson(JsonConfig j) {
        return fromJson(j, new java.util.ArrayDeque<>());
    }

    /** {@code ancestors} holds the configs above {@code j}, nearest first, so an
     * inherited field re-links to the ancestor it names by distance. */
    @SuppressWarnings("unchecked")
    private static ViewConfig fromJson(JsonConfig j, java.util.Deque<ViewConfig> ancestors) {
        if (j == null) return null;
        if (j.inheritsUp != null) {
            ViewConfig ancestor = ancestors.stream().skip(j.inheritsUp - 1L)
                    .findFirst().orElse(null);
            if (ancestor != null) return ViewConfig.inheriting(ancestor);
        }
        ViewConfig cfg = new ViewConfig();
        if (j.className != null) {
            try {
                Class<?> cls = Class.forName(j.className);
                if (objectview.Viewable.class.isAssignableFrom(cls)) {
                    cfg.setCls((Class<? extends objectview.Viewable>) cls);
                }
            } catch (ClassNotFoundException | LinkageError ignored) {
                // a config saved for a class no longer present still selects by name
            }
        }
        cfg.setAllFields(j.allFields);
        cfg.setAllMinorFields(j.allMinorFields);
        cfg.minorFieldsVisible(j.minorFieldsVisible);
        cfg.setAddListener(j.addListener);
        cfg.setThumb(j.thumb);
        cfg.setBlurImages(j.blurImages);
        cfg.setFinitePaths(Boolean.TRUE.equals(j.finitePaths));
        if (j.answerType != null) {
            try {
                cfg.setAnswerType(ViewConfig.AnswerType.valueOf(j.answerType));
            } catch (IllegalArgumentException ignored) {
                // unknown answer type: keep the default
            }
        }
        ancestors.push(cfg);
        if (j.fields != null) {
            j.fields.forEach((name, child) -> cfg.addField(name, fromJson(child, ancestors)));
        }
        if (j.rememberedFields != null) {
            j.rememberedFields.forEach((name, child) ->
                    cfg.rememberField(name, fromJson(child, ancestors)));
        }
        ancestors.pop();
        return cfg;
    }

    private static JsonConfig toJson(ViewConfig cfg) {
        return toJson(cfg, new java.util.ArrayList<>());
    }

    /** {@code ancestors} holds the configs above {@code cfg}, outermost first. */
    private static JsonConfig toJson(ViewConfig cfg, java.util.List<ViewConfig> ancestors) {
        JsonConfig j = new JsonConfig();
        if (cfg.inheritedFrom() != null) {
            for (int i = ancestors.size() - 1; i >= 0; i--) {
                if (ancestors.get(i) == cfg.inheritedFrom()) {
                    j.inheritsUp = ancestors.size() - i;
                    return j;
                }
            }
        }

        j.className = cfg.getCls() == null ? null : cfg.getCls().getName();
        j.allFields = cfg.isAllFields();
        j.allMinorFields = cfg.isAllMinorFields();
        j.minorFieldsVisible = cfg.minorFieldsVisible();
        j.addListener = cfg.isAddListener();
        j.thumb = cfg.isThumb();
        j.blurImages = cfg.isBlurImages();
        j.finitePaths = cfg.isFinitePaths() ? Boolean.TRUE : null;
        j.answerType = cfg.getAnswerType() == null ? null : cfg.getAnswerType().name();

        ancestors.add(cfg);
        for (Map.Entry<String, ViewConfig> e : cfg.getFields().entrySet()) {
            j.fields.put(e.getKey(), toJson(e.getValue(), ancestors));
        }
        for (Map.Entry<String, ViewConfig> e : cfg.getRememberedFields().entrySet()) {
            j.rememberedFields.put(e.getKey(), toJson(e.getValue(), ancestors));
        }
        ancestors.remove(ancestors.size() - 1);

        return j;
    }

    public static class JsonConfig {
        public String className;
        public boolean allFields;
        public boolean allMinorFields;
        public Boolean minorFieldsVisible;
        public boolean addListener;
        public boolean thumb;
        public boolean blurImages;
        /** Present only for a finite field selection; old/saved View configs omit it. */
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public Boolean finitePaths;
        public String answerType;
        public Map<String, JsonConfig> fields = new LinkedHashMap<>();
        public Map<String, JsonConfig> rememberedFields = new LinkedHashMap<>();
        /** Set on a field that inherits an ancestor's config: how many levels up. */
        public Integer inheritsUp;
    }
}
