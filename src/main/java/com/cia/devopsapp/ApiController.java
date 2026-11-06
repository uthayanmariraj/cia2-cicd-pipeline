package com.cia.devopsapp;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final GradeService gradeService;
    private final String message;
    private final String version;

    public ApiController(GradeService gradeService,
                         @Value("${app.message}") String message,
                         @Value("${app.version}") String version) {
        this.gradeService = gradeService;
        this.message = message;
        this.version = version;
    }

    /** What is running: the page shows this, so a redeployment is visible in the browser. */
    @GetMapping("/info")
    public Map<String, String> info() {
        return Map.of("message", message, "version", version, "pod", hostname());
    }

    @GetMapping("/grade")
    public GradeService.GradeResult grade(@RequestParam List<Double> marks) {
        return gradeService.evaluate(marks);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> invalidMarks(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, String>> notNumbers(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.badRequest().body(Map.of("error", "Marks must be numbers"));
    }

    private static String hostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return "unknown";
        }
    }
}
