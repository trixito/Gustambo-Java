// SPDX-FileCopyrightText: Copyright (C) 2021 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.gustambo;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.game.GameInstanceID;
import org.jackhuang.hmcl.game.HMCLGameInstance;
import org.jackhuang.hmcl.game.HMCLGameRepository;
import org.jackhuang.hmcl.game.ModpackHelper;
import org.jackhuang.hmcl.game.QuickPlayType;
import org.jackhuang.hmcl.modpack.Modpack;
import org.jackhuang.hmcl.setting.GameDirectoryManager;
import org.jackhuang.hmcl.setting.GameSettings;
import org.jackhuang.hmcl.task.FileDownloadTask;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane;
import org.jackhuang.hmcl.util.TaskCancellationAction;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Properties;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Instala y mantiene actualizado el modpack del evento.
///
/// Al abrir el launcher consulta el último release de GitHub, y si la versión (tag) es distinta
/// de la instalada descarga el modpack y lo instala/actualiza con el instalador de HMCL.
/// Si no hay internet pero el modpack ya está instalado, simplemente no hace nada.
///
/// El modpack puede ser cualquier `.mrpack` o `.zip` del release (se ignoran jar/exe del launcher).
public final class GustamboModpack {
    private GustamboModpack() {
    }

    /// Repositorio `usuario/repo` donde se publican los releases con el modpack.
    private static final String REPOSITORY = "trixito/Gustambo-Java";

    /// Nombre de la instancia de Minecraft que se crea/actualiza.
    private static final String INSTANCE_NAME = "Trixsafio";

    /// Nombre que se muestra en la interfaz.
    public static final String DISPLAY_NAME = "Trixsafio";

    /// Dirección del servidor (`host` o `host:puerto`) al que entrar directo al jugar.
    /// Vacío = no entrar automáticamente.
    private static final String SERVER_ADDRESS = "";

    /// Nombres de asset aceptados, por orden de preferencia.
    private static final List<String> PREFERRED_ASSETS = List.of("modpack.mrpack", "modpack.zip");

    private static final Path STATE_FILE = Metadata.HMCL_LOCAL_HOME.resolve("gustambo-modpack.properties");

    /// `true` cuando hay una versión nueva del modpack que el jugador aún no ha instalado.
    private static final BooleanProperty UPDATE_AVAILABLE = new SimpleBooleanProperty(false);

    /// Versión nueva detectada y pendiente de instalar (solo hilo de JavaFX).
    private static Release pending;

    /// `true` mientras se descarga/instala el modpack (evita instalaciones dobles).
    private static boolean installing;

    /// Texto de estado para la pantalla principal ("No instalado · versión X", etc.).
    private static final StringProperty STATUS = new SimpleStringProperty("Comprobando…");

    /// Si es `true`, la próxima comprobación instala el modpack aunque no esté instalado.
    private static boolean autoInstall;

    public static ReadOnlyStringProperty statusProperty() {
        return STATUS;
    }

    /// El jugador pidió instalar (botón INSTALAR o icono de la instancia): instala si falta
    /// y, si ya está instalado, comprueba actualizaciones.
    public static void installNow() {
        autoInstall = true;
        start();
    }

    public static ReadOnlyBooleanProperty updateAvailableProperty() {
        return UPDATE_AVAILABLE;
    }

    /// Vuelve a preguntar al jugador si quiere actualizar (si hay una actualización pendiente).
    public static void promptUpdate() {
        if (pending == null) return;
        Release release = pending;
        Controllers.dialog(new MessageDialogPane.Builder(
                "Hay una nueva versión de " + DISPLAY_NAME + " (" + release.tag() + ").\nDebes actualizar para poder jugar.",
                "Actualización obligatoria", MessageDialogPane.MessageType.INFO)
                .addAction("Actualizar", () -> {
                    HMCLGameRepository repository = GameDirectoryManager.getSelectedRepository();
                    install(repository, new GameInstanceID(INSTANCE_NAME), release, true);
                })
                .build());
    }

    private record Release(String tag, String assetName, String assetUrl) {
    }

    /// Punto de entrada: llamar una vez, desde el hilo de JavaFX, cuando los controladores ya están listos.
    public static void start() {
        if (installing) return;
        Schedulers.io().execute(() -> {
            Release release = null;
            try {
                release = fetchLatestRelease();
            } catch (Exception e) {
                LOG.warning("No se pudo consultar el release del modpack", e);
            }

            Release latest = release;
            Platform.runLater(() -> {
                try {
                    onReleaseResolved(latest);
                } catch (Throwable e) {
                    LOG.warning("Error al preparar el modpack", e);
                    showError("No se pudo preparar el modpack.", e);
                }
            });
        });
    }

