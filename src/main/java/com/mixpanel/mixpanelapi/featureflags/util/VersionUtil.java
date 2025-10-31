package com.mixpanel.mixpanelapi.featureflags.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utility class for accessing the SDK version.
 * <p>
 * The version is loaded from the mixpanel-version.properties file,
 * which is populated by Maven during the build process.
 * </p>
 */
public class VersionUtil {
    private static final Logger logger = Logger.getLogger(VersionUtil.class.getName());
    private static final String VERSION_FILE = "mixpanel-version.properties";
    private static final String VERSION_KEY = "version";
    private static final String UNKNOWN_VERSION = "unknown";

    private static String cachedVersion = null;

    private VersionUtil() {
        // Utility class - prevent instantiation
    }

    /**
     * Gets the SDK version.
     * <p>
     * The version is loaded from the properties file on first access and cached.
     * Returns "unknown" if the version cannot be determined (e.g., running in IDE without build).
     * </p>
     *
     * @return the SDK version string
     */
    public static String getVersion() {
        if (cachedVersion == null) {
            cachedVersion = loadVersion();
        }
        return cachedVersion;
    }

    /**
     * Loads the version from the properties file.
     */
    private static String loadVersion() {
        try (InputStream input = VersionUtil.class.getClassLoader().getResourceAsStream(VERSION_FILE)) {
            if (input == null) {
                logger.log(Level.WARNING, "Version file not found: " + VERSION_FILE + " (using fallback version)");
                return UNKNOWN_VERSION;
            }

            Properties props = new Properties();
            props.load(input);

            String version = props.getProperty(VERSION_KEY);
            if (version == null || version.isEmpty()) {
                logger.log(Level.WARNING, "Version property not found in " + VERSION_FILE);
                return UNKNOWN_VERSION;
            }

            return version;
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to load version from " + VERSION_FILE, e);
            return UNKNOWN_VERSION;
        }
    }
}
