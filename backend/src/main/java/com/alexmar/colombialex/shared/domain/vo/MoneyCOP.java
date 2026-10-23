package com.alexmar.colombialex.shared.domain.vo;

import com.alexmar.colombialex.shared.domain.exceptions.DomainValidationException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Value Object inmutable para montos monetarios en Pesos Colombianos (COP).
 * Garantiza aritmética financiera exacta usando BigDecimal y RoundingMode.HALF_UP.
 */
public final class MoneyCOP {
    public static final MoneyCOP ZERO = new MoneyCOP(BigDecimal.ZERO);

    private final BigDecimal amount;

    private MoneyCOP(BigDecimal amount) {
        this.amount = amount.setScale(2, RoundingMode.HALF_UP);
    }

    public static MoneyCOP of(BigDecimal value) {
        if (value == null) {
            throw new DomainValidationException("El monto en COP no puede ser nulo.");
        }
        return new MoneyCOP(value);
    }

    public static MoneyCOP of(double value) {
        return new MoneyCOP(BigDecimal.valueOf(value));
    }

    public static MoneyCOP ofNonNegative(BigDecimal value, String fieldName) {
        if (value == null || value.compareTo(BigDecimal.ZERO) < 0) {
            throw new DomainValidationException("El campo '" + fieldName + "' debe ser mayor o igual a 0 COP.");
        }
        return new MoneyCOP(value);
    }

    public MoneyCOP add(MoneyCOP other) {
        return new MoneyCOP(this.amount.add(other.amount));
    }

    public MoneyCOP subtract(MoneyCOP other) {
        return new MoneyCOP(this.amount.subtract(other.amount));
    }

    public MoneyCOP multiply(BigDecimal factor) {
        return new MoneyCOP(this.amount.multiply(factor));
    }

    public MoneyCOP multiply(double factor) {
        return new MoneyCOP(this.amount.multiply(BigDecimal.valueOf(factor)));
    }

    public MoneyCOP divide(BigDecimal divisor) {
        if (divisor == null || divisor.compareTo(BigDecimal.ZERO) == 0) {
            throw new DomainValidationException("División por cero no permitida en cálculo monetario.");
        }
        return new MoneyCOP(this.amount.divide(divisor, 6, RoundingMode.HALF_UP));
    }

    public MoneyCOP roundToPesos() {
        return new MoneyCOP(this.amount.setScale(0, RoundingMode.HALF_UP));
    }

    public boolean isLessThanOrEqualTo(MoneyCOP other) {
        return this.amount.compareTo(other.amount) <= 0;
    }

    public boolean isGreaterThanOrEqualTo(MoneyCOP other) {
        return this.amount.compareTo(other.amount) >= 0;
    }

    public BigDecimal value() {
        return amount;
    }

    public BigDecimal roundedPesosValue() {
        return amount.setScale(0, RoundingMode.HALF_UP);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MoneyCOP that)) return false;
        return amount.compareTo(that.amount) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(amount.stripTrailingZeros());
    }

    @Override
    public String toString() {
        return "$" + amount.setScale(0, RoundingMode.HALF_UP).toPlainString() + " COP";
    }
}
