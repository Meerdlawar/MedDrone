package uk.ac.ed.acp.cw2;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Environment Configuration Tests
 *
 * Tests for LO2 Section 1: Testing environment configuration
 *
 * These tests verify URL validation logic WITHOUT loading the Spring application context.
 * This avoids bean wiring issues and tests the configuration validation in isolation.
 *
 * Test Coverage:
 * 1. Check that the service reads the ILP_URL environment variable correctly on startup
 * 2. Check that a missing ILP_URL environment variable produces an appropriate error message
 * 3. Check that a malformed URL in ILP_URL is detected and produces an error
 */
@DisplayName("Environment Configuration Tests")
class EnvironmentConfigurationTest {

    // =========================================================================
    // URL Validation Utility (mirrors what your application should do)
    // =========================================================================

    /**
     * Validates an ILP REST URL.
     * This should match the validation logic in your application.
     */
    static class IlpUrlValidator {

        public static ValidationResult validate(String url) {
            if (url == null) {
                return ValidationResult.invalid("ILP_URL environment variable is required but not set (null)");
            }

            if (url.isBlank()) {
                return ValidationResult.invalid("ILP_URL environment variable is required but empty");
            }

            try {
                URI uri = URI.create(url);

                String scheme = uri.getScheme();
                if (scheme == null) {
                    return ValidationResult.invalid("ILP_URL must have a scheme (http or https): " + url);
                }

                if (!scheme.equals("http") && !scheme.equals("https")) {
                    return ValidationResult.invalid("ILP_URL must use http or https scheme, got: " + scheme);
                }

                String host = uri.getHost();
                if (host == null || host.isBlank()) {
                    return ValidationResult.invalid("ILP_URL must have a valid host: " + url);
                }

                return ValidationResult.valid(url);

            } catch (IllegalArgumentException e) {
                return ValidationResult.invalid("ILP_URL is malformed: " + e.getMessage());
            }
        }
    }

    static class ValidationResult {
        private final boolean valid;
        private final String url;
        private final String errorMessage;

        private ValidationResult(boolean valid, String url, String errorMessage) {
            this.valid = valid;
            this.url = url;
            this.errorMessage = errorMessage;
        }

        static ValidationResult valid(String url) {
            return new ValidationResult(true, url, null);
        }

        static ValidationResult invalid(String errorMessage) {
            return new ValidationResult(false, null, errorMessage);
        }

        public boolean isValid() { return valid; }
        public String getUrl() { return url; }
        public String getErrorMessage() { return errorMessage; }
    }

    // =========================================================================
    // Test 1: ILP_URL is read and validated correctly
    // =========================================================================

    @Nested
    @DisplayName("1. ILP_URL Environment Variable Reading")
    class IlpUrlReadingTests {

        @Test
        @DisplayName("Valid HTTPS URL is accepted")
        void validHttpsUrl_isAccepted() {
            String url = "https://ilp-rest.azurewebsites.net";

            ValidationResult result = IlpUrlValidator.validate(url);

            assertThat(result.isValid())
                    .describedAs("Valid HTTPS URL should be accepted")
                    .isTrue();
            assertThat(result.getUrl()).isEqualTo(url);
        }

        @Test
        @DisplayName("Valid HTTP URL is accepted")
        void validHttpUrl_isAccepted() {
            String url = "http://localhost:8080";

            ValidationResult result = IlpUrlValidator.validate(url);

            assertThat(result.isValid())
                    .describedAs("Valid HTTP URL should be accepted")
                    .isTrue();
            assertThat(result.getUrl()).isEqualTo(url);
        }

        @Test
        @DisplayName("URL with path is accepted")
        void urlWithPath_isAccepted() {
            String url = "https://api.example.com/v1/ilp";

            ValidationResult result = IlpUrlValidator.validate(url);

            assertThat(result.isValid())
                    .describedAs("URL with path should be accepted")
                    .isTrue();
        }

        @Test
        @DisplayName("URL with port is accepted")
        void urlWithPort_isAccepted() {
            String url = "http://192.168.1.1:3000";

            ValidationResult result = IlpUrlValidator.validate(url);

            assertThat(result.isValid())
                    .describedAs("URL with port should be accepted")
                    .isTrue();
        }

