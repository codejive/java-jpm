package org.codejive.jpm.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests for AppInfo class, focusing on action parsing and management. */
class AppInfoLegacyTest {

    @TempDir Path tempDir;

    @Test
    void testUpdateLegacyDeps() throws IOException {
        // Create a test app.yml file with legacy dependencies map
        Path appYmlPath = tempDir.resolve("app.yml");
        String yamlContent = "dependencies:\n" + "  com.example:test-lib: \"1.0.0\"\n";
        Files.writeString(appYmlPath, yamlContent);

        AppInfo appInfo = AppInfo.read(appYmlPath);

        // Test dependencies are still parsed correctly
        assertThat(appInfo.dependencies()).hasSize(1);
        assertThat(appInfo.dependencies()).contains("com.example:test-lib:1.0.0");

        AppInfo.write(appInfo, appYmlPath);

        // Verify the file now uses new dependencies list format
        String updatedContent = Files.readString(appYmlPath);
        assertThat(updatedContent).doesNotContain("com.example:test-lib: \"1.0.0\"");
        assertThat(updatedContent).contains("- com.example:test-lib:1.0.0");
    }
}
