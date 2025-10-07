package org.codejive.jpm.config;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

/**
 * Represents the contents of an app.yml file. There are methods for reading and writing instances
 * from/to files.
 */
public class AppInfo {
    private Map<String, Object> yaml = new LinkedHashMap<>();

    public Map<String, String> dependencies = new LinkedHashMap<>();
    public Map<String, String> repositories = new LinkedHashMap<>();
    public Map<String, String> actions = new LinkedHashMap<>();

    /** The official name of the app.yml file. */
    public static final String APP_INFO_FILE = "app.yml";

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
     * Returns the action command for the given action name.
     *
     * @param actionName The name of the action
     * @return The action command or null if not found
     */
    public String getAction(String actionName) {
        return actions != null ? actions.get(actionName) : null;
    }

    /**
     * Returns all available action names.
     *
     * @return A set of action names
     */
    public java.util.Set<String> getActionNames() {
        return actions != null ? actions.keySet() : java.util.Collections.emptySet();
    }

    /**
     * Reads the app.yml file in the current directory and returns its content as an AppInfo object.
     * If the file does not exist, an empty AppInfo object is returned.
     *
     * @return An instance of AppInfo
     * @throws IOException if an error occurred while reading or parsing the file
     */
    @SuppressWarnings("unchecked")
    public static AppInfo read() throws IOException {
        Path appInfoFile = Paths.get(System.getProperty("user.dir"), APP_INFO_FILE);
        return read(appInfoFile);
    }

    /**
     * Reads the app.yml file in the current directory and returns its content as an AppInfo object.
     * If the file does not exist, an empty AppInfo object is returned.
     *
     * @param appInfoFile The path to the app.yml file
     * @return An instance of AppInfo
     * @throws IOException if an error occurred while reading or parsing the file
     */
    @SuppressWarnings("unchecked")
    public static AppInfo read(Path appInfoFile) throws IOException {
        if (Files.isRegularFile(appInfoFile)) {
            try (Reader in = Files.newBufferedReader(appInfoFile)) {
                return read(in);
            }
        }
        return new AppInfo();
    }

    /**
     * Reads the app.yml from the given Reader and returns its content as an AppInfo object.
     *
     * @param in The Reader to read the app.yml content from
     * @return An instance of AppInfo
     */
    @SuppressWarnings("unchecked")
    public static AppInfo read(Reader in) {
        AppInfo appInfo = new AppInfo();
        Yaml yaml = new Yaml();
        appInfo.yaml = yaml.load(in);
        // Ensure yaml is never null
        if (appInfo.yaml == null) {
            appInfo.yaml = new LinkedHashMap<>();
        }
        // We now take any known information from the Yaml map and transfer it to their
        // respective fields in the AppInfo object, leaving unknown information untouched
        // WARNING awful code ahead
        // Parse dependencies section
        if (appInfo.yaml.containsKey("dependencies")
                && appInfo.yaml.get("dependencies") instanceof Map) {
            Map<String, Object> deps = (Map<String, Object>) appInfo.yaml.get("dependencies");
            for (Map.Entry<String, Object> entry : deps.entrySet()) {
                appInfo.dependencies.put(entry.getKey(), entry.getValue().toString());
            }
        }
        // Parse repositories section
        if (appInfo.yaml.containsKey("repositories")
                && appInfo.yaml.get("repositories") instanceof Map) {
            Map<String, Object> deps = (Map<String, Object>) appInfo.yaml.get("repositories");
            for (Map.Entry<String, Object> entry : deps.entrySet()) {
                appInfo.repositories.put(entry.getKey(), entry.getValue().toString());
            }
        }
        // Parse actions section
        if (appInfo.yaml.containsKey("actions") && appInfo.yaml.get("actions") instanceof Map) {
            Map<String, Object> actions = (Map<String, Object>) appInfo.yaml.get("actions");
            for (Map.Entry<String, Object> entry : actions.entrySet()) {
                appInfo.actions.put(entry.getKey(), entry.getValue().toString());
            }
        }
        return appInfo;
    }

    /**
     * Writes the AppInfo object to the app.yml file in the current directory.
     *
     * @param appInfo The AppInfo object to write
     * @throws IOException if an error occurred while writing the file
     */
    @SuppressWarnings("unchecked")
    public static void write(AppInfo appInfo) throws IOException {
        Path appInfoFile = Paths.get(System.getProperty("user.dir"), APP_INFO_FILE);
        write(appInfo, appInfoFile);
    }

    /**
     * Writes the AppInfo object to the given path.
     *
     * @param appInfo The AppInfo object to write
     * @param appInfoFile The path to write the app.yml file to
     * @throws IOException if an error occurred while writing the file
     */
    @SuppressWarnings("unchecked")
    public static void write(AppInfo appInfo, Path appInfoFile) throws IOException {
        try (Writer out = Files.newBufferedWriter(appInfoFile)) {
            write(appInfo, out);
        }
    }

    /**
     * Writes the AppInfo object to the given Writer.
     *
     * @param appInfo The AppInfo object to write
     * @param out The Writer to write to
     */
    @SuppressWarnings("unchecked")
    public static void write(AppInfo appInfo, Writer out) {
        DumperOptions dopts = new DumperOptions();
        dopts.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        dopts.setPrettyFlow(true);
        Yaml yaml = new Yaml(dopts);
        // WARNING awful code ahead
        appInfo.yaml.put("dependencies", (Map<String, Object>) (Map) appInfo.dependencies);
        if (!appInfo.repositories.isEmpty()) {
            appInfo.yaml.put("repositories", (Map<String, Object>) (Map) appInfo.repositories);
        } else {
            appInfo.yaml.remove("repositories");
        }
        if (!appInfo.actions.isEmpty()) {
            appInfo.yaml.put("actions", (Map<String, Object>) (Map) appInfo.actions);
        } else {
            appInfo.yaml.remove("actions");
        }
        yaml.dump(appInfo.yaml, out);
    }
}
