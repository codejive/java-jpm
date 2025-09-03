package org.codejive.jpm.search;

import static org.assertj.core.api.Assertions.*;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.codejive.jpm.search.Search;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.FieldSource;

public class SearchIT {
    @ParameterizedTest
    @EnumSource(Search.Backends.class)
    void testSearchSingleTerm(Search.Backends backend) throws IOException {
        Search s = Search.getBackend(backend);
        Search.SearchResult res = s.findArtifacts("httpclient", 10);
        assertThat(res.count).isGreaterThan(1);
        assertThat(res.artifacts).isNotEmpty();
        res = s.findNextArtifacts(res);
        assertThat(res.count).isGreaterThan(1);
        assertThat(res.artifacts).isNotEmpty();
    }

    @ParameterizedTest
    @EnumSource(Search.Backends.class)
    void testSearchDoubleTerm(Search.Backends backend) throws IOException {
        Search s = Search.getBackend(backend);
        Search.SearchResult res = s.findArtifacts("apache:httpclient", 10);
        assertThat(res.count).isGreaterThan(1);
        assertThat(res.artifacts).isNotEmpty();
        res = s.findNextArtifacts(res);
        assertThat(res.count).isGreaterThan(1);
        assertThat(res.artifacts).isNotEmpty();
    }

    @ParameterizedTest
    @EnumSource(Search.Backends.class)
    void testSearchTripleTerm(Search.Backends backend) throws IOException {
        Search s = Search.getBackend(backend);
        Search.SearchResult res = s.findArtifacts("org.apache.httpcomponents:httpclient:", 10);
        assertThat(res.count).isGreaterThan(1);
        assertThat(res.artifacts).isNotEmpty();
        assertThat(res.artifacts).allMatch(a -> "org.apache.httpcomponents".equals(a.getGroupId()));
        assertThat(res.artifacts).allMatch(a -> "httpclient".equals(a.getArtifactId()));
        res = s.findNextArtifacts(res);
        assertThat(res.count).isGreaterThan(1);
        assertThat(res.artifacts).isNotEmpty();
        assertThat(res.artifacts).allMatch(a -> "org.apache.httpcomponents".equals(a.getGroupId()));
        assertThat(res.artifacts).allMatch(a -> "httpclient".equals(a.getArtifactId()));
    }
}
