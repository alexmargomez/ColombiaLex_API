package com.alexmar.colombialex.tax.domain.vo;

import com.alexmar.colombialex.shared.domain.exceptions.DomainValidationException;
import java.util.ArrayList;
import java.util.List;

/**
 * Value Object inmutable para el Número de Identificación Tributaria (NIT) en Colombia.
 * Implementa el algoritmo oficial Módulo 11 de la DIAN (Orden Administrativa 0004 de 1989).
 */
public final class NitVO {

    private static final int[] DIAN_PRIMES = {3, 7, 13, 17, 19, 23, 29, 37, 41, 43, 47, 53, 59, 67, 71};

    public record DigitWeightStep(int positionFromRight, int digit, int primeWeight, int product) {}

    public record NitVerificationReport(
            String rawInput,
            String cleanBaseNit,
            int calculatedCheckDigit,
            Integer providedCheckDigit,
            boolean validProvidedCheckDigit,
            String formattedNitWithDv,
            String taxpayerPersonType,
            int modulo11Sum,
            int modulo11Remainder,
            String formulaExplanation,
            List<DigitWeightStep> stepByStepWeights
    ) {}

    private NitVO() {}

    public static NitVerificationReport inspectAndCalculate(String rawNit) {
        if (rawNit == null || rawNit.isBlank()) {
            throw new DomainValidationException("El NIT o documento tributario no puede estar vacío.");
        }

        String trimmed = rawNit.trim().replace(".", "").replace(" ", "");
        String baseDigits;
        Integer providedDv = null;

        if (trimmed.contains("-")) {
            String[] parts = trimmed.split("-", 2);
            baseDigits = parts[0].replaceAll("\\D", "");
            String dvPart = parts[1].replaceAll("\\D", "");
            if (!dvPart.isEmpty()) {
                providedDv = Integer.parseInt(dvPart.substring(0, 1));
            }
        } else {
            baseDigits = trimmed.replaceAll("\\D", "");
        }

        if (baseDigits.length() < 5 || baseDigits.length() > 15) {
            throw new DomainValidationException("El NIT base debe contener entre 5 y 15 dígitos numéricos.");
        }

        List<DigitWeightStep> steps = new ArrayList<>();
        int sum = 0;
        int len = baseDigits.length();

        for (int i = 0; i < len; i++) {
            int charIndex = len - 1 - i;
            int digit = Character.getNumericValue(baseDigits.charAt(charIndex));
            int prime = DIAN_PRIMES[i];
            int product = digit * prime;
            sum += product;
            steps.add(new DigitWeightStep(i + 1, digit, prime, product));
        }

        int remainder = sum % 11;
        int calculatedDv = (remainder == 0 || remainder == 1) ? remainder : (11 - remainder);
        boolean isValid = providedDv == null || providedDv == calculatedDv;

        String personType = (baseDigits.length() == 9 && (baseDigits.startsWith("8") || baseDigits.startsWith("9")))
                ? "PERSONA_JURIDICA (Sociedad / Entidad)"
                : "PERSONA_NATURAL / RUT";

        String explanation = String.format(
                "Suma ponderada = %d | %d mod 11 = %d -> %s = %d",
                sum,
                sum,
                remainder,
                (remainder <= 1 ? "Residuo directo (0 o 1)" : "11 - " + remainder),
                calculatedDv
        );

        return new NitVerificationReport(
                rawNit,
                baseDigits,
                calculatedDv,
                providedDv,
                isValid,
                baseDigits + "-" + calculatedDv,
                personType,
                sum,
                remainder,
                explanation,
                steps
        );
    }
}
