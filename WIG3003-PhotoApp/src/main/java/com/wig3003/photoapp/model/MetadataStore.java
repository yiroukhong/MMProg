package com.wig3003.photoapp.model;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class MetadataStore {

    private static final MetadataStore INSTANCE = new MetadataStore();

    private final Map<String, String> annotations = new HashMap<>();
    private final Path storageFile;

    private MetadataStore() {
        storageFile = Paths.get(System.getProperty("user.home"), ".viflow", "annotations.properties");
        load();
    }

    public static MetadataStore getInstance() {
        return INSTANCE;
    }

    // ── Annotation CRUD ───────────────────────────────────────────────────────

    /** Saves (overwrites) the annotation for path. Passing blank/null deletes it. */
    public void saveAnnotation(String path, String text) {
        if (text == null || text.isBlank()) {
            annotations.remove(path);
        } else {
            annotations.put(path, text);
        }
        persist();
    }

    /** Returns the stored annotation text, or null if none exists. */
    public String getAnnotation(String path) {
        return annotations.get(path);
    }

    /** Removes any annotation for path. */
    public void deleteAnnotation(String path) {
        annotations.remove(path);
        persist();
    }

    /** True if path has a non-empty annotation stored. */
    public boolean hasAnnotation(String path) {
        return annotations.containsKey(path);
    }

    /** Count of paths in the given collection that have annotations. */
    public int annotationCount(Collection<String> paths) {
        int n = 0;
        for (String p : paths) {
            if (annotations.containsKey(p)) n++;
        }
        return n;
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    private void load() {
        if (!Files.exists(storageFile)) return;
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(storageFile)) {
            props.load(in);
        } catch (IOException e) {
            return;
        }
        for (String key : props.stringPropertyNames()) {
            annotations.put(key, props.getProperty(key));
        }
    }

    private void persist() {
        try {
            Files.createDirectories(storageFile.getParent());
            Properties props = new Properties();
            annotations.forEach(props::setProperty);
            try (OutputStream out = Files.newOutputStream(storageFile)) {
                props.store(out, "Vi-Flow annotations");
            }
        } catch (IOException ignored) {
            // non-fatal — annotations remain in memory for the session
        }
    }
}
