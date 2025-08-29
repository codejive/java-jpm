package org.codejive.jpm.config;

import static org.assertj.core.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests for AppInfo class, focusing on action parsing and management. */
class AppInfoTest {

    @TempDir Path tempDir;

    @Test
    void testReadAppInfoWithActions() throws IOException {
        // Create a test app.yml file with actions
        Path appYmlPath = tempDir.resolve("app.yml");
        String yamlContent =
                "dependencies:\n"
                        + "  com.example:test-lib: \"1.0.0\"\n"
                        + "\n"
                        + "actions:\n"
                        + "  build: \"javac -cp {{deps}} *.java\"\n"
                        + "  test: \"java -cp {{deps}} TestRunner\"\n"
                        + "  run: \"java -cp .:{{deps}} MainClass\"\n"
                        + "  hello: \"echo Hello World\"\n";
        Files.writeString(appYmlPath, yamlContent);

        // Change to temp directory for reading
        String originalDir = System.getProperty("user.dir");
        System.setProperty("user.dir", tempDir.toString());

        try {
            AppInfo appInfo = AppInfo.read();

            // Test action retrieval
            assertThat(appInfo.getAction("build")).isEqualTo("javac -cp {{deps}} *.java");
            assertThat(appInfo.getAction("test")).isEqualTo("java -cp {{deps}} TestRunner");
            assertThat(appInfo.getAction("run")).isEqualTo("java -cp .:{{deps}} MainClass");
            assertThat(appInfo.getAction("hello")).isEqualTo("echo Hello World");

            // Test action names
            Set<String> actionNames = appInfo.getActionNames();
            assertThat(actionNames).hasSize(4);
            assertThat(actionNames).contains("build", "test", "run", "hello");

            // Test non-existent action
            assertThat(appInfo.getAction("nonexistent")).isNull();

            // Test dependencies are still parsed correctly
            assertThat(appInfo.dependencies).hasSize(1);
            assertThat(appInfo.dependencies).containsEntry("com.example:test-lib", "1.0.0");
        } finally {
            System.setProperty("user.dir", originalDir);
        }
    }

    @Test
    void testReadAppInfoWithoutActions() throws IOException {
        // Create a test app.yml file without actions
        Path appYmlPath = tempDir.resolve("app.yml");
        String yamlContent = "dependencies:\n" + "  com.example:test-lib: \"1.0.0\"\n";
        Files.writeString(appYmlPath, yamlContent);

        String originalDir = System.getProperty("user.dir");
        System.setProperty("user.dir", tempDir.toString());

        try {
            AppInfo appInfo = AppInfo.read();

            // Test no actions
            assertThat(appInfo.getActionNames()).isEmpty();
            assertThat(appInfo.getAction("build")).isNull();

            // Test dependencies are still parsed correctly
            assertThat(appInfo.dependencies).hasSize(1);
        } finally {
            System.setProperty("user.dir", originalDir);
        }
    }

    @Test
    void testReadEmptyAppInfo() throws IOException {
        String originalDir = System.getProperty("user.dir");
        System.setProperty("user.dir", tempDir.toString());

        try {
            // No app.yml file exists
            AppInfo appInfo = AppInfo.read();

            // Test no actions and no dependencies
            assertThat(appInfo.getActionNames()).isEmpty();
            assertThat(appInfo.dependencies).isEmpty();
            assertThat(appInfo.getAction("build")).isNull();
        } finally {
            System.setProperty("user.dir", originalDir);
        }
    }

    @Test
    void testWriteAppInfoWithActions() throws IOException {
        AppInfo appInfo = new AppInfo();
        appInfo.dependencies.put("com.example:test-lib", "1.0.0");
        appInfo.actions.put("build", "javac -cp {{deps}} *.java");
        appInfo.actions.put("test", "java -cp {{deps}} TestRunner");

        String originalDir = System.getProperty("user.dir");
        System.setProperty("user.dir", tempDir.toString());

        try {
            AppInfo.write(appInfo);

            // Verify the file was written
            Path appYmlPath = tempDir.resolve("app.yml");
            assertThat(appYmlPath).exists();

            // Read it back and verify
            AppInfo readBack = AppInfo.read();
            assertThat(readBack.getAction("build")).isEqualTo("javac -cp {{deps}} *.java");
            assertThat(readBack.getAction("test")).isEqualTo("java -cp {{deps}} TestRunner");
            assertThat(readBack.getActionNames()).hasSize(2);
            assertThat(readBack.dependencies).hasSize(1);
        } finally {
            System.setProperty("user.dir", originalDir);
        }
    }

    @Test
    void testAppInfoWithComplexActions() throws IOException {
        Path appYmlPath = tempDir.resolve("app.yml");
        String yamlContent =
                "dependencies:\n"
                        + "  com.example:test-lib: \"1.0.0\"\n"
                        + "\n"
                        + "actions:\n"
                        + "  complex: \"java -cp {{deps}} -Dprop=value MainClass arg1 arg2\"\n"
                        + "  quoted: 'echo \"Hello with spaces\"'\n"
                        + "  multiline: >\n"
                        + "    java -cp {{deps}}\n"
                        + "    -Xmx1g\n"
                        + "    MainClass\n";
        Files.writeString(appYmlPath, yamlContent);

        String originalDir = System.getProperty("user.dir");
        System.setProperty("user.dir", tempDir.toString());

        try {
            AppInfo appInfo = AppInfo.read();

            assertThat(appInfo.getAction("complex"))
                    .isEqualTo("java -cp {{deps}} -Dprop=value MainClass arg1 arg2");
            assertThat(appInfo.getAction("quoted")).isEqualTo("echo \"Hello with spaces\"");
            assertThat(appInfo.getAction("multiline")).contains("java -cp {{deps}}");
            assertThat(appInfo.getActionNames()).hasSize(3);
        } finally {
            System.setProperty("user.dir", originalDir);
        }
    }
}
