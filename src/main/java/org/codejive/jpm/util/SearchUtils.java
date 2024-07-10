package org.codejive.jpm.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URLEncoder;
import java.util.Collections;
import java.util.List;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;

public class SearchUtils {

    public static class SearchResult {
        public final List<? extends Artifact> artifacts;
        public final String query;
        public final int start;
        public final int count;
        public final int total;

        public SearchResult(
                List<? extends Artifact> artifacts, String query, int start, int count, int total) {
            this.artifacts = Collections.unmodifiableList(artifacts);
            this.query = query;
            this.start = start;
            this.count = count;
            this.total = total;
        }
    }

    public static SearchResult findArtifacts(String artifactPattern, int count) throws IOException {
        return select(artifactPattern, 0, count);
    }

    public static SearchResult findNextArtifacts(SearchResult prevResult) throws IOException {
        if (prevResult.start + prevResult.count >= prevResult.total) {
            return null;
        }
        SearchResult result =
                select(prevResult.query, prevResult.start + prevResult.count, prevResult.count);
        return result.artifacts.isEmpty() ? null : result;
    }

    private static SearchResult select(String query, int start, int count) throws IOException {
        String[] parts = query.split(":", -1);
        String finalQuery;
        if (parts.length >= 3) {
            finalQuery = "g:%s AND a:%s".formatted(parts[0], parts[1]);
        } else if (parts.length == 2) {
            finalQuery = "%s AND %s".formatted(parts[0], parts[1]);
        } else {
            finalQuery = query;
        }
        String searchUrl =
                "https://search.maven.org/solrsearch/select?start=%d&rows=%d&q=%s"
                        .formatted(start, count, URLEncoder.encode(finalQuery, "UTF-8"));
        if (parts.length >= 3) {
            searchUrl += "&core=gav";
        }
        String agent =
                String.format(
                        "jpm/%s (%s %s)",
                        Version.get(),
                        System.getProperty("os.name"),
                        System.getProperty("os.arch"));
        try (CloseableHttpClient httpClient = HttpClients.custom().setUserAgent(agent).build()) {
            HttpGet request = new HttpGet(searchUrl);
            try (CloseableHttpResponse response = httpClient.execute(request)) {
                Gson gson = new GsonBuilder().create();
                InputStream ins = response.getEntity().getContent();
                InputStreamReader rdr = new InputStreamReader(ins);
                MvnSearchResult result = gson.fromJson(rdr, MvnSearchResult.class);
                if (result.responseHeader.status != 0) {
                    throw new IOException("Search failed");
                }
                List<DefaultArtifact> artifacts =
                        result.response.docs.stream()
                                .filter(
                                        d ->
                                                parts.length != 2
                                                        || d.g.contains(parts[0])
                                                                && d.a.contains(parts[1]))
                                .map(
                                        d ->
                                                new DefaultArtifact(
                                                        d.g,
                                                        d.a,
                                                        "",
                                                        d.v != null ? d.v : d.latestVersion))
                                .toList();
                return new SearchResult(artifacts, query, start, count, result.response.numFound);
            }
        }
    }
}

class MvnSearchResult {
    public MsrHeader responseHeader;
    public MsrResponse response;
}

class MsrHeader {
    public int status;
}

class MsrResponse {
    public List<MsrDoc> docs;
    public int numFound;
    public int start;
}

class MsrDoc {
    public String g;
    public String a;
    public String v;
    public String latestVersion;
}
