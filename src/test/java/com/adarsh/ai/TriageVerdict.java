package com.adarsh.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Locale;

/**
 * The model's classification of one failure.
 *
 * <p>The four categories are the ones a human triager actually sorts into, and they map to
 * different owners: a product bug goes to the dev team, a test bug to whoever owns the suite,
 * an environment failure to whoever owns the device lab, and flakiness onto the flakiness
 * backlog. A verdict that does not route the failure to someone is not worth generating.</p>
 *
 * @param category   one of {@link Category}
 * @param confidence the model's own confidence, 0-100
 * @param reasoning  short justification, shown in the report
 * @param nextStep   the single most useful thing to do next
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TriageVerdict(
        String category,
        int confidence,
        String reasoning,
        String nextStep) {

    public enum Category {
        PRODUCT_BUG("Product bug", "The app behaved incorrectly. Raise a defect."),
        TEST_BUG("Test bug", "The test or its locators are wrong. Fix the suite."),
        ENVIRONMENT("Environment", "Device, emulator, network or build problem."),
        FLAKY("Flaky", "Timing or instability; the app is probably fine."),
        UNKNOWN("Unknown", "The evidence did not support a classification.");

        private final String label;
        private final String meaning;

        Category(String label, String meaning) {
            this.label = label;
            this.meaning = meaning;
        }

        public String label() {
            return label;
        }

        public String meaning() {
            return meaning;
        }

        static Category from(String value) {
            if (value == null) {
                return UNKNOWN;
            }
            String normalised = value.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
            for (Category candidate : values()) {
                if (candidate.name().equals(normalised)) {
                    return candidate;
                }
            }
            return UNKNOWN;
        }
    }

    public Category asCategory() {
        return Category.from(category);
    }

    /**
     * A verdict the reader should treat with suspicion.
     *
     * <p>Surfaced rather than hidden: a low-confidence guess presented as certainty is worse
     * than no guess, because it sends someone down the wrong path with false authority.</p>
     */
    public boolean isLowConfidence() {
        return confidence < 60;
    }

    /** Rendered into the Allure attachment. */
    public String render() {
        Category resolved = asCategory();
        return """
                AI TRIAGE VERDICT (advisory only - this did not affect the test result)

                Category   : %s (%s)
                Confidence : %d%%%s

                Reasoning  : %s

                Next step  : %s
                """.formatted(
                resolved.label(),
                resolved.meaning(),
                confidence,
                isLowConfidence() ? "  <-- low confidence, verify before acting" : "",
                reasoning == null || reasoning.isBlank() ? "(none given)" : reasoning,
                nextStep == null || nextStep.isBlank() ? "(none given)" : nextStep);
    }
}
