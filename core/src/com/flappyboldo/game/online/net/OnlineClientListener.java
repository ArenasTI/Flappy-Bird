package com.flappyboldo.game.online.net;

import java.util.List;

public interface OnlineClientListener {
    default void onConnected(int playerId) {
    }

    default void onRoomUpdate(List<String> playerNames) {
    }

    default void onStartGame() {
    }

    default void onStartGame(float spawnX, float spawnY, long startDelayMs) {
        onStartGame();
    }

    default void onRemoteJump(int playerId) {
    }

    default void onSpawnPipe(float gapCenterY) {
    }

    default void onEliminated(int playerId) {
    }

    default void onGameFinished(int winnerId) {
    }

    default void onPlayerLeft(int playerId) {
    }

    default void onServerClosed(String reason) {
    }

    default void onError(String message) {
    }
}
