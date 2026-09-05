package com.adarsh.utils;

import com.adarsh.config.ConfigReader;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Reads the JSON fixtures under {@code src/test/resources/testdata} into records.
 *
 * <p>Tests ask for a typed object, never for a map or a raw string — so a fixture that drifts
 * out of shape fails once, here, with the file name in the message, instead of surfacing as a
 * confusing {@code null} three screens into a flow.</p>
 *
 * <p>Fixtures are immutable for the life of the run, so each file is parsed once and cached.
 * The cache is a {@link ConcurrentHashMap} because parallel {@code <test>} blocks read the
 * same fixtures at the same time. Records are deeply immutable, so sharing them across
 * threads is safe; anything a test needs to vary it derives with a {@code with…} copy.</p>
 */
public final class JsonDataReader {

    private static final Logger LOG = LogManager.getLogger(JsonDataReader.class);

    private static final ObjectMapper MAPPER = JsonMapper.builder()
            // Fixtures carry "_comment"/"_todo" keys for humans; they must not break parsing.
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private static final Map<String, Object> CACHE = new ConcurrentHashMap<>();

    private JsonDataReader() {
        throw new AssertionError("Utility class - not instantiable");
    }

    /** Reads {@code <testdata.dir>/<fileName>} as a single object. */
    public static <T> T read(String fileName, Class<T> type) {
        return cached(fileName, type.getName(),
                () -> parse(fileName, in -> readValue(fileName, in, type)));
    }

    /** Reads {@code <testdata.dir>/<fileName>} as a JSON array of {@code type}. */
    public static <T> List<T> readList(String fileName, Class<T> type) {
        return cached(fileName, "List<" + type.getName() + ">",
                () -> parse(fileName, in -> {
                    try {
                        return MAPPER.readValue(in, MAPPER.getTypeFactory()
                                .constructCollectionType(List.class, type));
                    } catch (IOException e) {
                        throw failure(fileName, e);
                    }
                }));
    }

    /** Reads an arbitrary shape, for the rare fixture that is not a record or a flat list. */
    public static <T> T read(String fileName, TypeReference<T> type) {
        return cached(fileName, type.getType().getTypeName(),
                () -> parse(fileName, in -> {
                    try {
                        return MAPPER.readValue(in, type);
                    } catch (IOException e) {
                        throw failure(fileName, e);
                    }
                }));
    }

    /**
     * Reads a list and indexes it by a key, for {@code byId} style lookups.
     *
     * @throws IllegalStateException if two entries share a key — a duplicate id in a fixture is
     *         a bug that would otherwise silently shadow one of the entries
     */
    public static <T, K> Map<K, T> readIndexed(
            String fileName, Class<T> type, Function<T, K> keyExtractor) {
        return readList(fileName, type).stream()
                .collect(Collectors.toUnmodifiableMap(keyExtractor, Function.identity(),
                        (first, second) -> {
                            throw new IllegalStateException(
                                    "Duplicate key in " + fileName + ": " + first + " / " + second);
                        }));
    }

    /**
     * Reads a list and returns the single entry whose key matches.
     *
     * @throws NoSuchElementException naming the file and the available keys, so a typo in a
     *         test is a one-line fix rather than a debugging session
     */
    public static <T, K> T findByKey(
            String fileName, Class<T> type, Function<T, K> keyExtractor, K key) {
        List<T> all = readList(fileName, type);
        return all.stream()
                .filter(item -> keyExtractor.apply(item).equals(key))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException(
                        "No entry with key '" + key + "' in " + fileName + ". Available: "
                                + all.stream().map(keyExtractor).map(String::valueOf).toList()));
    }

    /**
     * Shapes a list into the {@code Object[][]} TestNG data providers must return.
     *
     * <p>One fixture entry becomes one test invocation carrying the whole record, rather than
     * a row of loose columns — the test then reads {@code scenario.expectedError()} instead of
     * {@code data[3]}.</p>
     */
    public static <T> Object[][] toDataProvider(String fileName, Class<T> type) {
        List<T> items = readList(fileName, type);
        if (items.isEmpty()) {
            throw new IllegalStateException(
                    "Fixture " + fileName + " is empty - a data provider with no rows silently "
                            + "skips its test instead of failing it.");
        }
        return items.stream().map(item -> new Object[]{item}).toArray(Object[][]::new);
    }

    /** Drops cached fixtures. Only needed by a test that rewrites a fixture at runtime. */
    public static void clearCache() {
        CACHE.clear();
    }

    // ------------------------------------------------------------------ internals

    @SuppressWarnings("unchecked")
    private static <T> T cached(String fileName, String typeKey, java.util.function.Supplier<T> loader) {
        return (T) CACHE.computeIfAbsent(fileName + "#" + typeKey, ignored -> loader.get());
    }

    private static <T> T parse(String fileName, Function<InputStream, T> parser) {
        String resource = resourcePath(fileName);
        try (InputStream in = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalArgumentException(
                        "Test data file not found: src/test/resources/" + resource);
            }
            T value = parser.apply(in);
            LOG.debug("Loaded test data from {}", resource);
            return value;
        } catch (IOException e) {
            throw failure(fileName, e);
        }
    }

    private static <T> T readValue(String fileName, InputStream in, Class<T> type) {
        try {
            return MAPPER.readValue(in, type);
        } catch (IOException e) {
            throw failure(fileName, e);
        }
    }

    private static UncheckedIOException failure(String fileName, IOException cause) {
        return new UncheckedIOException(
                "Could not read test data " + resourcePath(fileName) + ": " + cause.getMessage(),
                cause);
    }

    /** Callers pass a bare file name; the folder comes from {@code testdata.dir}. */
    private static String resourcePath(String fileName) {
        String dir = ConfigReader.config().testDataDir();
        return dir.isBlank() ? fileName : dir + "/" + fileName;
    }
}