    private static void onReleaseResolved(Release latest) {
        HMCLGameRepository repository = GameDirectoryManager.getSelectedRepository();
        GameInstanceID id = new GameInstanceID(INSTANCE_NAME);
        boolean installed = isInstalled(repository, id);

        boolean install = autoInstall;
        autoInstall = false;

        if (latest == null) {
            if (!installed) {
                STATUS.set("No instalado · sin conexión");
                if (install) {
                    showError("No se pudo descargar el modpack del evento.\nRevisa tu conexión a internet e inténtalo de nuevo.", null);
                }
            } else {
                String tag = readInstalledTag();
                STATUS.set("Instalado" + (tag == null ? "" : " · versión " + tag));
            }
            return; // ya instalado: se puede jugar sin conexión
        }

        if (installed && latest.tag().equals(readInstalledTag())) {
            pending = null;
            UPDATE_AVAILABLE.set(false);
            STATUS.set("Instalado · versión " + latest.tag());
            return; // ya está al día
        }

        if (!installed) {
            STATUS.set("No instalado · versión " + latest.tag());
            if (install) install(repository, id, latest, false); // solo si el jugador lo pidió
            return;
        }

        // ya instalado pero hay versión nueva: avisar y dejar que el jugador decida
        pending = latest;
        STATUS.set("Actualización obligatoria · versión " + latest.tag());
        UPDATE_AVAILABLE.set(true);
        promptUpdate();
    }

    // ------------------------------------------------------------------ instalación

    private static void install(HMCLGameRepository repository, GameInstanceID id, Release release, boolean update) {
        if (installing) return;
        installing = true;
        Path file = Metadata.getBundledModpackDirectory().resolve(release.assetName());
        try {
            Files.createDirectories(file.getParent());
            Files.deleteIfExists(file);
        } catch (IOException e) {
            installing = false;
            showError("No se pudo preparar la carpeta de descarga.", e);
            return;
        }

        Task<?> download = new FileDownloadTask(release.assetUrl(), file);

        Task<Object> pipeline = download.thenComposeAsync(() -> toObjectTask(createInstallTask(repository, id, file, update)));

        Task<?> whole = pipeline.whenComplete(Schedulers.javafx(),
                () -> {
                    installing = false;
                    onInstalled(repository, id, release, file);
                },
                exception -> {
                    installing = false;
                    LOG.warning("Falló la instalación del modpack", exception);
                    showError(update ? "No se pudo actualizar el modpack." : "No se pudo instalar el modpack.", exception);
                });

        Controllers.taskDialog(whole,
                update ? "Actualizando " + DISPLAY_NAME : "Instalando " + DISPLAY_NAME,
                TaskCancellationAction.NORMAL);
    }

    private static Task<?> createInstallTask(HMCLGameRepository repository, GameInstanceID id, Path file, boolean update) throws Exception {
        if (update) {
            return ModpackHelper.getUpdateTask(repository, file, id,
                    ModpackHelper.readModpackConfiguration(repository.getLayout().getModpackConfigurationFile(id)));
        }
        Modpack modpack = ModpackHelper.readModpackManifest(file);
        return ModpackHelper.getInstallTask(repository, file, id, modpack, null);
    }

    @SuppressWarnings("unchecked")
    private static Task<Object> toObjectTask(Task<?> task) {
        return (Task<Object>) task;
    }

