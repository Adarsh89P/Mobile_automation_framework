package com.adarsh.utils;

import com.adarsh.config.ConfigReader;
import com.github.javafaker.Faker;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Generates the data that has to be different every run.
 *
 * <p>Fixtures cover data the app must echo back verbatim (product titles, error copy).
 * This covers the opposite case: values a re-run must not collide with — a new customer
 * name on an order, an address the previous run did not already create.</p>
 *
 * <p>{@link Faker} is not documented as thread-safe, so each thread gets its own instance
 * through a {@link ThreadLocal} rather than sharing one across parallel devices.</p>
 *
 * <p>Uniqueness does not come from Faker itself — a name generator repeats sooner than people
 * expect. Emails and similar keys carry an epoch-millisecond stamp plus a monotonic counter,
 * which stays unique across parallel threads <em>and</em> across back-to-back runs.</p>
 */
public final class FakerUtils {

    private static final Logger LOG = LogManager.getLogger(FakerUtils.class);

    private static final AtomicLong SEQUENCE = new AtomicLong();

    private static final ThreadLocal<Faker> FAKER = ThreadLocal.withInitial(FakerUtils::newFaker);

    private FakerUtils() {
        throw new AssertionError("Utility class - not instantiable");
    }

    private static Faker newFaker() {
        Locale locale = Locale.forLanguageTag(ConfigReader.config().fakerLocale());
        String seed = ConfigReader.config().fakerSeed();
        if (seed != null && !seed.isBlank()) {
            // A fixed seed makes a failing run reproducible; it also removes uniqueness, so it
            // is opt-in and never the default.
            long parsed = Long.parseLong(seed.trim());
            LOG.warn("Faker is seeded with {} - generated data will repeat between runs", parsed);
            return new Faker(locale, new java.util.Random(parsed));
        }
        return new Faker(locale);
    }

    /** The raw generator, for the occasional value with no helper below. */
    public static Faker faker() {
        return FAKER.get();
    }

    // ------------------------------------------------------------------ identity

    public static String firstName() {
        return faker().name().firstName();
    }

    public static String lastName() {
        return faker().name().lastName();
    }

    public static String fullName() {
        return faker().name().firstName() + " " + faker().name().lastName();
    }

    /**
     * An address that no earlier run has used.
     *
     * <p>The local part is {@code <name>.<epochMillis>.<counter>} so two threads generating in
     * the same millisecond still differ.</p>
     */
    public static String uniqueEmail() {
        return uniqueEmail("qa");
    }

    public static String uniqueEmail(String prefix) {
        return "%s.%s@example.com".formatted(sanitise(prefix), uniqueSuffix());
    }

    /** Monotonic, collision-free across threads and across runs. */
    public static String uniqueSuffix() {
        return Instant.now().toEpochMilli() + "-" + SEQUENCE.incrementAndGet();
    }

    /** Meets the usual "upper, lower, digit, symbol" rules rather than being merely random. */
    public static String password() {
        return "Qa!" + faker().internet().password(8, 12, true, true) + SEQUENCE.incrementAndGet();
    }

    public static String phoneNumber() {
        return faker().phoneNumber().cellPhone().replaceAll("[^0-9]", "");
    }

    // ------------------------------------------------------------------ address

    public static String streetAddress() {
        return faker().address().streetAddress();
    }

    public static String city() {
        return faker().address().city();
    }

    public static String state() {
        return faker().address().stateAbbr();
    }

    public static String zipCode() {
        // Faker occasionally emits ZIP+4; the demo app's field takes five digits.
        return faker().address().zipCode().replaceAll("[^0-9]", "").substring(0, 5);
    }

    public static String country() {
        return faker().address().country();
    }

    // ------------------------------------------------------------------ misc

    /** Short free text for a notes or search field. */
    public static String sentence() {
        return faker().lorem().sentence();
    }

    /** Removes anything an email local part or an id should not contain. */
    private static String sanitise(String value) {
        String cleaned = (value == null ? "" : value).toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        return cleaned.isBlank() ? "qa" : cleaned;
    }

    /** Releases this thread's generator. Called from the test listener after a run. */
    public static void reset() {
        FAKER.remove();
    }
}
