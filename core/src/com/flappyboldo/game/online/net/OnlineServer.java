package com.flappyboldo.game.online.net;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.StringJoiner;

public class OnlineServer extends Thread {
    private static final int MAX_CLIENTS = 2;
    private static final int RECEIVE_BUFFER_SIZE = 1024;
    private static final int SOCKET_TIMEOUT_MS = 16;
    private static final long CLIENT_TIMEOUT_MS = 1800L;
    private static final long SPAWN_MIN_MS = 1100L;
    private static final long SPAWN_MAX_MS = 2200L;
    private static final long START_DELAY_MS = 1200L;
    private static final float FIXED_STEP_SECONDS = 1f / 120f;
    private static final float MAX_FRAME_DELTA_SECONDS = 0.25f;

    private static final float WORLD_HEIGHT = 200f;
    private static final float WORLD_WIDTH = 100f;
    private static final float GROUND_HEIGHT = 0.15f * WORLD_HEIGHT;
    private static final float PIPE_WIDTH = WORLD_WIDTH / 6f;
    private static final float PIPE_GAP_HEIGHT = WORLD_HEIGHT / 3f;
    private static final float PIPE_SPEED = 50f;
    private static final float BIRD_WIDTH = 0.15f * WORLD_WIDTH;
    private static final float BIRD_HEIGHT = WORLD_HEIGHT / 17f;
    private static final float GRAVITY = 400f;
    private static final float JUMP_FORCE = 130f;

    private static final float GAP_MIN_CENTER = WORLD_HEIGHT * 0.30f;
    private static final float GAP_MAX_CENTER = WORLD_HEIGHT * 0.70f;
    private static final float START_X = 24f;
    private static final float START_Y = 100f;

    private final DatagramSocket socket;
    private final int port;
    private final Random random;

    private final List<ClientInfo> clients;
    private final List<PipeState> pipes;

    private volatile boolean running;
    private long nextSpawnAtMs;
    private long lastSimulationTickMs;
    private long matchStartsAtMs;
    private float simulationAccumulatorSeconds;
    private long simulationTick;
    private RoomState roomState;
    private int lastWinnerId;

    public OnlineServer(int port) throws SocketException {
        this.port = port;
        this.socket = new DatagramSocket(port);
        this.socket.setSoTimeout(SOCKET_TIMEOUT_MS);
        this.random = new Random();
        this.clients = new ArrayList<>(MAX_CLIENTS);
        this.pipes = new ArrayList<>();
        this.running = true;
        this.roomState = RoomState.WAITING;
        this.lastWinnerId = 0;
        this.lastSimulationTickMs = System.currentTimeMillis();
        this.matchStartsAtMs = 0L;
        this.simulationAccumulatorSeconds = 0f;
        this.simulationTick = 0L;
    }

    @Override
    public void run() {
        byte[] buffer = new byte[RECEIVE_BUFFER_SIZE];
        while (running) {
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            try {
                socket.receive(packet);
                handlePacket(packet);
            } catch (SocketTimeoutException ignored) {
                // Tick de servidor.
            } catch (IOException e) {
                if (running) {
                    System.out.println("Online server error: " + e.getMessage());
                }
            }

            long now = System.currentTimeMillis();
            tickTimeouts(now);
            tickMatch(now);
        }
    }

    public synchronized void closeServer() {
        if (!running) {
            return;
        }
        running = false;
        broadcast(OnlineProtocol.SERVER_CLOSED);
        socket.close();
        interrupt();
    }

    public int getPort() {
        return port;
    }

    public synchronized ServerSnapshot getSnapshot() {
        List<PlayerSnapshot> players = new ArrayList<>();
        for (ClientInfo client : clients) {
            players.add(new PlayerSnapshot(client.id, client.name, client.ready, client.alive, client.score));
        }
        return new ServerSnapshot(roomState.protocolValue, lastWinnerId, players);
    }

