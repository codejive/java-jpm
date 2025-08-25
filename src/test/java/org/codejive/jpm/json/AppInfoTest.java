package org.codejive.jpm.json;

import static org.junit.jupiter.api.Assertions.*;

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
            assertEquals("javac -cp {{deps}} *.java", appInfo.getAction("build"));
            assertEquals("java -cp {{deps}} TestRunner", appInfo.getAction("test"));
            assertEquals("java -cp .:{{deps}} MainClass", appInfo.getAction("run"));
            assertEquals("echo Hello World", appInfo.getAction("hello"));

            // Test action names
            Set<String> actionNames = appInfo.getActionNames();
            assertEquals(4, actionNames.size());
            assertTrue(actionNames.contains("build"));
            assertTrue(actionNames.contains("test"));
            assertTrue(actionNames.contains("run"));
            assertTrue(actionNames.contains("hello"));

            // Test non-existent action
            assertNull(appInfo.getAction("nonexistent"));

            // Test dependencies are still parsed correctly
            assertEquals(1, appInfo.dependencies.size());
            assertTrue(appInfo.dependencies.containsKey("com.example:test-lib"));
            assertEquals("1.0.0", appInfo.dependencies.get("com.example:test-lib"));
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
            assertTrue(appInfo.getActionNames().isEmpty());
            assertNull(appInfo.getAction("build"));

            // Test dependencies are still parsed correctly
            assertEquals(1, appInfo.dependencies.size());
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
            assertTrue(appInfo.getActionNames().isEmpty());
            assertTrue(appInfo.dependencies.isEmpty());
            assertNull(appInfo.getAction("build"));
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
            assertTrue(Files.exists(appYmlPath));

            // Read it back and verify
            AppInfo readBack = AppInfo.read();
            assertEquals("javac -cp {{deps}} *.java", readBack.getAction("build"));
            assertEquals("java -cp {{deps}} TestRunner", readBack.getAction("test"));
            assertEquals(2, readBack.getActionNames().size());
            assertEquals(1, readBack.dependencies.size());
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

            assertEquals(
                    "java -cp {{deps}} -Dprop=value MainClass arg1 arg2",
                    appInfo.getAction("complex"));
            assertEquals("echo \"Hello with spaces\"", appInfo.getAction("quoted"));
            assertTrue(appInfo.getAction("multiline").contains("java -cp {{deps}}"));
            assertEquals(3, appInfo.getActionNames().size());
        } finally {
            System.setProperty("user.dir", originalDir);
        }
    }
}
