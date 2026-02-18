package com.flappyboldo.game.online.net;

public final class OnlineProtocol {
    public static final int PORT = 5555;

    public static final String HELLO = "HELLO";
    public static final String WELCOME = "WELCOME";
    public static final String ROOM = "ROOM";
    public static final String READY = "READY";
    public static final String START_GAME = "START_GAME";
    public static final String JUMP = "JUMP";
    public static final String SPAWN = "SPAWN";
    public static final String REMATCH = "REMATCH";
    public static final String ELIMINATED = "ELIMINATED";
    public static final String FIN = "FIN";
    public static final String LEAVE = "LEAVE";
    public static final String CLIENT_LEFT = "CLIENT_LEFT";
    public static final String SERVER_CLOSED = "SERVER_CLOSED";
    public static final String ERROR = "ERROR";
    public static final String PING = "PING";
    public static final String PONG = "PONG";

    public static final String ROOM_WAITING = "WAITING";
    public static final String ROOM_PLAYING = "PLAYING";
    public static final String ROOM_FINISHED = "FINISHED";

    public static final String ERROR_SERVER_FULL = "SERVER_FULL";
    public static final String ERROR_INVALID_MSG = "INVALID_MSG";

    private OnlineProtocol() {
    }
}