    private synchronized void handlePacket(DatagramPacket packet) {
        String raw = new String(packet.getData(), 0, packet.getLength()).trim();
        if (raw.isEmpty()) {
            return;
        }

        String[] parts = raw.split(":", 3);
        String command = parts[0];
        ClientInfo sender = findByAddress(packet.getAddress(), packet.getPort());
        if (sender != null) {
            sender.lastSeenMs = System.currentTimeMillis();
        }

        if (OnlineProtocol.HELLO.equals(command)) {
            String playerName = parts.length > 1 ? sanitizeName(parts[1]) : "Player";
            handleHello(packet.getAddress(), packet.getPort(), playerName);
            return;
        }

        if (OnlineProtocol.PING.equals(command)) {
            send(OnlineProtocol.PONG, packet.getAddress(), packet.getPort());
            return;
        }

        if (sender == null) {
            send(OnlineProtocol.ERROR + ":" + OnlineProtocol.ERROR_INVALID_MSG,
                packet.getAddress(), packet.getPort());
            return;
        }

        if (OnlineProtocol.JUMP.equals(command)) {
            handleJump(sender);
            return;
        }

        if (OnlineProtocol.READY.equals(command)) {
            boolean ready = parts.length < 2 || "1".equals(parts[1]) || "true".equalsIgnoreCase(parts[1]);
            sender.ready = ready;
            broadcastRoomState();
            evaluateStartCondition();
            return;
        }

        if (OnlineProtocol.REMATCH.equals(command)) {
            if (roomState != RoomState.FINISHED) {
                return;
            }
            sender.rematchReady = true;
            broadcastRoomState();
            evaluateRematchCondition();
            return;
        }

        if (OnlineProtocol.LEAVE.equals(command)) {
            removeClient(sender.id, true);
        }
    }

    private void handleHello(InetAddress address, int port, String playerName) {
        ClientInfo existing = findByAddress(address, port);
        if (existing != null) {
            existing.name = playerName;
            existing.lastSeenMs = System.currentTimeMillis();
            send(OnlineProtocol.WELCOME + ":" + existing.id, existing.address, existing.port);
            broadcastRoomState();
            return;
        }

        if (clients.size() >= MAX_CLIENTS) {
            send(OnlineProtocol.ERROR + ":" + OnlineProtocol.ERROR_SERVER_FULL, address, port);
            return;
        }

        int assignedId = nextAvailableId();
        if (assignedId == -1) {
            send(OnlineProtocol.ERROR + ":" + OnlineProtocol.ERROR_SERVER_FULL, address, port);
            return;
        }

        ClientInfo client = new ClientInfo(assignedId, playerName, address, port);
        clients.add(client);
        send(OnlineProtocol.WELCOME + ":" + assignedId, address, port);
        evaluateWaitingState();
        broadcastRoomState();
    }

    private void handleJump(ClientInfo sender) {
        if (roomState != RoomState.PLAYING || System.currentTimeMillis() < matchStartsAtMs || !sender.alive) {
            return;
        }
        sender.velocity = JUMP_FORCE;
        broadcast(OnlineProtocol.JUMP + ":" + sender.id);
    }

    private void startMatch() {
        roomState = RoomState.PLAYING;
        lastWinnerId = 0;
        pipes.clear();
        long now = System.currentTimeMillis();
        matchStartsAtMs = now + START_DELAY_MS;
        lastSimulationTickMs = now;
        simulationAccumulatorSeconds = 0f;
        simulationTick = 0L;
        scheduleNextSpawn(matchStartsAtMs);

        for (ClientInfo client : clients) {
            client.alive = true;
            client.score = 0;
            client.y = START_Y;
            client.velocity = 0f;
            client.eliminatedTick = -1L;
            client.ready = false;
            client.rematchReady = false;
        }

        String startMsg = String.format(Locale.US, "%s:%.2f:%.2f:%d",
            OnlineProtocol.START_GAME, START_X, START_Y, START_DELAY_MS);
        broadcast(startMsg);
        broadcastRoomState();
    }

