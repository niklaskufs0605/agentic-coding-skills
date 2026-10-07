import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class DiscountCalculatorTest {
    private final DiscountCalculator calculator = new DiscountCalculator();

    @ParameterizedTest(name = "{0} less {1}% costs {2}")
    @CsvSource({
        "100.00, 20, 80.00",
        "83.17, 13, 72.36",
        "0.05, 10, 0.05",
        "10.00, 0, 10.00",
        "10.00, 100, 0.00"
    })
    void roundsDiscountedPriceToTwoDecimalsUsingHalfUp(
            BigDecimal price, int percentage, BigDecimal expected) {
        var result = calculator.discount(price, percentage);

        // The contract includes two decimal places, so scale matters here.
        assertThat(result).isEqualTo(expected);
    }

    @Test
    void rejectsPercentageAboveOneHundred() {
        var price = new BigDecimal("10.00");

        assertThatThrownBy(() -> calculator.discount(price, 101))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNegativePercentage() {
        var price = new BigDecimal("10.00");

        assertThatThrownBy(() -> calculator.discount(price, -1))
            .isInstanceOf(IllegalArgumentException.class);
    }

    // Demonstration subject: inputs are non-null, non-negative prices.
    static final class DiscountCalculator {
        BigDecimal discount(BigDecimal price, int percentage) {
            if (percentage < 0 || percentage > 100) {
                throw new IllegalArgumentException("Percentage must be between 0 and 100");
            }
            return price.multiply(BigDecimal.valueOf(100L - percentage))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        }
    }
}
