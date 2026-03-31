package org.codejive.jpm;

import static org.assertj.core.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
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
        mixin.cacheDir = tempCacheDir1;

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
        mixin.cacheDir = tempCacheDir1; // Command line option is set

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
        mixin.cacheDir = tempCacheDir1;

        // Call multiple times to ensure consistent behavior
        Path result1 = mixin.getCacheDir();
        Path result2 = mixin.getCacheDir();

        assertThat(result1).isEqualTo(tempCacheDir1);
        assertThat(result2).isEqualTo(tempCacheDir1);
        assertThat(result1).isEqualTo(result2);
        assertThat(errContent.toString()).isEmpty(); // No warnings
    }
}
