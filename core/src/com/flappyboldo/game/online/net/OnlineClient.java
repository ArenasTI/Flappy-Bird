package com.flappyboldo.game.online.net;

import com.badlogic.gdx.Gdx;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

public class OnlineClient extends Thread {
    private static final int RECEIVE_BUFFER_SIZE = 1024;
    private static final int SOCKET_TIMEOUT_MS = 250;
    private static final long CONNECT_TIMEOUT_MS = 4000L;
    private static final long SERVER_TIMEOUT_MS = 8000L;
    private static final long PING_INTERVAL_MS = 500L;

    private final DatagramSocket socket;
    private final InetAddress serverIp;
    private final int serverPort;
    private final String playerName;

    private volatile boolean running;
    private volatile OnlineClientListener listener;

    private boolean connected;
    private int localPlayerId;

    private long connectStartMs;
    private long lastServerMessageMs;
    private long lastPingMs;
    private boolean disconnectionNotified;

    public OnlineClient(String serverIp, int serverPort, String playerName)
        throws UnknownHostException, SocketException {
        this.serverIp = InetAddress.getByName(serverIp);
        this.serverPort = serverPort;
        this.playerName = sanitizeName(playerName);
        this.socket = new DatagramSocket();
        this.socket.setSoTimeout(SOCKET_TIMEOUT_MS);
        this.running = true;
        this.connected = false;
        this.localPlayerId = 0;
    }

    @Override
    public void run() {
        connectStartMs = System.currentTimeMillis();
        lastServerMessageMs = connectStartMs;
        lastPingMs = 0L;

        sendRaw(OnlineProtocol.HELLO + ":" + playerName);

        byte[] buffer = new byte[RECEIVE_BUFFER_SIZE];
        while (running) {
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            try {
                socket.receive(packet);
                lastServerMessageMs = System.currentTimeMillis();
                handlePacket(packet);
            } catch (SocketTimeoutException ignored) {
                // Tick cliente.
            } catch (IOException e) {
                if (running) {
                    notifyError("Network error: " + e.getMessage());
                }
            }

            long now = System.currentTimeMillis();
            tickPing(now);
            tickTimeouts(now);
        }
    }

    public void setListener(OnlineClientListener listener) {
        this.listener = listener;
    }

    public int getLocalPlayerId() {
        return localPlayerId;
    }

    public boolean isConnectedToRoom() {
        return connected;
    }

    public synchronized void sendJump(int playerId) {
        if (!running || !connected) {
            return;
        }
        sendRaw(OnlineProtocol.JUMP + ":" + playerId);
    }

    public synchronized void sendReady(boolean ready) {
        if (!running || !connected) {
            return;
        }
        sendRaw(OnlineProtocol.READY + ":" + (ready ? "1" : "0"));
    }

    public synchronized void sendRematch() {
        if (!running || !connected) {
            return;
        }
        sendRaw(OnlineProtocol.REMATCH + ":1");
    }

    public synchronized void closeClient() {
        if (!running) {
            return;
        }
        if (connected && localPlayerId > 0) {
            sendRaw(OnlineProtocol.LEAVE + ":" + localPlayerId);
        }
        running = false;
        socket.close();
        interrupt();
    }

    private void handlePacket(DatagramPacket packet) {
        String raw = new String(packet.getData(), 0, packet.getLength()).trim();
        if (raw.isEmpty()) {
            return;
        }

        String[] parts = raw.split(":", 8);
        String command = parts[0];

        if (OnlineProtocol.WELCOME.equals(command) && parts.length > 1) {
            int id = parseInt(parts[1], 0);
            if (id > 0) {
                connected = true;
                localPlayerId = id;
                notifyConnected(id);
            }
            return;
        }

        if (OnlineProtocol.ROOM.equals(command)) {
            String payload = "";
            if (parts.length > 4) {
                payload = parts[4];
            } else if (parts.length > 2) {
                payload = parts[2];
            }
            notifyRoomUpdate(parseRoomPlayers(payload));
            return;
        }

        if (OnlineProtocol.START_GAME.equals(command) && parts.length > 3) {
            float spawnX = parseFloat(parts[1], 0f);
            float spawnY = parseFloat(parts[2], 0f);
            long startDelayMs = parseLong(parts[3], 0L);
            dispatch(listener -> listener.onStartGame(spawnX, spawnY, startDelayMs));
            return;
        }

        if (OnlineProtocol.JUMP.equals(command) && parts.length > 1) {
            int playerId = parseInt(parts[1], 0);
            if (playerId > 0) {
                dispatch(listener -> listener.onRemoteJump(playerId));
            }
            return;
        }

        if (OnlineProtocol.SPAWN.equals(command) && parts.length > 1) {
            float gapCenterY = parseFloat(parts[1], 0f);
            dispatch(listener -> listener.onSpawnPipe(gapCenterY));
            return;
        }

        if (OnlineProtocol.ELIMINATED.equals(command) && parts.length > 1) {
            int playerId = parseInt(parts[1], 0);
            if (playerId > 0) {
                dispatch(listener -> listener.onEliminated(playerId));
            }
            return;
        }

        if (OnlineProtocol.FIN.equals(command) && parts.length > 1) {
            int winnerId = parseInt(parts[1], 0);
            dispatch(listener -> listener.onGameFinished(winnerId));
            return;
        }

        if (OnlineProtocol.CLIENT_LEFT.equals(command) && parts.length > 1) {
            int playerId = parseInt(parts[1], 0);
            if (playerId > 0) {
                dispatch(listener -> listener.onPlayerLeft(playerId));
            }
            return;
        }

        if (OnlineProtocol.SERVER_CLOSED.equals(command)) {
            notifyServerClosed("The server closed the room");
            return;
        }

        if (OnlineProtocol.ERROR.equals(command)) {
            String message = parts.length > 1 ? parts[1] : "Unknown error";
            notifyError(mapError(message));
        }
    }