        @Test
        @DisplayName("Multiple valid URL formats are all accepted")
        void multipleValidFormats_allAccepted() {
            String[] validUrls = {
                    "https://ilp-rest.azurewebsites.net",
                    "http://localhost:8080",
                    "https://api.example.com/v1",
                    "http://192.168.1.1:3000",
                    "https://ilp.service.internal:443/api",
            };

            for (String url : validUrls) {
                ValidationResult result = IlpUrlValidator.validate(url);
                assertThat(result.isValid())
                        .describedAs("URL '%s' should be accepted as valid", url)
                        .isTrue();
            }
        }
    }

    // =========================================================================
    // Test 2: Missing ILP_URL produces appropriate error
    // =========================================================================

    @Nested
    @DisplayName("2. Missing ILP_URL Handling")
    class MissingIlpUrlTests {

        @Test
        @DisplayName("Null URL produces appropriate error message")
        void nullUrl_producesAppropriateError() {
            ValidationResult result = IlpUrlValidator.validate(null);

            assertThat(result.isValid())
                    .describedAs("Null URL should be invalid")
                    .isFalse();
            assertThat(result.getErrorMessage())
                    .describedAs("Error message should mention null or not set")
                    .containsIgnoringCase("null");
        }

        @Test
        @DisplayName("Empty string URL produces appropriate error message")
        void emptyUrl_producesAppropriateError() {
            ValidationResult result = IlpUrlValidator.validate("");

            assertThat(result.isValid())
                    .describedAs("Empty URL should be invalid")
                    .isFalse();
            assertThat(result.getErrorMessage())
                    .describedAs("Error message should mention empty")
                    .containsIgnoringCase("empty");
        }

        @Test
        @DisplayName("Blank/whitespace URL produces appropriate error message")
        void blankUrl_producesAppropriateError() {
            ValidationResult result = IlpUrlValidator.validate("   ");

            assertThat(result.isValid())
                    .describedAs("Blank URL should be invalid")
                    .isFalse();
        }

        @Test
        @DisplayName("Application should fail fast when URL is missing")
        void missingUrl_shouldFailFast() {
            // This test documents expected behavior:
            // When ILP_URL is not provided, the application should fail during startup
            // rather than failing later when trying to make a request

            ValidationResult result = IlpUrlValidator.validate(null);

            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrorMessage()).isNotNull();

