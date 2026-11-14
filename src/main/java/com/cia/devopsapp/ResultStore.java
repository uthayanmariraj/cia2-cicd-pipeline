package com.cia.devopsapp;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Saves grade results as files in a directory. In Kubernetes that directory is a
 * PersistentVolumeClaim, so the results outlive any single pod.
 *
 * <p>Each result is its own file, so several pods can write to the same shared volume
 * without overwriting each other. A file is written under a temporary name first and then
 * renamed, so a reader never sees a half-written result.
 */
@Component
public class ResultStore {

    public record SavedResult(String id, Instant savedAt, List<Double> marks,
                              double average, String grade, String savedBy) {
    }

    private static final String SUFFIX = ".json";

    private final Path directory;
    private final int maxRecords;
    private final ObjectMapper mapper;

    public ResultStore(@Value("${app.data-dir}") String dataDir,
                       @Value("${app.max-records}") int maxRecords,
                       ObjectMapper mapper) {
        this.directory = Path.of(dataDir);
        this.maxRecords = maxRecords;
        this.mapper = mapper;
        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create data directory " + directory, e);
        }
    }

    public SavedResult save(List<Double> marks, GradeService.GradeResult result, String savedBy) {
        Instant now = Instant.now();
        // A zero-padded timestamp first, so file names sort oldest to newest.
        String id = String.format("%013d-%s", now.toEpochMilli(), UUID.randomUUID().toString().substring(0, 8));
        SavedResult saved = new SavedResult(id, now, List.copyOf(marks), result.average(), result.grade(), savedBy);
        Path temporary = directory.resolve(".tmp-" + id);
        try {
            mapper.writeValue(temporary.toFile(), saved);
            Files.move(temporary, directory.resolve(id + SUFFIX), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot save result " + id, e);
        }
        pruneOldest();
        return saved;
    }

    /** The newest results first. */
    public List<SavedResult> recent(int limit) {
        List<SavedResult> results = new ArrayList<>();
        for (Path file : resultFiles().sorted(Comparator.reverseOrder()).limit(limit).toList()) {
            try {
                results.add(mapper.readValue(file.toFile(), SavedResult.class));
            } catch (IOException e) {
                // Removed by another pod's pruning, or unreadable: skip it.
            }
        }
        return results;
    }

    public long count() {
        return resultFiles().count();
    }

    private void pruneOldest() {
        List<Path> files = resultFiles().sorted().toList();
        for (Path file : files.subList(0, Math.max(0, files.size() - maxRecords))) {
            try {
                Files.deleteIfExists(file);
            } catch (NoSuchFileException e) {
                // Another pod pruned it first.
            } catch (IOException e) {
                throw new UncheckedIOException("Cannot remove old result " + file, e);
            }
        }
    }

    private Stream<Path> resultFiles() {
        try (Stream<Path> listing = Files.list(directory)) {
            return listing
                    .filter(path -> {
                        String name = path.getFileName().toString();
                        return name.endsWith(SUFFIX) && !name.startsWith(".");
                    })
                    .toList()
                    .stream();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read data directory " + directory, e);
        }
    }
}
