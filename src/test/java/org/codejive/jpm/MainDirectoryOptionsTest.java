package org.codejive.jpm;

import static org.assertj.core.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.codejive.jpm.config.UserConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junitpioneer.jupiter.SetEnvironmentVariable;

/** Tests for Main class CLI directory option parsing. */
@SetEnvironmentVariable(key = "JPM_CONFIG", value = "/nonexistent/config.yml")
class MainDirectoryOptionsTest {

    @TempDir Path tempDir;

    private PrintStream originalErr;
    private ByteArrayOutputStream errContent;

    @BeforeEach
    void setUp() {
        // Capture stderr to verify warning messages
        originalErr = System.err;
        errContent = new ByteArrayOutputStream();
        System.setErr(new PrintStream(errContent));
    }

    @AfterEach
    void tearDown() {
        System.setErr(originalErr);
    }

    @Test
    void testGetDirectoryWithCommandLineOption() {
        Main.DepsMixin mixin = new Main.DepsMixin();
        mixin.directory = "/tmp/my-deps";

        Path result = mixin.getDirectory();

        assertThat(result).isNotNull();
        assertThat(result.toString()).isEqualTo(Path.of("/tmp/my-deps").toString());
        assertThat(errContent.toString()).isEmpty(); // No warnings
    }

    @Test
    void testGetDirectoryWithDefault() {
        Main.DepsMixin mixin = new Main.DepsMixin();
        // Don't set directory - should use default 'deps'

        Path result = mixin.getDirectory();

        assertThat(result).isNotNull();
        assertThat(result.toString()).isEqualTo(Path.of("deps").toString());
        assertThat(errContent.toString()).isEmpty(); // No warnings
    }

    @Test
    void testGetDirectoryWithUserConfig() throws IOException {
        // Create a temporary config file with directory setting
        Path configFile = tempDir.resolve("config.yml");
        Files.writeString(configFile, "config:\n  directory: /tmp/dir-from-config\n");

        // Use a test subclass to inject custom UserConfig
        Main.DepsMixin mixin =
                new Main.DepsMixin() {
                    @Override
                    UserConfig getUserConfig() {
                        return UserConfig.read(configFile);
                    }
                };

        Path result = mixin.getDirectory();

        // Should use the directory from UserConfig
        assertThat(result).isNotNull();
        assertThat(result.toString()).isEqualTo(Path.of("/tmp/dir-from-config").toString());
        assertThat(errContent.toString()).isEmpty(); // No warnings
    }

    @Test
    void testGetDirectoryCliOptionOverridesUserConfig() throws IOException {
        // Create a temporary config file with directory setting
        Path configFile = tempDir.resolve("config.yml");
        Files.writeString(configFile, "config:\n  directory: /tmp/dir-from-config\n");

        // Use a test subclass to inject custom UserConfig
        Main.DepsMixin mixin =
                new Main.DepsMixin() {
                    @Override
                    UserConfig getUserConfig() {
                        return UserConfig.read(configFile);
                    }
                };
        mixin.directory = "/tmp/cli-dir"; // CLI option should take precedence

        Path result = mixin.getDirectory();

        // Should use CLI option, not UserConfig
        assertThat(result).isNotNull();
        assertThat(result.toString()).isEqualTo(Path.of("/tmp/cli-dir").toString());
        assertThat(errContent.toString()).isEmpty(); // No warnings
    }

    @Test
    void testGetDirectoryUserConfigOverridesDefault() throws IOException {
        // Create a temporary config file with directory setting
        Path configFile = tempDir.resolve("config.yml");
        Files.writeString(configFile, "config:\n  directory: /tmp/dir-from-config\n");

        // Use a test subclass to inject custom UserConfig
        Main.DepsMixin mixin =
                new Main.DepsMixin() {
                    @Override
                    UserConfig getUserConfig() {
                        return UserConfig.read(configFile);
                    }
                };

        Path result = mixin.getDirectory();

        // UserConfig should override default value
        assertThat(result).isNotNull();
        assertThat(result.toString()).isEqualTo(Path.of("/tmp/dir-from-config").toString());
        assertThat(errContent.toString()).isEmpty(); // No warnings
    }

    @Test
    void testGetDirectoryWithInvalidPath() {
        Main.DepsMixin mixin = new Main.DepsMixin();
        // Use a path with null bytes which is invalid on most filesystems
        mixin.directory = "/invalid\0path/deps";

        assertThatThrownBy(() -> mixin.getDirectory())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("The specified output directory path is invalid:");
    }

    @Test
    void testGetDirectoryWithEmptyString() {
        Main.DepsMixin mixin = new Main.DepsMixin();
        mixin.directory = "";

        Path result = mixin.getDirectory();

        // Empty string should return null
        assertThat(result).isNull();
        assertThat(errContent.toString()).isEmpty(); // No warnings
    }