    private void evaluateStartCondition() {
        if (roomState != RoomState.WAITING) {
            return;
        }
        if (clients.size() != MAX_CLIENTS) {
            evaluateWaitingState();
            return;
        }
        for (ClientInfo client : clients) {
            if (!client.ready) {
                return;
            }
        }
        startMatch();
    }

    private void evaluateWaitingState() {
        if (roomState == RoomState.PLAYING) {
            return;
        }
        if (clients.size() < MAX_CLIENTS) {
            roomState = RoomState.WAITING;
        }
    }

    private void tickTimeouts(long nowMs) {
        synchronized (this) {
            Iterator<ClientInfo> iterator = clients.iterator();
            boolean changed = false;

            while (iterator.hasNext()) {
                ClientInfo client = iterator.next();
                if (nowMs - client.lastSeenMs >= CLIENT_TIMEOUT_MS) {
                    iterator.remove();
                    broadcast(OnlineProtocol.CLIENT_LEFT + ":" + client.id);
                    changed = true;
                }
            }

            if (changed) {
                onRoomChangedAfterDisconnect();
                broadcastRoomState();
            }
        }
    }

    private void tickMatch(long nowMs) {
        synchronized (this) {
            if (roomState != RoomState.PLAYING) {
                return;
            }
            if (nowMs < matchStartsAtMs) {
                lastSimulationTickMs = nowMs;
                return;
            }
            float frameDelta = (nowMs - lastSimulationTickMs) / 1000f;
            if (frameDelta <= 0f) {
                return;
            }
            if (frameDelta > MAX_FRAME_DELTA_SECONDS) {
                frameDelta = MAX_FRAME_DELTA_SECONDS;
            }
            lastSimulationTickMs = nowMs;
            simulationAccumulatorSeconds += frameDelta;

            boolean roomChanged = false;
            if (nowMs >= nextSpawnAtMs) {
                float gapY = randomFloat(GAP_MIN_CENTER, GAP_MAX_CENTER);
                pipes.add(new PipeState(WORLD_WIDTH, gapY));
                broadcast(OnlineProtocol.SPAWN + ":" + String.format(Locale.US, "%.2f", gapY));
                scheduleNextSpawn(nowMs);
                roomChanged = true;
            }

            while (simulationAccumulatorSeconds >= FIXED_STEP_SECONDS && roomState == RoomState.PLAYING) {
                simulationAccumulatorSeconds -= FIXED_STEP_SECONDS;
                simulationTick++;
                if (simulateStep(FIXED_STEP_SECONDS, simulationTick)) {
                    roomChanged = true;
                }
            }

            if (roomChanged) {
                broadcastRoomState();
            }
        }
    }

    private boolean simulateStep(float deltaSeconds, long tickId) {
        boolean roomChanged = false;

        for (ClientInfo client : clients) {
            if (!client.alive) {
                continue;
            }
            client.velocity -= GRAVITY * deltaSeconds;
            client.y += client.velocity * deltaSeconds;
            if (client.y > WORLD_HEIGHT - BIRD_HEIGHT) {
                client.y = WORLD_HEIGHT - BIRD_HEIGHT;
                client.velocity = 0f;
            }
        }

        for (int i = pipes.size() - 1; i >= 0; i--) {
            PipeState pipe = pipes.get(i);
            pipe.x -= PIPE_SPEED * deltaSeconds;
            if (pipe.x + PIPE_WIDTH < 0f) {
                pipes.remove(i);
            }
        }

        for (ClientInfo client : clients) {
            if (!client.alive) {
                continue;
            }
            if (client.y <= GROUND_HEIGHT) {
                eliminate(client.id, tickId);
                roomChanged = true;
                continue;
            }

            for (PipeState pipe : pipes) {
                if (!pipe.scoreCounted[client.id] && pipe.x + PIPE_WIDTH < START_X) {
                    pipe.scoreCounted[client.id] = true;
                    client.score++;
                    roomChanged = true;
                }

                if (START_X + BIRD_WIDTH >= pipe.x && START_X <= pipe.x + PIPE_WIDTH) {
                    float gapBottom = pipe.gapCenterY - PIPE_GAP_HEIGHT / 2f;
                    float gapTop = gapBottom + PIPE_GAP_HEIGHT;
                    if (client.y < gapBottom || client.y + BIRD_HEIGHT > gapTop) {
                        eliminate(client.id, tickId);
                        roomChanged = true;
                        break;
                    }
                }
            }
        }

        evaluateGameFinish();
        return roomChanged;
    }

