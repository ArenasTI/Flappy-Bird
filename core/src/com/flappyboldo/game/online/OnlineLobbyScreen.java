package com.flappyboldo.game.online;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Scaling;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.flappyboldo.game.MainMenuScreen;
import com.flappyboldo.game.MyGdxGame;
import com.flappyboldo.game.UiSkinFactory;
import com.flappyboldo.game.online.net.OnlineClientListener;
import com.flappyboldo.game.online.net.OnlineSession;

import java.util.ArrayList;
import java.util.List;

public class OnlineLobbyScreen implements Screen, OnlineClientListener {
    private final MyGdxGame game;
    private final OnlineSession session;
    private final List<String> players;

    private Stage stage;
    private Skin skin;
    private Texture backgroundTexture;

    private Label titleLabel;
    private Label roomIpLabel;
    private Label playerLabel;
    private Label statusLabel;
    private Label playersLabel;
    private TextButton readyButton;

    private boolean movingToGame;
    private boolean readyState;

    public OnlineLobbyScreen(MyGdxGame game, OnlineSession session) {
        this.game = game;
        this.session = session;
        this.players = new ArrayList<>();
        this.movingToGame = false;
        this.readyState = false;
    }

    @Override
    public void show() {
        stage = new Stage(new ScreenViewport(), game.getBatch());
        skin = UiSkinFactory.createDefaultSkin();
        backgroundTexture = new Texture("png/stage_sky.png");
        session.setListener(this);

        Image backgroundImage = new Image(backgroundTexture);
        backgroundImage.setFillParent(true);
        backgroundImage.setScaling(Scaling.fill);
        stage.addActor(backgroundImage);

        Table table = new Table();
        table.setFillParent(true);
        table.defaults().pad(8f);

        titleLabel = new Label("CONNECTED TO SERVER", skin);
        roomIpLabel = new Label("IP: " + session.getRoomIp() + ":" + session.getRoomPort(), skin);
        playerLabel = new Label("Name: " + session.getPlayerName(), skin);
        statusLabel = new Label("Waiting for players...", skin);
        playersLabel = new Label("Players connected: 0/2", skin);
        readyButton = new TextButton("READY", skin);
        readyButton.setDisabled(!session.isConnected());
        TextButton backButton = new TextButton("BACK", skin);

        readyButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                readyState = !readyState;
                session.sendReady(readyState);
                readyButton.setText(readyState ? "UNREADY" : "READY");
                statusLabel.setText(readyState ? "Ready. Waiting for the other player..." : "Not ready");
            }
        });

        backButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                leaveToMainMenu();
            }
        });

        table.add(titleLabel).row();
        table.add(roomIpLabel).row();
        table.add(playerLabel).row();
        table.add(statusLabel).padTop(10f).row();
        table.add(playersLabel).padTop(10f).row();
        table.add(readyButton).width(220f).height(64f).padTop(14f).row();
        table.add(backButton).width(220f).height(64f).padTop(18f);

        stage.addActor(table);
        Gdx.input.setInputProcessor(stage);
    }

    private void leaveToMainMenu() {
        session.setListener(null);
        session.close();
        game.setScreen(new MainMenuScreen(game));
    }

    @Override
    public void render(float delta) {
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            leaveToMainMenu();
            return;
        }

        Gdx.gl.glClearColor(0f, 0f, 0f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        stage.act(delta);
        stage.draw();
    }

    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }

    @Override
    public void pause() {
    }

    @Override
    public void resume() {
    }

    @Override
    public void hide() {
        Gdx.input.setInputProcessor(null);
        dispose();
    }

    @Override
    public void dispose() {
        if (!movingToGame) {
            session.setListener(null);
            session.close();
        }
        if (stage != null) {
            stage.dispose();
            stage = null;
        }
        if (skin != null) {
            skin.dispose();
            skin = null;
        }
        if (backgroundTexture != null) {
            backgroundTexture.dispose();
            backgroundTexture = null;
        }
    }

    @Override
    public void onConnected(int playerId) {
        statusLabel.setText("Connected as player " + playerId);
        readyButton.setDisabled(false);
    }

    @Override
    public void onRoomUpdate(List<String> playerNames) {
        players.clear();
        players.addAll(playerNames);
        StringBuilder builder = new StringBuilder();
        builder.append("Players connected: ").append(players.size()).append("/2");
        for (String player : players) {
            builder.append("\n").append(player);
        }
        playersLabel.setText(builder.toString());
        if (players.size() < 2) {
            statusLabel.setText("Waiting for second player...");
            return;
        }

        boolean allReady = true;
        for (String player : players) {
            if (!player.contains("[READY]")) {
                allReady = false;
                break;
            }
        }
        if (allReady) {
            statusLabel.setText("All ready. Starting...");
        } else if (readyState) {
            statusLabel.setText("Waiting for the other player's READY...");
        } else {
            statusLabel.setText("Press READY to start");
        }
    }

    @Override
    public void onStartGame(float spawnX, float spawnY, long startDelayMs) {
        movingToGame = true;
        session.setListener(null);
        game.setScreen(new OnlineGameScreen(
            game,
            session,
            spawnX,
            spawnY,
            startDelayMs,
            extractPlayerName(1),
            extractPlayerName(2)
        ));
    }

    @Override
    public void onServerClosed(String reason) {
        statusLabel.setText(reason);
    }

    @Override
    public void onError(String message) {
        statusLabel.setText(message);
    }

    private String extractPlayerName(int playerId) {
        String prefix = "P" + playerId + " - ";
        for (String player : players) {
            if (player != null && player.startsWith(prefix)) {
                String rest = player.substring(prefix.length());
                int readyTagIndex = rest.indexOf(" [");
                if (readyTagIndex > -1) {
                    return rest.substring(0, readyTagIndex).trim();
                }
                return rest.trim();
            }
        }
        return "Player" + playerId;
    }
}