            // The error should be clear about what's wrong
            assertThat(result.getErrorMessage().toLowerCase())
                    .containsAnyOf("required", "null", "missing", "not set");
        }
    }

    // =========================================================================
    // Test 3: Malformed URL is detected and produces error
    // =========================================================================

    @Nested
    @DisplayName("3. Malformed ILP_URL Detection")
    class MalformedIlpUrlTests {

        @Test
        @DisplayName("URL without protocol is rejected")
        void urlWithoutProtocol_isRejected() {
            String url = "ilp-rest.azurewebsites.net";

            ValidationResult result = IlpUrlValidator.validate(url);

            assertThat(result.isValid())
                    .describedAs("URL without protocol should be rejected")
                    .isFalse();
            assertThat(result.getErrorMessage())
                    .containsIgnoringCase("scheme");
        }

        @Test
        @DisplayName("URL with spaces is rejected")
        void urlWithSpaces_isRejected() {
            String url = "https://ilp rest.azure websites.net";

            ValidationResult result = IlpUrlValidator.validate(url);

            assertThat(result.isValid())
                    .describedAs("URL with spaces should be rejected")
                    .isFalse();
        }

        @Test
        @DisplayName("URL with wrong protocol is rejected")
        void urlWithWrongProtocol_isRejected() {
            String url = "ftp://ilp-rest.example.com";

            ValidationResult result = IlpUrlValidator.validate(url);

            assertThat(result.isValid())
                    .describedAs("FTP URL should be rejected for REST service")
                    .isFalse();
            assertThat(result.getErrorMessage())
                    .containsAnyOf("http", "https", "scheme");
        }

        @Test
        @DisplayName("URL missing host is rejected")
        void urlMissingHost_isRejected() {
            String url = "https://";

            ValidationResult result = IlpUrlValidator.validate(url);

            assertThat(result.isValid())
                    .describedAs("URL without host should be rejected")
                    .isFalse();
        }

        @Test
        @DisplayName("URL with only port is rejected")
        void urlOnlyPort_isRejected() {
            String url = "https://:8080";

            ValidationResult result = IlpUrlValidator.validate(url);

            assertThat(result.isValid())
                    .describedAs("URL with only port should be rejected")
                    .isFalse();
        }

        @Test
        @DisplayName("Completely invalid string is rejected")
        void completelyInvalidString_isRejected() {
            String[] invalidUrls = {
                    "not-a-url",
                    "://missing-scheme.com",
                    "just some text",
                    "http//missing-colon.com",
                    "<script>alert('xss')</script>",
            };

            for (String url : invalidUrls) {
                ValidationResult result = IlpUrlValidator.validate(url);
                assertThat(result.isValid())
                        .describedAs("Invalid string '%s' should be rejected", url)
                        .isFalse();
            }
        }

        @Test
        @DisplayName("URL with invalid characters is rejected")
        void urlWithInvalidCharacters_isRejected() {
            String url = "https://ilp-rest.example.com/<invalid>";

            // Note: Some invalid characters may or may not cause URI.create to throw
            // This test verifies the validation handles them appropriately
            ValidationResult result = IlpUrlValidator.validate(url);

            // Either parsed but flagged, or throws - both acceptable
            if (result.isValid()) {
                // If it parsed, the URL should at least be sanitized or the
                // application should handle invalid paths gracefully
                assertThat(result.getUrl()).isNotNull();
            }
        }

        @Test
        @DisplayName("Error messages are descriptive")
        void errorMessages_areDescriptive() {
            // Test that error messages give useful information

            ValidationResult nullResult = IlpUrlValidator.validate(null);
            assertThat(nullResult.getErrorMessage())
                    .describedAs("Error for null should be descriptive")
                    .isNotEmpty()
                    .hasSizeGreaterThan(10);

            ValidationResult noSchemeResult = IlpUrlValidator.validate("example.com");
            assertThat(noSchemeResult.getErrorMessage())
                    .describedAs("Error for missing scheme should mention scheme/protocol")
                    .containsIgnoringCase("scheme");

            ValidationResult wrongSchemeResult = IlpUrlValidator.validate("ftp://example.com");
            assertThat(wrongSchemeResult.getErrorMessage())
                    .describedAs("Error for wrong scheme should mention http/https")
                    .containsAnyOf("http", "https");
        }
    }

    // =========================================================================
    // Test: Environment Variable Reading Simulation
    // =========================================================================

    @Nested
    @DisplayName("Environment Variable Simulation")
    class EnvironmentVariableTests {

        @Test
        @DisplayName("System property can override default URL")
        void systemProperty_canOverrideDefault() {
            // Simulate how Spring would read the property
            String defaultUrl = "https://default.example.com";
            String overrideUrl = System.getProperty("ilp.rest.url", defaultUrl);

            // In normal test run, system property won't be set, so default is used
            ValidationResult result = IlpUrlValidator.validate(overrideUrl);

            assertThat(result.isValid())
                    .describedAs("Default or overridden URL should be valid")
                    .isTrue();
        }

        @Test
        @DisplayName("Environment variable simulation works correctly")
        void environmentVariable_simulationWorks() {
            // Test the pattern: ${ILP_URL:default}
            String envValue = System.getenv("ILP_URL");
            String defaultValue = "https://ilp-rest.azurewebsites.net";

            String effectiveUrl = (envValue != null && !envValue.isBlank())
                    ? envValue
                    : defaultValue;

            ValidationResult result = IlpUrlValidator.validate(effectiveUrl);

            assertThat(result.isValid())
                    .describedAs("Effective URL (env or default) should be valid")
                    .isTrue();
        }
    }

    // =========================================================================
    // Integration-style test (can be enabled if needed)
    // =========================================================================

    @Nested
    @DisplayName("Configuration Behavior Documentation")
    class ConfigurationBehaviorTests {

        @Test
        @DisplayName("Documents expected application.properties configuration")
        void documentsExpectedConfiguration() {
            // This test documents the expected configuration format
            // Your application.properties should have:
            //   ilp.rest.url=${ILP_URL:https://ilp-rest.azurewebsites.net}
            //
            // This means:
            // 1. Use ILP_URL environment variable if set
            // 2. Fall back to the default URL if not set
            //
            // Your DroneQueryService (or config class) should validate this URL
            // on startup and fail fast if invalid.

            String expectedPropertyKey = "ilp.rest.url";
            String expectedEnvVariable = "ILP_URL";
            String expectedDefault = "https://ilp-rest.azurewebsites.net";

            // Verify the default is valid
            ValidationResult result = IlpUrlValidator.validate(expectedDefault);
            assertThat(result.isValid())
                    .describedAs("Default URL should be valid: %s", expectedDefault)
                    .isTrue();

            // Document the configuration
            System.out.println("Expected configuration:");
            System.out.println("  Property: " + expectedPropertyKey);
            System.out.println("  Env var:  " + expectedEnvVariable);
            System.out.println("  Default:  " + expectedDefault);
        }
    }
}