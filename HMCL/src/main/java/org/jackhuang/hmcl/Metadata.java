// SPDX-FileCopyrightText: Copyright (C) 2021 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl;

import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.io.JarUtils;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Stores metadata about this application.
 */
public final class Metadata {
    private Metadata() {
    }

    public static final String NAME = "GustamboLauncher";
    public static final String FULL_NAME = "Gustambo Launcher";
    public static final String VERSION = System.getProperty("hmcl.version.override", JarUtils.getAttribute("hmcl.version", "@develop@"));

    /// Explicit Application User Model ID used for Windows taskbar grouping and pinning.
    public static final String WINDOWS_APP_USER_MODEL_ID = "net.trixito.gustambo.client";

    public static final String TITLE = NAME + " " + VERSION;

    /// Whether the build metadata identifies this launcher as an official build.
    public static final boolean OFFICIAL_BUILD = Boolean.parseBoolean(JarUtils.getAttribute("hmcl.official", "false"));

    public static final int MINIMUM_REQUIRED_JAVA_VERSION = 17;
    public static final int MINIMUM_SUPPORTED_JAVA_VERSION = 17;
    public static final int RECOMMENDED_JAVA_VERSION = 21;

    public static final String PUBLISH_URL = "https://github.com/trixito/Gustambo-Java";
    public static final String REPOSITORY_URL = "https://github.com/trixito/Gustambo-Java";
    public static final String HMCL_UPDATE_URL = System.getProperty("hmcl.update_source.override", PUBLISH_URL + "/api/update_link");
    public static final String MANUAL_UPDATE_URL = "https://github.com/trixito/Gustambo-Java/releases";

    // GustamboClient: enlaces propios (antes apuntaban a docs.hmcl.net y bilibili)
    public static final String DOCS_URL = REPOSITORY_URL;
    public static final String CONTACT_URL = REPOSITORY_URL + "/issues";
    public static final String CHANGELOG_URL = MANUAL_UPDATE_URL;
    public static final String EULA_URL = REPOSITORY_URL;
    public static final String GROUPS_URL = REPOSITORY_URL;

    public static final String BUILD_CHANNEL = JarUtils.getAttribute("hmcl.version.type", "nightly");
    public static final String GITHUB_SHA = JarUtils.getAttribute("hmcl.version.hash", null);

    public static final Path CURRENT_DIRECTORY = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
    /// Carpeta de juego propia de GustamboClient (no toca el .minecraft del jugador).
    public static final Path MINECRAFT_DIRECTORY;
    public static final Path HMCL_USER_HOME;
    public static final Path HMCL_LOCAL_HOME;
    public static final Path DEPENDENCIES_DIRECTORY;

    static {
        String hmclHome = System.getProperty("hmcl.home", System.getenv("HMCL_USER_HOME"));
        if (StringUtils.isBlank(hmclHome)) {
            if (OperatingSystem.CURRENT_OS.isLinuxOrBSD()) {
                String xdgData = System.getenv("XDG_DATA_HOME");
                if (StringUtils.isNotBlank(xdgData)) {
                    HMCL_USER_HOME = Path.of(xdgData, "gustambo").toAbsolutePath().normalize();
                } else {
                    HMCL_USER_HOME = Path.of(System.getProperty("user.home"), ".local", "share", "gustambo").toAbsolutePath().normalize();
                }
            } else {
                HMCL_USER_HOME = OperatingSystem.getWorkingDirectory("gustambo");
            }
        } else {
            HMCL_USER_HOME = Path.of(hmclHome).toAbsolutePath().normalize();
        }

        MINECRAFT_DIRECTORY = HMCL_USER_HOME.resolve("minecraft");

        String hmclCurrentDir = System.getProperty("hmcl.dir", System.getenv("HMCL_LOCAL_HOME"));
        HMCL_LOCAL_HOME = StringUtils.isNotBlank(hmclCurrentDir)
                ? Path.of(hmclCurrentDir).toAbsolutePath().normalize()
                : CURRENT_DIRECTORY.resolve(".gustambo");

        String hmclDependencies = System.getProperty("hmcl.dependencies.dir", System.getenv("HMCL_DEPENDENCIES_DIR"));
        DEPENDENCIES_DIRECTORY = StringUtils.isNotBlank(hmclDependencies)
                ? Path.of(hmclDependencies).toAbsolutePath().normalize()
                : HMCL_LOCAL_HOME.resolve("dependencies");
    }

    public static boolean isStable() {
        return "stable".equals(BUILD_CHANNEL);
    }

    public static boolean isDev() {
        return "dev".equals(BUILD_CHANNEL);
    }

    public static boolean isNightly() {
        return !isStable() && !isDev();
    }

    public static @Nullable String getSuggestedJavaDownloadLink() {
        return "https://adoptium.net/temurin/releases/?version=21";
    }

    /// Directory under [HMCL_LOCAL_HOME] that holds a launcher-bundled modpack for automatic install.
    public static final String BUNDLED_MODPACK_DIRECTORY_NAME = "modpack";

    /// Returns the directory for a launcher-bundled modpack (`[HMCL_LOCAL_HOME]/modpack`).
    public static Path getBundledModpackDirectory() {
        return HMCL_LOCAL_HOME.resolve(BUNDLED_MODPACK_DIRECTORY_NAME);
    }

    /// Returns the bundled modpack package under [getBundledModpackDirectory], if present.
    ///
    /// Prefers `modpack.zip` over `modpack.mrpack` when both exist. Presence of the package is the
    /// signal to offer automatic install; the file is removed after a successful install.
    ///
    /// @return the modpack path, or `null` when no package is present
    public static @Nullable Path findBundledModpackFile() {
        Path directory = getBundledModpackDirectory();
        Path zipModpack = directory.resolve("modpack.zip");
        if (Files.isRegularFile(zipModpack)) {
            return zipModpack;
        }
        Path mrpackModpack = directory.resolve("modpack.mrpack");
        if (Files.isRegularFile(mrpackModpack)) {
            return mrpackModpack;
        }
        return null;
    }

}
