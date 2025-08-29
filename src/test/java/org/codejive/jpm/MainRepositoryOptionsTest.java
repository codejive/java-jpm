package org.codejive.jpm;

import static org.assertj.core.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Tests for Main class CLI repository option parsing. */
class MainRepositoryOptionsTest {

    @Test
    void testGetRepositoryMapWithNamedRepositories() {
        Main.BaseArtifactsMixin mixin = new Main.BaseArtifactsMixin();
        mixin.repositories =
                List.of(
                        "central=https://repo1.maven.org/maven2",
                        "jcenter=https://jcenter.bintray.com",
                        "custom=https://my.custom.repo/maven2");

        Map<String, String> result = mixin.getRepositoryMap();

        assertThat(result).hasSize(3);
        assertThat(result)
                .containsEntry("central", "https://repo1.maven.org/maven2")
                .containsEntry("jcenter", "https://jcenter.bintray.com")
                .containsEntry("custom", "https://my.custom.repo/maven2");
    }

    @Test
    void testGetRepositoryMapWithUnnamedRepositories() {
        Main.BaseArtifactsMixin mixin = new Main.BaseArtifactsMixin();
        mixin.repositories =
                List.of(
                        "https://repo1.maven.org/maven2",
                        "https://jcenter.bintray.com",
                        "https://my.custom.repo/maven2");

        Map<String, String> result = mixin.getRepositoryMap();

        assertThat(result).hasSize(3);
        // For unnamed repos, the hostname should be used as the name
        assertThat(result)
                .containsEntry("repo1.maven.org", "https://repo1.maven.org/maven2")
                .containsEntry("jcenter.bintray.com", "https://jcenter.bintray.com")
                .containsEntry("my.custom.repo", "https://my.custom.repo/maven2");
    }

    @Test
    void testGetRepositoryMapWithMixedRepositories() {
        Main.BaseArtifactsMixin mixin = new Main.BaseArtifactsMixin();
        mixin.repositories =
                List.of(
                        "central=https://repo1.maven.org/maven2",
                        "https://jcenter.bintray.com",
                        "custom=https://my.custom.repo/maven2",
                        "https://oss.sonatype.org/content/repositories/snapshots");

        Map<String, String> result = mixin.getRepositoryMap();

        assertThat(result).hasSize(4);
        assertThat(result)
                .containsEntry("central", "https://repo1.maven.org/maven2")
                .containsEntry("jcenter.bintray.com", "https://jcenter.bintray.com")
                .containsEntry("custom", "https://my.custom.repo/maven2")
                .containsEntry(
                        "oss.sonatype.org",
                        "https://oss.sonatype.org/content/repositories/snapshots");
    }

    @Test
    void testGetRepositoryMapWithEmptyList() {
        Main.BaseArtifactsMixin mixin = new Main.BaseArtifactsMixin();
        mixin.repositories = new ArrayList<>();

        Map<String, String> result = mixin.getRepositoryMap();

        assertThat(result).isEmpty();
    }

    @Test
    void testGetRepositoryMapWithInvalidUrls() {
        Main.BaseArtifactsMixin mixin = new Main.BaseArtifactsMixin();
        mixin.repositories =
                List.of(
                        "invalid-url",
                        "also-invalid",
                        "file:///local/path"); // Valid URL but not HTTP

        Map<String, String> result = mixin.getRepositoryMap();

        assertThat(result).hasSize(3);
        // For invalid URLs, the entire string should be used as both name and URL
        assertThat(result)
                .containsEntry("repo1", "invalid-url")
                .containsEntry("repo2", "also-invalid");
        // For file:// URLs, getHost() returns null/empty, so name becomes empty
        assertThat(result).containsEntry("", "file:///local/path");
    }

    @Test
    void testGetRepositoryMapWithEqualsInUrl() {
        Main.BaseArtifactsMixin mixin = new Main.BaseArtifactsMixin();
        mixin.repositories =
                List.of(
                        "nexus=https://nexus.example.com/repository/maven-public/?foo=bar",
                        "https://repo.example.com/path?param=value&other=test");

        Map<String, String> result = mixin.getRepositoryMap();

        assertThat(result).hasSize(2);
        assertThat(result)
                .containsEntry(
                        "nexus", "https://nexus.example.com/repository/maven-public/?foo=bar");
        // The second URL has = in it, so it gets split at the first =
        assertThat(result).containsEntry("https://repo.example.com/path?param", "value&other=test");
    }

    @Test
    void testGetRepositoryMapWithDuplicateNames() {
        Main.BaseArtifactsMixin mixin = new Main.BaseArtifactsMixin();
        mixin.repositories =
                List.of(
                        "central=https://repo1.maven.org/maven2",
                        "central=https://repo.maven.apache.org/maven2"); // Duplicate name

        Map<String, String> result = mixin.getRepositoryMap();

        assertThat(result).hasSize(1);
        // The last one should win
        assertThat(result).containsEntry("central", "https://repo.maven.apache.org/maven2");
    }

    @Test
    void testGetRepositoryMapWithEmptyNameOrUrl() {
        Main.BaseArtifactsMixin mixin = new Main.BaseArtifactsMixin();
        mixin.repositories = List.of("=https://repo1.maven.org/maven2", "name=", "=");

        Map<String, String> result = mixin.getRepositoryMap();

        assertThat(result).hasSize(1);
        // When = is at the beginning, it's not treated as a name=value separator
        assertThat(result).containsEntry("repo1", "https://repo1.maven.org/maven2");
    }
}
