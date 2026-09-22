package com.example.contractpayments.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class ArchitectureTest {
    @Test void domainAndApplicationDoNotImportFrameworks() throws IOException {
        List<String> forbidden = List.of("org.springframework", "jakarta.persistence", "org.apache.kafka");
        try (var files = Files.walk(Path.of("src/main/java"))) {
            var violations = files.filter(path -> path.toString().endsWith(".java"))
                .filter(path -> path.toString().contains("\\domain\\") || path.toString().contains("\\application\\")
                    || path.toString().contains("/domain/") || path.toString().contains("/application/"))
                .filter(path -> forbidden.stream().anyMatch(prefix -> read(path).contains("import " + prefix)))
                .toList();
            assertThat(violations).isEmpty();
        }
    }

    private String read(Path path) {
        try { return Files.readString(path); }
        catch (IOException error) { throw new IllegalStateException(error); }
    }
}
