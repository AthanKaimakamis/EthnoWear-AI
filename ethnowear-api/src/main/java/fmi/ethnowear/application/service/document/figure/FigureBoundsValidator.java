package fmi.ethnowear.application.service.document.figure;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class FigureBoundsValidator {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal ONE = BigDecimal.ONE;

    public void validate(
            BigDecimal x,
            BigDecimal y,
            BigDecimal width,
            BigDecimal height
    ) {
        if (x == null || y == null || width == null || height == null)
            throw new IllegalArgumentException("Figure coordinates are required");

        if (x.compareTo(ZERO) < 0
                || y.compareTo(ZERO) < 0
                || width.compareTo(ZERO) <= 0
                || height.compareTo(ZERO) <= 0
                || x.add(width).compareTo(ONE) > 0
                || y.add(height).compareTo(ONE) > 0)
            throw new IllegalArgumentException(
                    "Figure coordinates must describe a normalized box inside the page"
            );
    }
}
