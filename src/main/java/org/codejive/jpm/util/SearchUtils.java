package org.codejive.jpm.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URLEncoder;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.eclipse.aether.artifact.DefaultArtifact;

/** Utility class for searching Maven artifacts. */
public class SearchUtils {

    /**
     * Find artifacts matching the given pattern. This will return the first page of results. If the
     * pattern to search for is a simple name (there are no colons in the string), the search will
     * match any part of an artifact's group or name. If there's a single colon, the search will
     * match any part of the group id and artifact id separately. If there are two colons, the
     * search will match the group id and artifact id exactly, and will return the artifact's
     * versions.
     *
     * @param artifactPattern The pattern to search for.
     * @param count The maximum number of results to return.
     * @return The search result as an instance of {@link SearchResult}.
     * @throws IOException If an error occurred during the search.
     */
    public static SearchResult findArtifacts(String artifactPattern, int count) throws IOException {
        return select(artifactPattern, 0, count);
    }

    /**
     * Find the next page of artifacts. This takes a {@link SearchResult} returned by a previous
     * call to {@link #findArtifacts(String, int)} and returns the next page of results.
     *
     * @param prevResult The previous search result.
     * @return The next search result as an instance of {@link SearchResult}.
     * @throws IOException If an error occurred during the search.
     */
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
            // Exact group/artifact match for retrieving versions
            finalQuery = String.format("g:%s AND a:%s", parts[0], parts[1]);
        } else if (parts.length == 2) {
            // Partial group/artifact match, we will filter the results
            // to remove those that match an inverted artifact/group
            finalQuery = String.format("%s AND %s", parts[0], parts[1]);
        } else {
            // Simple partial match
            finalQuery = query;
        }
        String searchUrl =
                String.format(
                        "https://search.maven.org/solrsearch/select?start=%d&rows=%d&q=p:jar+AND+%s",
                        start, count, URLEncoder.encode(finalQuery, "UTF-8"));
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
                                .filter(d -> acceptDoc(d, parts))
                                .map(SearchUtils::toArtifact)
                                .collect(Collectors.toList());
                return new SearchResult(artifacts, query, start, count, result.response.numFound);
            }
        }
    }

    private static boolean acceptDoc(MsrDoc d, String[] parts) {
        return d.ec != null
                && d.ec.contains(".jar")
                && (parts.length != 2 || d.g.contains(parts[0]) && d.a.contains(parts[1]));
    }

    private static DefaultArtifact toArtifact(MsrDoc d) {
        return new DefaultArtifact(d.g, d.a, "", d.v != null ? d.v : d.latestVersion);
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
    public String p;
    public List<String> ec;
}
