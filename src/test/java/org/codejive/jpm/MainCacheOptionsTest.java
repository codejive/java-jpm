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
import org.junitpioneer.jupiter.ClearEnvironmentVariable;
import org.junitpioneer.jupiter.SetEnvironmentVariable;

/** Tests for Main class CLI cache option parsing. */
@SetEnvironmentVariable(key = "JPM_CONFIG", value = "/nonexistent/config.yml")
class MainCacheOptionsTest {

    @TempDir Path tempCacheDir1;
    @TempDir Path tempCacheDir2;
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
    @ClearEnvironmentVariable(key = "JPM_CACHE")
    void testGetCacheDirWithCommandLineOption() {
        Main.DepsMixin mixin = new Main.DepsMixin();
        mixin.cacheDir = tempCacheDir1.toString();

        Path result = mixin.getCacheDir();

        assertThat(result).isEqualTo(tempCacheDir1);
        assertThat(errContent.toString()).isEmpty(); // No warnings
    }

    @Test
    @SetEnvironmentVariable(key = "JPM_CACHE", value = "/tmp/jpm-cache-from-env")
    void testGetCacheDirFromEnvironmentVariable() {
        Main.DepsMixin mixin = new Main.DepsMixin();
        // Don't set mixin.cacheDir - it should use the env var

        Path result = mixin.getCacheDir();

        assertThat(result).isNotNull();
        String expectedPath = Path.of("/tmp/jpm-cache-from-env").toString();
        assertThat(result.toString()).isEqualTo(expectedPath);
        assertThat(errContent.toString()).isEmpty(); // No warnings
    }

    @Test
    @SetEnvironmentVariable(key = "JPM_CACHE", value = "/tmp/jpm-cache-from-env")
    void testCommandLineOptionTakesPrecedenceOverEnvironmentVariable() {
        Main.DepsMixin mixin = new Main.DepsMixin();
        mixin.cacheDir = tempCacheDir1.toString(); // Command line option is set

        Path result = mixin.getCacheDir();

        // Should return the command line option, not the environment variable
        assertThat(result).isEqualTo(tempCacheDir1);
        assertThat(errContent.toString()).isEmpty(); // No warnings
    }

    @Test
    @ClearEnvironmentVariable(key = "JPM_CACHE")
    void testGetCacheDirWithNoOptionAndNoEnvironmentVariable() {
        Main.DepsMixin mixin = new Main.DepsMixin();
        // Neither command line option nor environment variable is set

        Path result = mixin.getCacheDir();

        assertThat(result).isNull();
        assertThat(errContent.toString()).isEmpty(); // No warnings
    }

    @Test
    @SetEnvironmentVariable(key = "JPM_CACHE", value = "")
    void testGetCacheDirWithEmptyEnvironmentVariable() {
        Main.DepsMixin mixin = new Main.DepsMixin();

        Path result = mixin.getCacheDir();

        assertThat(result).isNull();
        assertThat(errContent.toString()).isEmpty(); // No warnings
    }

    @Test
    @ClearEnvironmentVariable(key = "JPM_CACHE")
    void testGetCacheDirMultipleCalls() {
        Main.DepsMixin mixin = new Main.DepsMixin();
        mixin.cacheDir = tempCacheDir1.toString();

        // Call multiple times to ensure consistent behavior
        Path result1 = mixin.getCacheDir();
        Path result2 = mixin.getCacheDir();

        assertThat(result1).isEqualTo(tempCacheDir1);
        assertThat(result2).isEqualTo(tempCacheDir1);
        assertThat(result1).isEqualTo(result2);
        assertThat(errContent.toString()).isEmpty(); // No warnings
    }

    @Test
    @ClearEnvironmentVariable(key = "JPM_CACHE")
    void testGetCacheDirWithInvalidPath() {
        Main.DepsMixin mixin = new Main.DepsMixin();
        // Use a path with null bytes which is invalid on most filesystems
        mixin.cacheDir = "/invalid\0path/cache";

        Path result = mixin.getCacheDir();

        // Should return null when path is invalid
        assertThat(result).isNull();
        // Should print warning to stderr
        assertThat(errContent.toString())
                .contains("Warning: The specified cache path is invalid, ignoring:");
    }