    private void eliminate(int playerId, long tickId) {
        ClientInfo client = findById(playerId);
        if (client == null || !client.alive) {
            return;
        }
        client.alive = false;
        client.velocity = 0f;
        client.eliminatedTick = tickId;
        broadcast(OnlineProtocol.ELIMINATED + ":" + playerId);
    }

    private void evaluateGameFinish() {
        if (roomState != RoomState.PLAYING) {
            return;
        }
        if (System.currentTimeMillis() < matchStartsAtMs) {
            return;
        }

        if (clients.isEmpty()) {
            finishMatch(0);
            return;
        }

        List<ClientInfo> alivePlayers = new ArrayList<>(MAX_CLIENTS);
        List<ClientInfo> deadPlayers = new ArrayList<>(MAX_CLIENTS);
        for (ClientInfo client : clients) {
            if (client.alive) {
                alivePlayers.add(client);
            } else {
                deadPlayers.add(client);
            }
        }

        if (alivePlayers.size() > 1) {
            return;
        }

        if (alivePlayers.size() == 1) {
            finishMatch(alivePlayers.get(0).id);
            return;
        }

        if (deadPlayers.size() < 2) {
            finishMatch(0);
            return;
        }

        ClientInfo first = deadPlayers.get(0);
        ClientInfo second = deadPlayers.get(1);
        if (first.eliminatedTick == second.eliminatedTick) {
            finishMatch(0);
            return;
        }

        int winnerId = first.eliminatedTick < second.eliminatedTick ? second.id : first.id;
        finishMatch(winnerId);
    }

    private void finishMatch(int winnerId) {
        roomState = RoomState.FINISHED;
        lastWinnerId = winnerId;
        matchStartsAtMs = 0L;
        simulationAccumulatorSeconds = 0f;
        for (ClientInfo client : clients) {
            client.ready = false;
            client.rematchReady = false;
        }
        broadcast(OnlineProtocol.FIN + ":" + winnerId);
        broadcastRoomState();
    }

    private void evaluateRematchCondition() {
        if (roomState != RoomState.FINISHED || clients.size() != MAX_CLIENTS) {
            return;
        }
        for (ClientInfo client : clients) {
            if (!client.rematchReady) {
                return;
            }
        }
        startMatch();
    }

    private void scheduleNextSpawn(long nowMs) {
        long interval = (long) randomFloat(SPAWN_MIN_MS, SPAWN_MAX_MS);
        nextSpawnAtMs = nowMs + interval;
    }

    private void removeClient(int playerId, boolean notify) {
        ClientInfo removed = null;
        for (int i = 0; i < clients.size(); i++) {
            if (clients.get(i).id == playerId) {
                removed = clients.remove(i);
                break;
            }
        }

        if (removed == null) {
            return;
        }

        if (notify) {
            broadcast(OnlineProtocol.CLIENT_LEFT + ":" + removed.id);
        }
        onRoomChangedAfterDisconnect();
        broadcastRoomState();
    }

    private void onRoomChangedAfterDisconnect() {
        if (roomState == RoomState.PLAYING) {
            if (clients.size() == 1) {
                finishMatch(clients.get(0).id);
                return;
            }
            if (clients.isEmpty()) {
                finishMatch(0);
                return;
            }
        }

        if (clients.size() < MAX_CLIENTS) {
            roomState = RoomState.WAITING;
            matchStartsAtMs = 0L;
        }
    }

