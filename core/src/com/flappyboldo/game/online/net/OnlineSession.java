package com.flappyboldo.game.online.net;

import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Locale;

public class OnlineSession {
    private final String playerName;
    private final String roomIp;
    private final int roomPort;
    private final OnlineClient client;

    private OnlineSession(String playerName, String roomIp, int roomPort, OnlineClient client) {
        this.playerName = playerName;
        this.roomIp = roomIp;
        this.roomPort = roomPort;
        this.client = client;
    }

    public static OnlineSession joinRoom(String playerName, String ip, int port) throws IOException {
        String sanitizedName = sanitizePlayerName(playerName);
        String sanitizedIp = sanitizeIp(ip);
        int sanitizedPort = sanitizePort(port);
        OnlineClient client = new OnlineClient(sanitizedIp, sanitizedPort, sanitizedName);
        client.start();
        return new OnlineSession(sanitizedName, sanitizedIp, sanitizedPort, client);
    }

    public String getPlayerName() {
        return playerName;
    }

    public String getRoomIp() {
        return roomIp;
    }

    public int getRoomPort() {
        return roomPort;
    }

    public int getLocalPlayerId() {
        return client != null ? client.getLocalPlayerId() : 0;
    }

    public void setListener(OnlineClientListener listener) {
        if (client != null) {
            client.setListener(listener);
        }
    }

    public void sendJump(int playerId) {
        if (client != null) {
            client.sendJump(playerId);
        }
    }

    public void sendReady(boolean ready) {
        if (client != null) {
            client.sendReady(ready);
        }
    }

    public void sendRematch() {
        if (client != null) {
            client.sendRematch();
        }
    }

    public boolean isConnected() {
        return client != null && client.isConnectedToRoom();
    }

    public void close() {
        if (client != null) {
            client.closeClient();
        }
    }

    public static boolean isValidIp(String ip) {
        try {
            String value = sanitizeIp(ip);
            InetAddress.getByName(value);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    public static boolean isValidPort(String port) {
        try {
            int value = Integer.parseInt(port == null ? "" : port.trim());
            sanitizePort(value);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    public static int parsePort(String port) {
        int value = Integer.parseInt(port == null ? "" : port.trim());
        return sanitizePort(value);
    }

    private static String sanitizeIp(String ip) {
        String value = ip == null ? "" : ip.trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("IP cannot be empty");
        }
        return value;
    }

    private static int sanitizePort(int port) {
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Port must be between 1 and 65535");
        }
        return port;
    }

    private static String sanitizePlayerName(String name) {
        String value = name == null ? "" : name.trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Name cannot be empty");
        }
        value = value.replace(":", "").replace("|", "").replace(",", "");
        if (value.length() > 20) {
            value = value.substring(0, 20);
        }
        return value;
    }

    @Override
    public String toString() {
        return String.format(Locale.US, "OnlineSession{player=%s, ip=%s, port=%d}", playerName, roomIp, roomPort);
    }
}