    private static void onInstalled(HMCLGameRepository repository, GameInstanceID id, Release release, Path file) {
        try {
            HMCLGameInstance instance = repository.getInstance(id);
            repository.setSelectedInstance(instance);
            applyServer(instance);
        } catch (Exception e) {
            LOG.warning("No se pudo seleccionar la instancia " + INSTANCE_NAME, e);
        }

        writeInstalledTag(release.tag());
        pending = null;
        UPDATE_AVAILABLE.set(false);
        STATUS.set("Instalado · versión " + release.tag());
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            LOG.warning("No se pudo borrar el modpack descargado", e);
        }
        Controllers.showToast("Modpack " + release.tag() + " listo");
    }

    /// Hace que el juego entre directo al servidor del evento (si [#SERVER_ADDRESS] no está vacío).
    private static void applyServer(HMCLGameInstance instance) {
        if (SERVER_ADDRESS.isBlank()) return;
        try {
            GameSettings.Instance settings = instance.getSettingsOrCreate();
            settings.getOverrideProperties().add("quickPlay");
            settings.getOverrideProperties().add("quickPlayMultiplayer");
            settings.quickPlayProperty().setValue(QuickPlayType.MULTIPLAYER);
            settings.quickPlayMultiplayerProperty().setValue(SERVER_ADDRESS);
            instance.saveSettings();
        } catch (Exception e) {
            LOG.warning("No se pudo configurar el servidor automático", e);
        }
    }

    // ------------------------------------------------------------------ estado local

    private static boolean isInstalled(HMCLGameRepository repository, GameInstanceID id) {
        try {
            return Files.isRegularFile(repository.getLayout().getModpackConfigurationFile(id));
        } catch (Exception e) {
            return false;
        }
    }

    private static String readInstalledTag() {
        if (!Files.isRegularFile(STATE_FILE)) return null;
        try (var reader = Files.newBufferedReader(STATE_FILE, StandardCharsets.UTF_8)) {
            Properties properties = new Properties();
            properties.load(reader);
            return properties.getProperty("tag");
        } catch (IOException e) {
            return null;
        }
    }

    private static void writeInstalledTag(String tag) {
        try {
            Files.createDirectories(STATE_FILE.getParent());
            Properties properties = new Properties();
            properties.setProperty("tag", tag);
            try (var writer = Files.newBufferedWriter(STATE_FILE, StandardCharsets.UTF_8)) {
                properties.store(writer, "GustamboClient modpack");
            }
        } catch (IOException e) {
            LOG.warning("No se pudo guardar la versión instalada del modpack", e);
        }
    }

    // ------------------------------------------------------------------ consulta del release

    private static Release fetchLatestRelease() throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();

        try {
            return fetchViaApi(client);
        } catch (Exception e) {
            // La API de GitHub limita a 60 consultas/hora por IP: en un evento con muchos
            // jugadores en la misma red puede fallar, así que hay una vía sin API.
            LOG.warning("API de GitHub no disponible, probando enlace directo", e);
        }
        return fetchViaRedirect(client);
    }

    private static Release fetchViaApi(HttpClient client) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.github.com/repos/" + REPOSITORY + "/releases/latest"))
                .timeout(Duration.ofSeconds(15))
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", Metadata.NAME)
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            throw new IOException("GitHub API respondió HTTP " + response.statusCode());
        }

        JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
        String tag = root.get("tag_name").getAsString();
        JsonArray assets = root.getAsJsonArray("assets");

        Release chosen = null;
        int bestRank = Integer.MAX_VALUE;
        long bestSize = -1;
        for (JsonElement element : assets) {
            JsonObject asset = element.getAsJsonObject();
            String name = asset.get("name").getAsString();
            String url = asset.get("browser_download_url").getAsString();
            long size = asset.has("size") ? asset.get("size").getAsLong() : 0;
            int rank = rankAsset(name);
            if (rank < 0) continue; // no parece un modpack (jar, exe, etc.)
            // mejor rank gana; a igual rank gana el archivo más grande
            if (rank < bestRank || (rank == bestRank && size > bestSize)) {
                bestRank = rank;
                bestSize = size;
                chosen = new Release(tag, name, url);
            }
        }
        if (chosen == null) {
            throw new IOException("El release " + tag + " no tiene un asset de modpack (modpack.mrpack / modpack.zip)");
        }
        return chosen;
    }

    /// Menor = mejor. Negativo = no es un modpack (se ignora).
    private static int rankAsset(String name) {
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        int index = PREFERRED_ASSETS.indexOf(lower);
        if (index >= 0) return index;
        // nunca elegir el propio launcher
        if (lower.contains("hmcl") || lower.contains("gustambolauncher") || lower.contains("gustamboclient") || lower.contains("launcher")) return -1;
        if (lower.endsWith(".mrpack")) return 10;
        if (lower.endsWith(".zip") && (lower.contains("modpack") || lower.contains("evento"))) return 11;
        if (lower.endsWith(".zip")) return 12;
        return -1;
    }

    private static Release fetchViaRedirect(HttpClient client) throws IOException, InterruptedException {
        for (String name : PREFERRED_ASSETS) {
            HttpRequest request = HttpRequest.newBuilder(URI.create("https://github.com/" + REPOSITORY + "/releases/latest/download/" + name))
                    .timeout(Duration.ofSeconds(15))
                    .header("User-Agent", Metadata.NAME)
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() / 100 != 3) continue;

            String location = response.headers().firstValue("Location").orElse(null);
            if (location == null) continue;

            String marker = "/releases/download/";
            int start = location.indexOf(marker);
            if (start < 0) continue;
            int tagStart = start + marker.length();
            int tagEnd = location.indexOf('/', tagStart);
            if (tagEnd < 0) continue;
            return new Release(location.substring(tagStart, tagEnd), name,
                    "https://github.com/" + REPOSITORY + "/releases/latest/download/" + name);
        }
        throw new IOException("No se encontró modpack.mrpack ni modpack.zip en el último release");
    }

    // ------------------------------------------------------------------ errores

    private static void showError(String message, Throwable cause) {
        String text = cause == null ? message : message + "\n\n" + cause;
        Controllers.dialog(text, "Error", MessageDialogPane.MessageType.ERROR);
    }
}
