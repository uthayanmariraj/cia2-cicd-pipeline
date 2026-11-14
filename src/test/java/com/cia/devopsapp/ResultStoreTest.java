package com.cia.devopsapp;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ResultStoreTest {

    @TempDir
    Path dataDir;

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private ResultStore store(int maxRecords) {
        return new ResultStore(dataDir.toString(), maxRecords, mapper);
    }

    private static GradeService.GradeResult result(double average, String grade) {
        return new GradeService.GradeResult(average, grade);
    }

    @Test
    void savedResultIsReadBack() {
        ResultStore store = store(10);
        store.save(List.of(78.0, 91.0, 85.0), result(84.67, "A"), "pod-a");

        List<ResultStore.SavedResult> recent = store.recent(5);
        assertThat(recent).hasSize(1);
        assertThat(recent.get(0).marks()).containsExactly(78.0, 91.0, 85.0);
        assertThat(recent.get(0).grade()).isEqualTo("A");
        assertThat(recent.get(0).savedBy()).isEqualTo("pod-a");
        assertThat(store.count()).isEqualTo(1);
    }

    @Test
    void resultsSurviveARestart() {
        // The first store stands for a pod that then crashes; the second for its replacement.
        store(10).save(List.of(50.0), result(50.0, "D"), "pod-that-crashed");

        ResultStore afterRestart = store(10);

        assertThat(afterRestart.count()).isEqualTo(1);
        assertThat(afterRestart.recent(5).get(0).savedBy()).isEqualTo("pod-that-crashed");
    }

    @Test
    void newestComeFirstAndOldestArePruned() throws InterruptedException {
        ResultStore store = store(2);
        store.save(List.of(10.0), result(10.0, "F"), "pod-a");
        Thread.sleep(5);
        store.save(List.of(20.0), result(20.0, "F"), "pod-a");
        Thread.sleep(5);
        store.save(List.of(30.0), result(30.0, "F"), "pod-a");

        assertThat(store.count()).isEqualTo(2);
        assertThat(store.recent(5)).extracting(ResultStore.SavedResult::average).containsExactly(30.0, 20.0);
    }

    @Test
    void ignoresHalfWrittenAndUnreadableFiles() throws IOException {
        ResultStore store = store(10);
        store.save(List.of(90.0), result(90.0, "O"), "pod-a");
        Files.writeString(dataDir.resolve(".tmp-0000000000001-abc"), "{\"partial\":");
        Files.writeString(dataDir.resolve("0000000000002-broken.json"), "not json");

        assertThat(store.recent(5)).extracting(ResultStore.SavedResult::grade).containsExactly("O");
    }
}
