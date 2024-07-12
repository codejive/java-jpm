package org.codejive.jpm.json;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.TreeMap;

/**
 * Represents the contents of an app.json file. There are methods for reading and writing instances
 * from/to files.
 */
public class AppInfo {
    private Map<String, Object> json;
    public Map<String, String> dependencies = new TreeMap<>();

    /** The official name of the app.json file. */
    public static final String APP_INFO_FILE = "app.json";

    /**
     * Returns the dependencies as an array of strings in the format "groupId:artifactId:version".
     *
     * @return An array of strings
     */
    public String[] getDependencyGAVs() {
        return dependencies.entrySet().stream()
                .map(e -> e.getKey() + ":" + e.getValue())
                .toArray(String[]::new);
    }

    /**
     * Reads the app.json file in the current directory and returns its content as an AppInfo
     * object.
     *
     * @return An instance of AppInfo
     * @throws IOException if an error occurred while reading or parsing the file
     */
    public static AppInfo read() throws IOException {
        Path prjJson = Paths.get(APP_INFO_FILE);
        AppInfo appInfo = new AppInfo();
        if (Files.isRegularFile(prjJson)) {
            try (Reader in = Files.newBufferedReader(prjJson)) {
                Gson parser = new GsonBuilder().create();
                appInfo.json = parser.fromJson(in, Map.class);
            }
        } else {
            appInfo = new AppInfo();
        }
        // WARNING awful code ahead
        if (appInfo.json.containsKey("dependencies")
                && appInfo.json.get("dependencies") instanceof Map) {
            appInfo.dependencies.putAll((Map<String, String>) appInfo.json.get("dependencies"));
        }
        return appInfo;
    }

    /**
     * Writes the AppInfo object to the app.json file in the current directory.
     *
     * @param appInfo The AppInfo object to write
     * @throws IOException if an error occurred while writing the file
     */
    public static void write(AppInfo appInfo) throws IOException {
        Path prjJson = Paths.get(APP_INFO_FILE);
        try (Writer out = Files.newBufferedWriter(prjJson)) {
            Gson parser = new GsonBuilder().setPrettyPrinting().create();
            // WARNING awful code ahead
            appInfo.json.put("dependencies", (Map<String, Object>) (Map) appInfo.dependencies);
            parser.toJson(appInfo.json, out);
        }
    }
}
