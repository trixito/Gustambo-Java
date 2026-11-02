// SPDX-FileCopyrightText: Copyright (C) 2021 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.gustambo;

import com.google.gson.JsonObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.Channels;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/// Discord Rich Presence sin librerías externas (IPC local con el Discord de escritorio).
/// Muestra: "Jugando a Gustambo Launcher" / "Trixsafio".
public final class DiscordPresence {
    private DiscordPresence() {
    }

    /// Application ID del portal de desarrolladores de Discord.
    private static final String CLIENT_ID = "1566677126986473534";
    private static final String DETAILS = "Trixsafio";
    /// Clave de la imagen subida en Rich Presence > Art Assets.
    private static final String LARGE_IMAGE_KEY = "logo";
    private static final String LARGE_IMAGE_TEXT = "Gustambo Launcher";

    private static volatile boolean running;
    private static volatile Thread thread;
    private static volatile Closeable0 connection;

    private interface Closeable0 {
        void close();
    }

    public static synchronized void start() {
        if (running) return;
        running = true;
        Thread t = new Thread(DiscordPresence::loop, "Gustambo-DiscordPresence");
        t.setDaemon(true);
        thread = t;
        t.start();
    }

    public static synchronized void stop() {
        running = false;
        Closeable0 c = connection;
        if (c != null) c.close();
        Thread t = thread;
        if (t != null) t.interrupt();
    }

    private static void loop() {
        final long startTime = System.currentTimeMillis() / 1000;
        while (running) {
            try (Ipc ipc = Ipc.open()) {
                connection = ipc::close;
                ipc.send(0, "{\"v\":1,\"client_id\":\"" + CLIENT_ID + "\"}");
                ipc.read(); // READY

                JsonObject assets = new JsonObject();
                assets.addProperty("large_image", LARGE_IMAGE_KEY);
                assets.addProperty("large_text", LARGE_IMAGE_TEXT);

                JsonObject timestamps = new JsonObject();
                timestamps.addProperty("start", startTime);

                JsonObject activity = new JsonObject();
                activity.addProperty("details", DETAILS);
                activity.add("assets", assets);
                activity.add("timestamps", timestamps);

                JsonObject args = new JsonObject();
                args.addProperty("pid", ProcessHandle.current().pid());
                args.add("activity", activity);

                JsonObject frame = new JsonObject();
                frame.addProperty("cmd", "SET_ACTIVITY");
                frame.add("args", args);
                frame.addProperty("nonce", UUID.randomUUID().toString());

                ipc.send(1, frame.toString());
                ipc.read();

                // Mantener la conexión abierta; si Discord se cierra, read() lanza excepción y se reintenta.
                while (running) {
                    ipc.read();
                }
            } catch (Throwable ignored) {
                // Discord no está abierto o se cerró: reintentar más tarde, sin molestar al launcher.
            }
            connection = null;
            if (!running) break;
            try {
                Thread.sleep(15_000);
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    private static final class Ipc implements AutoCloseable {
        private final InputStream in;
        private final OutputStream out;
        private final AutoCloseable resource;

        private Ipc(InputStream in, OutputStream out, AutoCloseable resource) {
            this.in = in;
            this.out = out;
            this.resource = resource;
        }

        static Ipc open() throws IOException {
            boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
            for (int i = 0; i < 10; i++) {
                try {
                    if (windows) {
                        RandomAccessFile file = new RandomAccessFile("\\\\?\\pipe\\discord-ipc-" + i, "rw");
                        InputStream in = Channels.newInputStream(file.getChannel());
                        OutputStream out = Channels.newOutputStream(file.getChannel());
                        return new Ipc(in, out, file);
                    } else {
                        for (String dir : unixDirs()) {
                            Path path = Path.of(dir, "discord-ipc-" + i);
                            if (!Files.exists(path)) continue;
                            SocketChannel ch = SocketChannel.open(StandardProtocolFamily.UNIX);
                            try {
                                ch.connect(UnixDomainSocketAddress.of(path));
                            } catch (IOException e) {
                                ch.close();
                                continue;
                            }
                            return new Ipc(Channels.newInputStream(ch), Channels.newOutputStream(ch), ch);
                        }
                    }
                } catch (IOException ignored) {
                }
            }
            throw new IOException("Discord no encontrado");
        }

        private static String[] unixDirs() {
            String xdg = System.getenv("XDG_RUNTIME_DIR");
            String tmp = firstNonNull(System.getenv("TMPDIR"), System.getenv("TMP"), System.getenv("TEMP"), "/tmp");
            if (xdg != null) {
                return new String[]{xdg, xdg + "/app/com.discordapp.Discord", xdg + "/snap.discord", tmp};
            }
            return new String[]{tmp};
        }

        private static String firstNonNull(String... values) {
            for (String v : values) if (v != null) return v;
            return null;
        }

        void send(int opcode, String json) throws IOException {
            byte[] data = json.getBytes(StandardCharsets.UTF_8);
            ByteBuffer buf = ByteBuffer.allocate(8 + data.length).order(ByteOrder.LITTLE_ENDIAN);
            buf.putInt(opcode).putInt(data.length).put(data);
            out.write(buf.array());
            out.flush();
        }

        String read() throws IOException {
            byte[] header = in.readNBytes(8);
            if (header.length < 8) throw new IOException("Conexión cerrada");
            int len = ByteBuffer.wrap(header, 4, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
            if (len < 0 || len > 1 << 20) throw new IOException("Frame inválido");
            byte[] body = in.readNBytes(len);
            return new String(body, StandardCharsets.UTF_8);
        }

        @Override
        public void close() {
            try {
                resource.close();
            } catch (Throwable ignored) {
            }
        }
    }
}