    private void broadcastRoomState() {
        StringJoiner joiner = new StringJoiner("|");
        for (ClientInfo client : clients) {
            joiner.add(client.id + "," + client.name + "," + (client.ready ? "1" : "0")
                + "," + client.score + "," + (client.alive ? "1" : "0"));
        }

        String payload = OnlineProtocol.ROOM + ":"
            + roomState.protocolValue + ":"
            + lastWinnerId + ":"
            + clients.size() + ":"
            + joiner;
        broadcast(payload);
    }

    private int nextAvailableId() {
        boolean has1 = false;
        boolean has2 = false;
        for (ClientInfo client : clients) {
            if (client.id == 1) {
                has1 = true;
            } else if (client.id == 2) {
                has2 = true;
            }
        }
        if (!has1) {
            return 1;
        }
        if (!has2) {
            return 2;
        }
        return -1;
    }

    private ClientInfo findByAddress(InetAddress address, int port) {
        for (ClientInfo client : clients) {
            if (client.port == port && client.address.equals(address)) {
                return client;
            }
        }
        return null;
    }

    private ClientInfo findById(int id) {
        for (ClientInfo client : clients) {
            if (client.id == id) {
                return client;
            }
        }
        return null;
    }

    private void send(String message, InetAddress address, int port) {
        try {
            byte[] data = message.getBytes();
            DatagramPacket packet = new DatagramPacket(data, data.length, address, port);
            socket.send(packet);
        } catch (IOException ignored) {
            // Cliente inaccesible.
        }
    }

    private void broadcast(String message) {
        for (ClientInfo client : clients) {
            send(message, client.address, client.port);
        }
    }

    private float randomFloat(float min, float max) {
        return min + random.nextFloat() * (max - min);
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

    public static final class PlayerSnapshot {
        public final int id;
        public final String name;
        public final boolean ready;
        public final boolean alive;
        public final int score;

        public PlayerSnapshot(int id, String name, boolean ready, boolean alive, int score) {
            this.id = id;
            this.name = name;
            this.ready = ready;
            this.alive = alive;
            this.score = score;
        }
    }

    public static final class ServerSnapshot {
        public final String roomState;
        public final int lastWinnerId;
        public final List<PlayerSnapshot> players;

        public ServerSnapshot(String roomState, int lastWinnerId, List<PlayerSnapshot> players) {
            this.roomState = roomState;
            this.lastWinnerId = lastWinnerId;
            this.players = Collections.unmodifiableList(new ArrayList<>(players));
        }
    }

    private enum RoomState {
        WAITING(OnlineProtocol.ROOM_WAITING),
        PLAYING(OnlineProtocol.ROOM_PLAYING),
        FINISHED(OnlineProtocol.ROOM_FINISHED);

        private final String protocolValue;

        RoomState(String protocolValue) {
            this.protocolValue = protocolValue;
        }
    }

    private static final class ClientInfo {
        private final int id;
        private String name;
        private final InetAddress address;
        private final int port;
        private long lastSeenMs;

        private boolean alive;
        private boolean ready;
        private boolean rematchReady;
        private int score;
        private float y;
        private float velocity;
        private long eliminatedTick;

        private ClientInfo(int id, String name, InetAddress address, int port) {
            this.id = id;
            this.name = name;
            this.address = address;
            this.port = port;
            this.lastSeenMs = System.currentTimeMillis();
            this.alive = true;
            this.ready = false;
            this.rematchReady = false;
            this.score = 0;
            this.y = START_Y;
            this.velocity = 0f;
            this.eliminatedTick = -1L;
        }
    }

    private static final class PipeState {
        private float x;
        private final float gapCenterY;
        private final boolean[] scoreCounted;

        private PipeState(float x, float gapCenterY) {
            this.x = x;
            this.gapCenterY = gapCenterY;
            this.scoreCounted = new boolean[] {false, false, false};
        }
    }
}
