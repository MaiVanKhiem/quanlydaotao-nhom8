package com.example.quanlydaotao.n8.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class GradeCalculator {

    private GradeCalculator() {
    }

    public record Input(
            String code,
            BigDecimal weight,
            BigDecimal minimum,
            BigDecimal maximum,
            boolean required,
            BigDecimal score
    ) {
    }

    public record Scale(
            UUID id,
            BigDecimal minimum,
            BigDecimal maximum,
            String letter,
            BigDecimal gpa,
            boolean pass
    ) {
    }

    public record Result(
            BigDecimal total,
            UUID scaleId,
            String letter,
            BigDecimal gpa,
            String result
    ) {
    }

    public static void validateScales(List<Scale> scales) {
        check(
                scales != null && !scales.isEmpty(),
                "Chưa có thang điểm."
        );

        for (Scale s : scales) {
            check(
                    s.id() != null
                            && s.minimum() != null
                            && s.maximum() != null
                            && s.letter() != null
                            && !s.letter().isBlank()
                            && s.gpa() != null,
                    "Thang điểm thiếu dữ liệu."
            );

            check(
                    s.minimum().signum() >= 0
                            && s.maximum().compareTo(BigDecimal.TEN) <= 0
                            && s.minimum().compareTo(s.maximum()) <= 0
                            && s.gpa().signum() >= 0
                            && s.gpa().compareTo(BigDecimal.valueOf(4)) <= 0,
                    "Thang điểm có giá trị không hợp lệ."
            );
        }

        for (int i = 0; i <= 1000; i++) {
            BigDecimal point = BigDecimal.valueOf(i, 2);

            long matches = scales.stream()
                    .filter(s -> contains(s, point))
                    .count();

            check(
                    matches == 1,
                    "Thang điểm bị thiếu hoặc chồng khoảng tại " + point
            );
        }
    }

    public static Result calculate(
            List<Input> inputs,
            List<Scale> scales
    ) {
        check(
                inputs != null && !inputs.isEmpty(),
                "Chưa có thành phần điểm."
        );

        validateScales(scales);

        BigDecimal weight = BigDecimal.ZERO;
        BigDecimal total = BigDecimal.ZERO;
        boolean failedMinimum = false;

        for (Input i : inputs) {
            check(
                    i.weight() != null
                            && i.minimum() != null
                            && i.maximum() != null,
                    "Thành phần thiếu cấu hình: " + i.code()
            );

            check(
                    i.weight().signum() >= 0
                            && i.weight().compareTo(
                                    BigDecimal.valueOf(100)
                            ) <= 0
                            && i.minimum().signum() >= 0
                            && i.maximum().signum() > 0
                            && i.maximum().compareTo(BigDecimal.TEN) <= 0
                            && i.minimum().compareTo(i.maximum()) <= 0,
                    "Cấu hình thành phần không hợp lệ: " + i.code()
            );

            weight = weight.add(i.weight());

            if (i.score() == null) {
                check(
                        !i.required(),
                        "Thiếu điểm bắt buộc: " + i.code()
                );
                continue;
            }

            check(
                    i.score().signum() >= 0
                            && i.score().compareTo(i.maximum()) <= 0,
                    "Điểm vượt giới hạn: " + i.code()
            );

            check(
                    i.score().stripTrailingZeros().scale() <= 2,
                    "Điểm chỉ được tối đa 2 chữ số thập phân."
            );

            failedMinimum |= i.required()
                    && i.score().compareTo(i.minimum()) < 0;

            // score / maximum * 10 * weight / 100
            total = total.add(
                    i.score()
                            .multiply(i.weight())
                            .divide(
                                    i.maximum().multiply(BigDecimal.TEN),
                                    24,
                                    RoundingMode.HALF_UP
                            )
            );
        }

        check(
                weight.compareTo(BigDecimal.valueOf(100)) == 0,
                "Tổng trọng số phải bằng 100%, hiện tại: " + weight
        );

        BigDecimal rounded = total.setScale(
                2,
                RoundingMode.HALF_UP
        );

        Scale selected = scales.stream()
                .filter(s -> contains(s, rounded))
                .findFirst()
                .orElseThrow();

        if (failedMinimum) {
            selected = scales.stream()
                    .filter(s -> !s.pass())
                    .min(
                            Comparator.comparing(Scale::gpa)
                                    .thenComparing(Scale::minimum)
                    )
                    .orElseThrow(
                            () -> new IllegalArgumentException(
                                    "Thiếu thang điểm không đạt."
                            )
                    );
        }

        return new Result(
                rounded,
                selected.id(),
                selected.letter(),
                selected.gpa(),
                selected.pass() ? "PASS" : "FAIL"
        );
    }

    private static boolean contains(
            Scale scale,
            BigDecimal point
    ) {
        return point.compareTo(scale.minimum()) >= 0
                && point.compareTo(scale.maximum()) <= 0;
    }

    private static void check(
            boolean valid,
            String message
    ) {
        if (!valid) {
            throw new IllegalArgumentException(message);
        }
    }
}