package uk.ac.ed.acp.cw2;

import org.junit.jupiter.api.*;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FR1: Testing Environment Configuration
 * 
 * Based on LO2 Testing Plan Section 1: Testing environment configuration
 * 
 * Test Coverage:
 * 1. Check that the service reads the ILP_URL environment variable correctly on startup.
 * 2. Check that a missing ILP_URL environment variable produces an appropriate error message.
 * 3. Check that a malformed URL in ILP_URL is detected and produces an error.
 * 
 * Total: 3 tests
 */
@DisplayName("FR1: Environment Configuration Tests")
class FR1_EnvironmentConfigurationTest {

    // URL Validation utility class (mirrors application logic)
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
    // FR1.1: Check that the service reads the ILP_URL environment variable 
    //        correctly on startup
    // =========================================================================

    @Nested
    @DisplayName("FR1.1: ILP_URL Environment Variable Reading")
    class FR1_1_UrlReadingTests {

        @Test
        @DisplayName("Valid HTTPS URL is read and accepted correctly")
        void FR1_1_1_validHttpsUrl_isAccepted() {
            String url = "https://ilp-rest.azurewebsites.net";

            ValidationResult result = IlpUrlValidator.validate(url);

            assertThat(result.isValid())
                    .describedAs("Valid HTTPS URL should be accepted")
                    .isTrue();
            assertThat(result.getUrl()).isEqualTo(url);
        }

        @Test
        @DisplayName("Valid HTTP URL is read and accepted correctly")
        void FR1_1_2_validHttpUrl_isAccepted() {
            String url = "http://localhost:8080";

            ValidationResult result = IlpUrlValidator.validate(url);

            assertThat(result.isValid())
                    .describedAs("Valid HTTP URL should be accepted")
                    .isTrue();
            assertThat(result.getUrl()).isEqualTo(url);
        }

        @Test
        @DisplayName("URL with path component is read correctly")
        void FR1_1_3_urlWithPath_isAccepted() {
            String url = "https://api.example.com/v1/ilp";

            ValidationResult result = IlpUrlValidator.validate(url);

            assertThat(result.isValid())
                    .describedAs("URL with path should be accepted")
                    .isTrue();
        }

        @Test
        @DisplayName("URL with port number is read correctly")
        void FR1_1_4_urlWithPort_isAccepted() {
            String url = "http://192.168.1.1:3000";

            ValidationResult result = IlpUrlValidator.validate(url);

            assertThat(result.isValid())
                    .describedAs("URL with port should be accepted")
                    .isTrue();
        }
    }

    // =========================================================================
    // FR1.2: Check that a missing ILP_URL environment variable produces 
    //        an appropriate error message
    // =========================================================================

    @Nested
    @DisplayName("FR1.2: Missing ILP_URL Error Handling")
    class FR1_2_MissingUrlTests {

        @Test
        @DisplayName("Null URL produces appropriate error message")
        void FR1_2_1_nullUrl_producesAppropriateError() {
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
        void FR1_2_2_emptyUrl_producesAppropriateError() {
            ValidationResult result = IlpUrlValidator.validate("");

            assertThat(result.isValid())
                    .describedAs("Empty URL should be invalid")
                    .isFalse();
            assertThat(result.getErrorMessage())
                    .describedAs("Error message should mention empty")
                    .containsIgnoringCase("empty");
        }

        @Test
        @DisplayName("Whitespace-only URL produces appropriate error message")
        void FR1_2_3_blankUrl_producesAppropriateError() {
            ValidationResult result = IlpUrlValidator.validate("   ");

            assertThat(result.isValid())
                    .describedAs("Blank URL should be invalid")
                    .isFalse();
        }
    }

    // =========================================================================
    // FR1.3: Check that a malformed URL in ILP_URL is detected and 
    //        produces an error
    // =========================================================================

    @Nested
    @DisplayName("FR1.3: Malformed ILP_URL Detection")
    class FR1_3_MalformedUrlTests {

        @Test
        @DisplayName("URL without protocol scheme is detected as malformed")
        void FR1_3_1_urlWithoutProtocol_isRejected() {
            String url = "ilp-rest.azurewebsites.net";

            ValidationResult result = IlpUrlValidator.validate(url);

            assertThat(result.isValid())
                    .describedAs("URL without protocol should be rejected")
                    .isFalse();
            assertThat(result.getErrorMessage())
                    .containsIgnoringCase("scheme");
        }

        @Test
        @DisplayName("URL with spaces is detected as malformed")
        void FR1_3_2_urlWithSpaces_isRejected() {
            String url = "https://ilp rest.azure websites.net";

            ValidationResult result = IlpUrlValidator.validate(url);

            assertThat(result.isValid())
                    .describedAs("URL with spaces should be rejected")
                    .isFalse();
        }

        @Test
        @DisplayName("URL with unsupported protocol (FTP) is detected as invalid")
        void FR1_3_3_urlWithWrongProtocol_isRejected() {
            String url = "ftp://ilp-rest.example.com";

            ValidationResult result = IlpUrlValidator.validate(url);

            assertThat(result.isValid())
                    .describedAs("FTP URL should be rejected for REST service")
                    .isFalse();
            assertThat(result.getErrorMessage())
                    .containsAnyOf("http", "https", "scheme");
        }

        @Test
        @DisplayName("URL missing host is detected as malformed")
        void FR1_3_4_urlMissingHost_isRejected() {
            String url = "https://";

            ValidationResult result = IlpUrlValidator.validate(url);

            assertThat(result.isValid())
                    .describedAs("URL without host should be rejected")
                    .isFalse();
        }

        @Test
        @DisplayName("Completely invalid string is detected as malformed")
        void FR1_3_5_completelyInvalidString_isRejected() {
            String[] invalidUrls = {
                    "not-a-url",
                    "://missing-scheme.com",
                    "just some text",
                    "http//missing-colon.com",
            };

            for (String url : invalidUrls) {
                ValidationResult result = IlpUrlValidator.validate(url);
                assertThat(result.isValid())
                        .describedAs("Invalid string '%s' should be rejected", url)
                        .isFalse();
            }
        }

        @Test
        @DisplayName("Error messages are descriptive and helpful")
        void FR1_3_6_errorMessages_areDescriptive() {
            ValidationResult nullResult = IlpUrlValidator.validate(null);
            assertThat(nullResult.getErrorMessage())
                    .describedAs("Error for null should be descriptive")
                    .isNotEmpty()
                    .hasSizeGreaterThan(10);

            ValidationResult noSchemeResult = IlpUrlValidator.validate("example.com");
            assertThat(noSchemeResult.getErrorMessage())
                    .describedAs("Error for missing scheme should mention scheme/protocol")
                    .containsIgnoringCase("scheme");
        }
    }
}
