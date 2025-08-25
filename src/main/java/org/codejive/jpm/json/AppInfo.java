package org.codejive.jpm.json;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.TreeMap;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

/**
 * Represents the contents of an app.yml file. There are methods for reading and writing instances
 * from/to files.
 */
public class AppInfo {
    private Map<String, Object> yaml = new TreeMap<>();
    public Map<String, String> dependencies = new TreeMap<>();
    public Map<String, String> actions = new TreeMap<>();

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
        return actions.get(actionName);
    }

    /**
     * Returns all available action names.
     *
     * @return A set of action names
     */
    public java.util.Set<String> getActionNames() {
        return actions.keySet();
    }

    /**
     * Reads the app.yml file in the current directory and returns its content as an AppInfo object.
     *
     * @return An instance of AppInfo
     * @throws IOException if an error occurred while reading or parsing the file
     */
    public static AppInfo read() throws IOException {
        Path prjJson = Paths.get(APP_INFO_FILE);
        AppInfo appInfo = new AppInfo();
        if (Files.isRegularFile(prjJson)) {
            try (Reader in = Files.newBufferedReader(prjJson)) {
                Yaml yaml = new Yaml();
                appInfo.yaml = yaml.load(in);
            }
        } else {
            appInfo = new AppInfo();
        }
        // WARNING awful code ahead
        if (appInfo.yaml.containsKey("dependencies")
                && appInfo.yaml.get("dependencies") instanceof Map) {
            Map<String, Object> deps = (Map<String, Object>) appInfo.yaml.get("dependencies");
            for (Map.Entry<String, Object> entry : deps.entrySet()) {
                appInfo.dependencies.put(entry.getKey(), entry.getValue().toString());
            }
        }
        // Parse actions section
        if (appInfo.yaml.containsKey("actions")
                && appInfo.yaml.get("actions") instanceof Map) {
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
    public static void write(AppInfo appInfo) throws IOException {
        Path prjJson = Paths.get(APP_INFO_FILE);
        try (Writer out = Files.newBufferedWriter(prjJson)) {
            DumperOptions dopts = new DumperOptions();
            dopts.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
            dopts.setPrettyFlow(true);
            Yaml yaml = new Yaml(dopts);
            // WARNING awful code ahead
            appInfo.yaml.put("dependencies", (Map<String, Object>) (Map) appInfo.dependencies);
            appInfo.yaml.put("actions", (Map<String, Object>) (Map) appInfo.actions);
            yaml.dump(appInfo.yaml, out);
        }
    }
}
