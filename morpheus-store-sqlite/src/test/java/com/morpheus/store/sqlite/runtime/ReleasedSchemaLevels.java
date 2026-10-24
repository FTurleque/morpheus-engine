package com.morpheus.store.sqlite.runtime;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Properties;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

/** The schema level each published version shipped, declared once in {@code released-schema-levels.properties}. */
final class ReleasedSchemaLevels {
    static final String RESOURCE = "/released-schema-levels.properties";

    private ReleasedSchemaLevels() {
    }

    static SortedMap<String, Integer> byVersion() {
        Properties declared = new Properties();
        try (InputStream stream = ReleasedSchemaLevels.class.getResourceAsStream(RESOURCE)) {
            if (stream == null) {
                throw new IllegalStateException("missing test resource " + RESOURCE);
            }
            declared.load(stream);
        } catch (IOException failure) {
            throw new IllegalStateException("cannot read test resource " + RESOURCE, failure);
        }
        SortedMap<String, Integer> levels = new TreeMap<>();
        for (Map.Entry<Object, Object> entry : declared.entrySet()) {
            levels.put(entry.getKey().toString(), Integer.parseInt(entry.getValue().toString().trim()));
        }
        return levels;
    }

    static SortedSet<Integer> distinctLevels() {
        return new TreeSet<>(byVersion().values());
    }
}