    private void tickPing(long nowMs) {
        if (!running) {
            return;
        }
        if (nowMs - lastPingMs >= PING_INTERVAL_MS) {
            sendRaw(OnlineProtocol.PING);
            lastPingMs = nowMs;
        }
    }

    private void tickTimeouts(long nowMs) {
        if (!running || disconnectionNotified) {
            return;
        }

        if (!connected && nowMs - connectStartMs >= CONNECT_TIMEOUT_MS) {
            disconnectionNotified = true;
            notifyError("Could not connect to the room");
            closeClient();
            return;
        }

        if (connected && nowMs - lastServerMessageMs >= SERVER_TIMEOUT_MS) {
            disconnectionNotified = true;
            notifyServerClosed("Lost connection to the server");
            closeClient();
        }
    }

    private void sendRaw(String message) {
        if (!running) {
            return;
        }
        try {
            byte[] data = message.getBytes();
            DatagramPacket packet = new DatagramPacket(data, data.length, serverIp, serverPort);
            socket.send(packet);
        } catch (IOException ignored) {
            // Se notificara por timeout.
        }
    }

    private void notifyConnected(int id) {
        dispatch(listener -> listener.onConnected(id));
    }

    private void notifyRoomUpdate(List<String> names) {
        dispatch(listener -> listener.onRoomUpdate(names));
    }

    private void notifyServerClosed(String reason) {
        dispatch(listener -> listener.onServerClosed(reason));
    }

    private void notifyError(String message) {
        dispatch(listener -> listener.onError(message));
    }

    private void dispatch(java.util.function.Consumer<OnlineClientListener> callback) {
        OnlineClientListener current = listener;
        if (current == null) {
            return;
        }
        if (Gdx.app == null) {
            callback.accept(current);
            return;
        }
        Gdx.app.postRunnable(() -> {
            OnlineClientListener latest = listener;
            if (latest != null) {
                callback.accept(latest);
            }
        });
    }

    private int parseInt(String raw, int fallback) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private float parseFloat(String raw, float fallback) {
        try {
            return Float.parseFloat(raw.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private long parseLong(String raw, long fallback) {
        try {
            return Long.parseLong(raw.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private List<String> parseRoomPlayers(String payload) {
        List<String> result = new ArrayList<>();
        if (payload == null || payload.trim().isEmpty()) {
            return result;
        }
        String[] entries = payload.split("\\|");
        for (String entry : entries) {
            if (entry == null || entry.trim().isEmpty()) {
                continue;
            }
            String[] pair = entry.split(",", 5);
            if (pair.length >= 4) {
                int id = parseInt(pair[0], 0);
                String name = pair[1];
                boolean ready = "1".equals(pair[2].trim());
                int score = parseInt(pair[3], 0);
                boolean alive = pair.length > 4 && "1".equals(pair[4].trim());
                result.add("P" + id + " - " + name
                    + (ready ? " [READY]" : " [WAIT]")
                    + " [S:" + score + "]"
                    + (alive ? " [ALIVE]" : " [OUT]"));
            } else if (pair.length >= 2) {
                boolean ready = pair.length > 2 && "1".equals(pair[2].trim());
                result.add("P" + pair[0] + " - " + pair[1] + (ready ? " [READY]" : " [WAIT]"));
            } else {
                result.add(entry.trim());
            }
        }
        return result;
    }

    private String sanitizeName(String raw) {
        String trimmed = raw == null ? "" : raw.trim();
        if (trimmed.isEmpty()) {
            return "Player";
        }
        String safe = trimmed.replace(":", "").replace("|", "").replace(",", "");
        if (safe.length() > 20) {
            return safe.substring(0, 20);
        }
        return safe;
    }

    private String mapError(String rawError) {
        if (OnlineProtocol.ERROR_SERVER_FULL.equals(rawError)) {
            return "Room is full";
        }
        if (OnlineProtocol.ERROR_INVALID_MSG.equals(rawError)) {
            return "Invalid message received";
        }
        return rawError;
    }
}
