package org.codejive.jpm.config;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import org.yaml.snakeyaml.Yaml;

/**
 * Represents the contents of a user configuration file. User configuration files can be located at
 * ~/.config/jpm/config.yml or ~/.jpmcfg.yml and provide default values for common command-line
 * options.
 */
public class UserConfig {
    private Path cache;
    private Path directory;
    private Boolean noLinks;
    private final Map<String, String> repositories = new LinkedHashMap<>();

    /** The primary user config file location (XDG standard). */
    public static final String USER_CONFIG_FILE = ".config/jpm/config.yml";

    /** The fallback user config file location. */
    public static final String USER_CONFIG_FILE_FALLBACK = ".jpmcfg.yml";

    public Path cache() {
        return cache;
    }

    public Path directory() {
        return directory;
    }

    public Boolean noLinks() {
        return noLinks;
    }

    public Map<String, String> repositories() {
        return repositories;
    }

    /**
     * Reads the user configuration file. Priority: explicit path parameter > JPM_CONFIG environment
     * variable > ~/.config/jpm/config.yml > ~/.jpmcfg.yml. If no file exists or can be read,
     * returns an empty UserConfig object.
     *
     * @param explicitConfig Optional explicit config file path from --config option (can be null)
     * @return An instance of UserConfig (never null)
     */
    public static UserConfig read(Path explicitConfig) {
        try {
            // 1. Check explicit --config option
            if (explicitConfig != null) {
                return readFromPath(explicitConfig);
            }

            // 2. Check JPM_CONFIG environment variable
            String envConfig = System.getenv("JPM_CONFIG");
            if (envConfig != null && !envConfig.isEmpty()) {
                Path envConfigPath = Paths.get(envConfig);
                return readFromPath(envConfigPath);
            }

            // 3. Check default locations
            String userHome = System.getProperty("user.home");
            Path primaryConfig = Paths.get(userHome, USER_CONFIG_FILE);

            if (Files.isRegularFile(primaryConfig)) {
                return readFromPath(primaryConfig);
            }

            Path fallbackConfig = Paths.get(userHome, USER_CONFIG_FILE_FALLBACK);
            if (Files.isRegularFile(fallbackConfig)) {
                return readFromPath(fallbackConfig);
            }
        } catch (Exception e) {
            System.err.println("Warning: Error checking for user config file: " + e.getMessage());
        }

        return new UserConfig();
    }

    /**
     * Reads the user configuration file from the default locations. Checks ~/.config/jpm/config.yml
     * first, then ~/.jpmcfg.yml. If neither file exists, returns an empty UserConfig object.
     *
     * <p>This is a convenience method equivalent to calling {@link #read(Path)} with null.
     *
     * @return An instance of UserConfig (never null)
     */
    public static UserConfig read() {
        return read(null);
    }

    /**
     * Reads the user configuration file from the given path. If the file does not exist, returns an
     * empty UserConfig object without warning. If the file exists but cannot be read, returns an
     * empty UserConfig object with a warning.
     *
     * @param configFile The path to the configuration file
     * @return An instance of UserConfig (never null)
     */
    private static UserConfig readFromPath(Path configFile) {
        if (!Files.exists(configFile)) {
            return new UserConfig();
        }
        if (!Files.isRegularFile(configFile)) {
            System.err.println(
                    "Warning: Config path exists but is not a regular file, ignoring: "
                            + configFile);
            return new UserConfig();
        }
        try (Reader in = Files.newBufferedReader(configFile)) {
            return readFromReader(in);
        } catch (IOException e) {
            System.err.println(
                    "Warning: Error reading user config file, ignoring: " + e.getMessage());
        }
        return new UserConfig();
    }

    /**
     * Reads the user configuration from the given Reader and returns its content as a UserConfig
     * object.
     *
     * @param in The Reader to read the configuration content from
     * @return An instance of UserConfig
     */
    @SuppressWarnings("unchecked")
    private static UserConfig readFromReader(Reader in) {
        UserConfig userConfig = new UserConfig();
        try {
            Yaml yaml = new Yaml();
            Map<String, Object> data = yaml.load(in);

            if (data == null || !data.containsKey("config")) {
                return userConfig;
            }

            Object configObj = data.get("config");
            if (!(configObj instanceof Map)) {
                System.err.println("Warning: 'config' section must be a map, ignoring");
                return userConfig;
            }

            Map<String, Object> config = (Map<String, Object>) configObj;

            // Parse cache
            if (config.containsKey("cache")) {
                Object cacheObj = config.get("cache");
                if (cacheObj instanceof String) {
                    String cachePath = (String) cacheObj;
                    userConfig.cache = expandHomePath(cachePath);
                } else {
                    System.err.println(
                            "Warning: 'cache' must be a string path, ignoring: " + cacheObj);
                }
            }

            // Parse directory
            if (config.containsKey("directory")) {
                Object dirObj = config.get("directory");
                if (dirObj instanceof String) {
                    String dirPath = (String) dirObj;
                    userConfig.directory = expandHomePath(dirPath);
                } else {
                    System.err.println(
                            "Warning: 'directory' must be a string path, ignoring: " + dirObj);
                }
            }

            // Parse no-links
            if (config.containsKey("no-links")) {
                Object noLinksObj = config.get("no-links");
                if (noLinksObj instanceof Boolean) {
                    userConfig.noLinks = (Boolean) noLinksObj;
                } else {
                    System.err.println(
                            "Warning: 'no-links' must be a boolean, ignoring: " + noLinksObj);
                }
            }

            // Parse repositories
            if (config.containsKey("repositories")) {
                Object reposObj = config.get("repositories");
                if (reposObj instanceof Map) {
                    Map<String, Object> repos = (Map<String, Object>) reposObj;
                    for (Map.Entry<String, Object> entry : repos.entrySet()) {
                        if (entry.getValue() != null) {
                            userConfig.repositories.put(
                                    entry.getKey(), entry.getValue().toString());
                        }
                    }
                } else {
                    System.err.println(
                            "Warning: 'repositories' must be a map, ignoring: " + reposObj);
                }
            }

        } catch (Exception e) {
            System.err.println(
                    "Warning: Error parsing user config file, ignoring: " + e.getMessage());
        }

        return userConfig;
    }

    /**
     * Expands a path starting with "~/" to use the user's home directory. Handles cross-platform
     * paths.
     *
     * @param pathStr The path string to expand
     * @return A Path with "~/" expanded to the user's home directory
     */
    private static Path expandHomePath(String pathStr) {
        if (pathStr.startsWith("~/")) {
            String userHome = System.getProperty("user.home");
            return Paths.get(userHome, pathStr.substring(2));
        }
        return Paths.get(pathStr);
    }
}
