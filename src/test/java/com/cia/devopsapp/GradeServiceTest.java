package com.cia.devopsapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class GradeServiceTest {

    private final GradeService service = new GradeService();

    @ParameterizedTest
    @CsvSource({"100,O", "90,O", "89.99,A", "80,A", "70,B", "60,C", "50,D", "49.99,F", "0,F"})
    void gradeBoundaries(double average, String expected) {
        assertThat(service.gradeFor(average)).isEqualTo(expected);
    }

    @Test
    void averagesMarksAndRoundsToTwoPlaces() {
        GradeService.GradeResult result = service.evaluate(List.of(70.0, 80.0, 85.0));
        assertThat(result.average()).isEqualTo(78.33);
        assertThat(result.grade()).isEqualTo("B");
    }

    @Test
    void gradesOnTheUnroundedAverage() {
        // 89.996 rounds to 90.00 for display but has not reached the O boundary.
        GradeService.GradeResult result = service.evaluate(List.of(89.996));
        assertThat(result.average()).isEqualTo(90.0);
        assertThat(result.grade()).isEqualTo("A");
    }

    @Test
    void rejectsEmptyAndMissingMarks() {
        assertThatThrownBy(() -> service.evaluate(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.evaluate(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.evaluate(Arrays.asList(50.0, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @CsvSource({"-1", "100.5"})
    void rejectsMarksOutOfRange(double mark) {
        assertThatThrownBy(() -> service.evaluate(List.of(mark)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 0 and 100");
    }
}
