package com.cia.devopsapp;

import java.util.List;
import org.springframework.stereotype.Service;

/** Business logic: turns a list of marks (0-100) into an average and a letter grade. */
@Service
public class GradeService {

    public record GradeResult(double average, String grade) {
    }

    public GradeResult evaluate(List<Double> marks) {
        if (marks == null || marks.isEmpty()) {
            throw new IllegalArgumentException("At least one mark is required");
        }
        for (Double mark : marks) {
            if (mark == null || mark < 0 || mark > 100) {
                throw new IllegalArgumentException("Marks must be between 0 and 100");
            }
        }
        double average = marks.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
        double rounded = Math.round(average * 100.0) / 100.0;
        return new GradeResult(rounded, gradeFor(average));
    }

    String gradeFor(double average) {
        if (average >= 90) {
            return "O";
        }
        if (average >= 80) {
            return "A";
        }
        if (average >= 70) {
            return "B";
        }
        if (average >= 60) {
            return "C";
        }
        if (average >= 50) {
            return "D";
        }
        return "F";
    }
}