    @Test
    @ClearEnvironmentVariable(key = "JPM_CACHE")
    void testGetCacheDirWithHomePathExpansion() {
        Main.DepsMixin mixin = new Main.DepsMixin();
        mixin.cacheDir = "~/my-cache";

        Path result = mixin.getCacheDir();

        // Should expand ~/ to user home
        assertThat(result).isNotNull();
        String userHome = System.getProperty("user.home");
        assertThat(result.toString()).isEqualTo(Path.of(userHome, "my-cache").toString());
        assertThat(errContent.toString()).isEmpty(); // No warnings
    }

    @Test
    @SetEnvironmentVariable(key = "JPM_CACHE", value = "~/env-cache")
    void testGetCacheDirWithHomePathExpansionFromEnv() {
        Main.DepsMixin mixin = new Main.DepsMixin();
        // Don't set cacheDir - should use env var

        Path result = mixin.getCacheDir();

        // Should expand ~/ in environment variable
        assertThat(result).isNotNull();
        String userHome = System.getProperty("user.home");
        assertThat(result.toString()).isEqualTo(Path.of(userHome, "env-cache").toString());
        assertThat(errContent.toString()).isEmpty(); // No warnings
    }

    @Test
    @ClearEnvironmentVariable(key = "JPM_CACHE")
    void testGetCacheDirWithUserConfig() throws IOException {
        // Create a temporary config file with cache setting
        Path configFile = tempDir.resolve("config.yml");
        Files.writeString(configFile, "config:\n  cache: /tmp/cache-from-config\n");

        // Use a test subclass to inject custom UserConfig
        Main.DepsMixin mixin =
                new Main.DepsMixin() {
                    @Override
                    UserConfig getUserConfig() {
                        return UserConfig.read(configFile);
                    }
                };

        Path result = mixin.getCacheDir();

        // Should use the cache from UserConfig
        assertThat(result).isNotNull();
        assertThat(result.toString()).isEqualTo(Path.of("/tmp/cache-from-config").toString());
        assertThat(errContent.toString()).isEmpty(); // No warnings
    }

    @Test
    @ClearEnvironmentVariable(key = "JPM_CACHE")
    void testGetCacheDirCliOptionOverridesUserConfig() throws IOException {
        // Create a temporary config file with cache setting
        Path configFile = tempDir.resolve("config.yml");
        Files.writeString(configFile, "config:\n  cache: /tmp/cache-from-config\n");

        // Use a test subclass to inject custom UserConfig
        Main.DepsMixin mixin =
                new Main.DepsMixin() {
                    @Override
                    UserConfig getUserConfig() {
                        return UserConfig.read(configFile);
                    }
                };
        mixin.cacheDir = tempCacheDir1.toString(); // CLI option should take precedence

        Path result = mixin.getCacheDir();

        // Should use CLI option, not UserConfig
        assertThat(result).isEqualTo(tempCacheDir1);
        assertThat(errContent.toString()).isEmpty(); // No warnings
    }

    @Test
    @SetEnvironmentVariable(key = "JPM_CACHE", value = "/tmp/cache-from-env")
    void testGetCacheDirUserConfigOverridesEnv() throws IOException {
        // Create a temporary config file with cache setting
        Path configFile = tempDir.resolve("config.yml");
        Files.writeString(configFile, "config:\n  cache: /tmp/cache-from-config\n");

        // Use a test subclass to inject custom UserConfig
        Main.DepsMixin mixin =
                new Main.DepsMixin() {
                    @Override
                    UserConfig getUserConfig() {
                        return UserConfig.read(configFile);
                    }
                };

        Path result = mixin.getCacheDir();

        // UserConfig should override environment variable
        assertThat(result).isNotNull();
        assertThat(result.toString()).isEqualTo(Path.of("/tmp/cache-from-config").toString());
        assertThat(errContent.toString()).isEmpty(); // No warnings
    }

    @Test
    @ClearEnvironmentVariable(key = "JPM_CACHE")
    void testGetCacheDirWithInvalidPathFromUserConfig() throws IOException {
        // Create a temporary config file with invalid cache path
        Path configFile = tempDir.resolve("config.yml");
        Files.writeString(configFile, "config:\n  cache: \"/invalid\\0path/cache\"\n");

        // Use a test subclass to inject custom UserConfig
        Main.DepsMixin mixin =
                new Main.DepsMixin() {
                    @Override
                    UserConfig getUserConfig() {
                        return UserConfig.read(configFile);
                    }
                };

        Path result = mixin.getCacheDir();

        // Should return null when path from UserConfig is invalid
        assertThat(result).isNull();
        // Should print warning to stderr
        assertThat(errContent.toString())
                .contains("Warning: The specified cache path is invalid, ignoring:");
    }
}