    @Test
    void testGetDirectoryWithHomePathExpansion() {
        Main.DepsMixin mixin = new Main.DepsMixin();
        mixin.directory = "~/my-deps";

        Path result = mixin.getDirectory();

        // Should expand ~/ to user home
        assertThat(result).isNotNull();
        String userHome = System.getProperty("user.home");
        assertThat(result.toString()).isEqualTo(Path.of(userHome, "my-deps").toString());
        assertThat(errContent.toString()).isEmpty(); // No warnings
    }

    @Test
    void testGetDirectoryWithHomePathExpansionFromUserConfig() throws IOException {
        // Create a temporary config file with directory containing home path
        Path configFile = tempDir.resolve("config.yml");
        Files.writeString(configFile, "config:\n  directory: ~/config-deps\n");

        // Use a test subclass to inject custom UserConfig
        Main.DepsMixin mixin =
                new Main.DepsMixin() {
                    @Override
                    UserConfig getUserConfig() {
                        return UserConfig.read(configFile);
                    }
                };

        Path result = mixin.getDirectory();

        // Should expand ~/ in UserConfig value
        assertThat(result).isNotNull();
        String userHome = System.getProperty("user.home");
        assertThat(result.toString()).isEqualTo(Path.of(userHome, "config-deps").toString());
        assertThat(errContent.toString()).isEmpty(); // No warnings
    }

    @Test
    void testGetDirectoryWhenPathIsFile() throws IOException {
        // Create a regular file
        Path regularFile = tempDir.resolve("not-a-directory.txt");
        Files.writeString(regularFile, "test content");

        Main.DepsMixin mixin = new Main.DepsMixin();
        mixin.directory = regularFile.toString();

        assertThatThrownBy(() -> mixin.getDirectory())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("The specified output directory is a file, not a directory:");
    }

    @Test
    void testGetDirectoryWhenPathIsFileFromUserConfig() throws IOException {
        // Create a regular file
        Path regularFile = tempDir.resolve("not-a-directory.txt");
        Files.writeString(regularFile, "test content");

        // Create a temporary config file with directory pointing to a file
        Path configFile = tempDir.resolve("config.yml");
        Files.writeString(
                configFile, String.format("config:\n  directory: %s\n", regularFile.toString()));

        // Use a test subclass to inject custom UserConfig
        Main.DepsMixin mixin =
                new Main.DepsMixin() {
                    @Override
                    UserConfig getUserConfig() {
                        return UserConfig.read(configFile);
                    }
                };

        assertThatThrownBy(() -> mixin.getDirectory())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("The specified output directory is a file, not a directory:");
    }

    @Test
    void testGetDirectoryWithInvalidPathFromUserConfig() throws IOException {
        // Create a temporary config file with invalid directory path
        Path configFile = tempDir.resolve("config.yml");
        Files.writeString(configFile, "config:\n  directory: \"/invalid\\0path/deps\"\n");

        // Use a test subclass to inject custom UserConfig
        Main.DepsMixin mixin =
                new Main.DepsMixin() {
                    @Override
                    UserConfig getUserConfig() {
                        return UserConfig.read(configFile);
                    }
                };

        // Should throw IllegalArgumentException for invalid path from UserConfig
        assertThatThrownBy(() -> mixin.getDirectory())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("The specified output directory path is invalid:");
    }

    @Test
    void testGetDirectoryMultipleCalls() {
        Main.DepsMixin mixin = new Main.DepsMixin();
        mixin.directory = "/tmp/my-deps";

        // Call multiple times to ensure consistent behavior
        Path result1 = mixin.getDirectory();
        Path result2 = mixin.getDirectory();

        assertThat(result1).isNotNull();
        assertThat(result2).isNotNull();
        assertThat(result1.toString()).isEqualTo(result2.toString());
        assertThat(errContent.toString()).isEmpty(); // No warnings
    }

    @Test
    void testGetDirectoryWithRelativePath() {
        Main.DepsMixin mixin = new Main.DepsMixin();
        mixin.directory = "my-deps";

        Path result = mixin.getDirectory();

        // Should accept relative paths
        assertThat(result).isNotNull();
        assertThat(result.toString()).isEqualTo(Path.of("my-deps").toString());
        assertThat(errContent.toString()).isEmpty(); // No warnings
    }

    @Test
    void testGetDirectoryWithAbsolutePath() {
        Main.DepsMixin mixin = new Main.DepsMixin();
        mixin.directory = "/tmp/absolute-deps";

        Path result = mixin.getDirectory();

        // Should accept absolute paths
        assertThat(result).isNotNull();
        assertThat(result.toString()).isEqualTo(Path.of("/tmp/absolute-deps").toString());
        assertThat(errContent.toString()).isEmpty(); // No warnings
    }
}
