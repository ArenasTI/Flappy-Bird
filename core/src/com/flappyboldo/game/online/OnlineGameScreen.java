package com.flappyboldo.game.online;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.ImageButton;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.Scaling;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.flappyboldo.game.MainMenuScreen;
import com.flappyboldo.game.MyGdxGame;
import com.flappyboldo.game.UiSkinFactory;
import com.flappyboldo.game.online.net.OnlineClientListener;
import com.flappyboldo.game.online.net.OnlineSession;

import java.util.ArrayList;
import java.util.List;

public class OnlineGameScreen implements Screen, OnlineClientListener {
    private static final int WORLD_HEIGHT = 200;
    private static final int WORLD_WIDTH = 100;

    private static final float GROUND_HEIGHT = 0.15f * WORLD_HEIGHT;
    private static final float PIPE_WIDTH = WORLD_WIDTH / 6f;
    private static final float PIPE_GAP_HEIGHT = WORLD_HEIGHT / 3f;
    private static final float PIPE_SPEED = 50f;

    private static final float BIRD_WIDTH = 0.15f * WORLD_WIDTH;
    private static final float BIRD_HEIGHT = WORLD_HEIGHT / 17f;
    private static final float GRAVITY = 400f;
    private static final float JUMP_FORCE = 130f;

    private enum FinalView {
        MENU,
        OPTIONS
    }

    private final MyGdxGame game;
    private final OnlineSession session;
    private final float spawnX;
    private final float spawnY;
    private final long startDelayMs;
    private final String playerOneName;
    private final String playerTwoName;

    private OrthographicCamera camera;
    private Viewport viewport;

    private BitmapFont hudFont;
    private BitmapFont titleFont;
    private GlyphLayout glyphLayout;

    private Texture skyTexture;
    private Texture groundTexture;
    private Texture birdTexture;
    private Texture opponentBirdTexture;
    private Texture pipeHeadTexture1;
    private Texture pipeHeadTexture2;
    private Texture pipeBodyTexture;
    private Texture winTexture;
    private Texture lostTexture;
    private Texture drawTexture;
    private Texture optionsButtonTexture;
    private Texture rematchButtonTexture;
    private Texture mainMenuButtonTexture;

    private Animation<TextureRegion> localBirdAnimation;
    private Animation<TextureRegion> opponentBirdAnimation;
    private float animationTime;
    private float groundOffset;

    private final BirdState[] birds;
    private final float[] birdX;
    private final int[] serverScores;
    private final List<PipeState> pipes;

    private int localPlayerId;
    private boolean gameStarted;
    private long gameStartAtMs;
    private boolean matchFinished;
    private boolean rematchRequested;
    private boolean showWinSprite;
    private boolean showLostSprite;
    private boolean showDrawSprite;
    private boolean opponentDisconnected;
    private String matchMessage;
    private String errorMessage;
    private String rematchStatus;
    private boolean closing;
    private boolean finalUiActive;

    private Stage finalStage;
    private Skin finalSkin;
    private Table finalRoot;
    private Label finalStatusLabel;
    private ImageButton rematchButton;
    private FinalView finalView;

    public OnlineGameScreen(
        MyGdxGame game,
        OnlineSession session,
        float spawnX,
        float spawnY,
        long startDelayMs,
        String playerOneName,
        String playerTwoName
    ) {
        this.game = game;
        this.session = session;
        this.spawnX = spawnX;
        this.spawnY = spawnY;
        this.startDelayMs = startDelayMs;
        this.playerOneName = playerOneName == null || playerOneName.trim().isEmpty() ? "Player1" : playerOneName;
        this.playerTwoName = playerTwoName == null || playerTwoName.trim().isEmpty() ? "Player2" : playerTwoName;
        this.birds = new BirdState[] {null, new BirdState(), new BirdState()};
        this.birdX = new float[] {0f, spawnX, spawnX};
        this.serverScores = new int[] {0, 0, 0};
        this.pipes = new ArrayList<>();
        this.localPlayerId = 0;
        this.gameStarted = false;
        this.gameStartAtMs = 0L;
        this.matchFinished = false;
        this.rematchRequested = false;
        this.showWinSprite = false;
        this.showLostSprite = false;
        this.showDrawSprite = false;
        this.opponentDisconnected = false;
        this.matchMessage = "";
        this.errorMessage = "";
        this.rematchStatus = "";
        this.closing = false;
        this.finalUiActive = false;
        this.finalView = FinalView.MENU;
    }

    @Override
    public void show() {
        camera = new OrthographicCamera();
        viewport = new FitViewport(WORLD_WIDTH, WORLD_HEIGHT, camera);
        viewport.apply();
        camera.position.set(WORLD_WIDTH / 2f, WORLD_HEIGHT / 2f, 0f);
        camera.update();

        hudFont = createFont(0.62f);
        titleFont = createFont(1.05f);
        glyphLayout = new GlyphLayout();

        skyTexture = new Texture("png/stage_sky.png");
        groundTexture = new Texture("png/stage_ground.png");
        birdTexture = new Texture("png/bird.png");
        opponentBirdTexture = new Texture("png/birdtwo.png");
        pipeHeadTexture1 = new Texture("png/pipe_head_1.png");
        pipeHeadTexture2 = new Texture("png/pipe_head_2.png");
        pipeBodyTexture = new Texture("png/pipe_body.png");
        winTexture = new Texture("png/win.png");
        lostTexture = new Texture("png/lost.png");
        drawTexture = new Texture("png/draw.png");
        optionsButtonTexture = new Texture("png/options.png");
        rematchButtonTexture = new Texture("png/rematch.png");
        mainMenuButtonTexture = new Texture("png/mainmenu.png");

        localBirdAnimation = createBirdAnimation(birdTexture);
        opponentBirdAnimation = createBirdAnimation(opponentBirdTexture);

        resetBird(1, spawnY);
        resetBird(2, spawnY);
        birdX[1] = spawnX;
        birdX[2] = spawnX;
        gameStartAtMs = System.currentTimeMillis() + Math.max(0L, startDelayMs);
        gameStarted = false;

        localPlayerId = session.getLocalPlayerId();
        session.setListener(this);
        createFinalUi();
        setFinalUiActive(false);
    }

    private BitmapFont createFont(float scale) {
        BitmapFont font = new BitmapFont();
        font.getData().setScale(scale);
        for (TextureRegion region : font.getRegions()) {
            region.getTexture().setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        }
        return font;
    }

    private Animation<TextureRegion> createBirdAnimation(Texture texture) {
        Array<TextureRegion> birdRegions = new Array<>();
        for (int i = 0; i < 3; i++) {
            birdRegions.add(new TextureRegion(texture, 0, (59 + 11) * i, texture.getWidth(), 59));
        }
        return new Animation<>(1f / 14f, birdRegions, Animation.PlayMode.LOOP_REVERSED);
    }

    private void resetBird(int playerId, float initialY) {
        BirdState bird = birds[playerId];
        bird.y = initialY;
        bird.velocity = 0f;
        bird.rotation = 0f;
        bird.alive = true;
    }

    private void update(float delta) {
        if (!matchFinished && Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            closeToMenu(false);
            return;
        }

        if (localPlayerId == 0) {
            localPlayerId = session.getLocalPlayerId();
        }

        animationTime += delta;

        if (matchFinished) {
            if (opponentDisconnected && Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
                closeToMenu(true);
                return;
            }
            if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE) && finalView == FinalView.OPTIONS) {
                setFinalView(FinalView.MENU);
            }
            if (finalUiActive && finalStage != null) {
                finalStage.act(delta);
            }
            return;
        }

        if (!gameStarted) {
            if (System.currentTimeMillis() >= gameStartAtMs) {
                gameStarted = true;
            } else {
                return;
            }
        }

        if (jumpPressed() && localPlayerId > 0) {
            BirdState local = birds[localPlayerId];
            if (local != null && local.alive) {
                jump(localPlayerId, true);
            }
        }

        for (int playerId = 1; playerId <= 2; playerId++) {
            BirdState bird = birds[playerId];
            if (!bird.alive) {
                continue;
            }
            bird.velocity -= GRAVITY * delta;
            bird.y += bird.velocity * delta;
            if (bird.velocity < 0f) {
                bird.rotation = -45f;
            }

            if (bird.y > WORLD_HEIGHT - BIRD_HEIGHT) {
                bird.y = WORLD_HEIGHT - BIRD_HEIGHT;
                bird.velocity = 0f;
            }
        }

        for (int i = pipes.size() - 1; i >= 0; i--) {
            PipeState pipe = pipes.get(i);
            pipe.x -= PIPE_SPEED * delta;
            if (pipe.x + PIPE_WIDTH < 0f) {
                pipes.remove(i);
            }
        }

        groundOffset -= PIPE_SPEED * 0.35f * delta;
        if (groundOffset <= -WORLD_WIDTH / 20f) {
            groundOffset = 0f;
        }
    }

    private boolean jumpPressed() {
        return Gdx.input.justTouched()
            || Gdx.input.isKeyJustPressed(Input.Keys.SPACE)
            || Gdx.input.isKeyJustPressed(Input.Keys.ENTER);
    }

    private void jump(int playerId, boolean sendToServer) {
        BirdState bird = birds[playerId];
        if (bird == null || !bird.alive) {
            return;
        }
        bird.velocity = JUMP_FORCE;
        bird.rotation = 0f;
        if (sendToServer) {
            session.sendJump(playerId);
        }
    }

    private void markEliminated(int playerId) {
        BirdState bird = birds[playerId];
        if (bird == null || !bird.alive) {
            return;
        }
        bird.alive = false;
        bird.rotation = -90f;
        bird.velocity = 0f;
    }

    @Override
    public void render(float delta) {
        update(delta);

        Gdx.gl.glClearColor(0f, 0f, 0f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        viewport.apply();
        camera.update();
        game.getBatch().setProjectionMatrix(camera.combined);
        game.getBatch().begin();

        game.getBatch().draw(skyTexture, 0f, GROUND_HEIGHT, WORLD_WIDTH, WORLD_HEIGHT - GROUND_HEIGHT);
        drawPipes();
        drawBirds();

        for (int i = 0; i < 25; i++) {
            game.getBatch().draw(groundTexture, groundOffset + i * WORLD_WIDTH / 20f,
                0f, WORLD_WIDTH / 20f, GROUND_HEIGHT);
        }

        hudFont.draw(game.getBatch(), playerOneName + ": " + serverScores[1], 2f, 194f);
        hudFont.draw(game.getBatch(), playerTwoName + ": " + serverScores[2], 2f, 184f);
        if (!matchFinished && !gameStarted) {
            drawCentered(hudFont, "Starting match...", 130f);
        }

        if (matchFinished) {
            Texture resultTexture = getResultTexture();
            if (resultTexture != null) {
                drawResultSprite(resultTexture, 158f);
            } else {
                drawCentered(titleFont, matchMessage.isEmpty() ? "Match finished" : matchMessage, 130f);
            }
            if (opponentDisconnected) {
                drawCentered(titleFont, "Your opponent", 112f);
                drawCentered(titleFont, "disconnected", 98f);
                drawCentered(hudFont, "ESC to return", 18f);
            } else if (errorMessage != null && !errorMessage.isEmpty()) {
                drawCentered(hudFont, errorMessage, 112f);
            }
        }

        game.getBatch().end();

        if (matchFinished && finalUiActive && finalStage != null) {
            finalStage.getViewport().apply();
            finalStage.draw();
        }
    }

    private void drawBirds() {
        int localId = localPlayerId == 2 ? 2 : 1;
        int opponentId = localId == 1 ? 2 : 1;
        TextureRegion localFrame = localBirdAnimation.getKeyFrame(animationTime, true);
        TextureRegion opponentFrame = opponentBirdAnimation.getKeyFrame(animationTime, true);

        game.getBatch().setColor(Color.WHITE);
        drawBird(opponentId, opponentFrame);
        drawBird(localId, localFrame);
    }

    private void drawBird(int playerId, TextureRegion frame) {
        BirdState bird = birds[playerId];
        if (bird == null || frame == null || (!bird.alive && !matchFinished)) {
            return;
        }
        game.getBatch().draw(frame, birdX[playerId], bird.y,
            BIRD_WIDTH / 2f, BIRD_HEIGHT / 2f, BIRD_WIDTH, BIRD_HEIGHT, 1f, 1f, bird.rotation);
    }

    private void drawPipes() {
        for (PipeState pipe : pipes) {
            float gapBottom = pipe.gapCenterY - PIPE_GAP_HEIGHT / 2f;
            float topBodyY = gapBottom + PIPE_GAP_HEIGHT + (WORLD_HEIGHT / 30f);

            game.getBatch().draw(pipeHeadTexture2, pipe.x, gapBottom, PIPE_WIDTH, WORLD_HEIGHT / 30f);
            game.getBatch().draw(pipeBodyTexture, pipe.x + (WORLD_WIDTH / 200f), GROUND_HEIGHT,
                PIPE_WIDTH - (WORLD_WIDTH / 100f), gapBottom - GROUND_HEIGHT);
            game.getBatch().draw(pipeBodyTexture, pipe.x + (WORLD_WIDTH / 200f), topBodyY,
                PIPE_WIDTH - (WORLD_WIDTH / 100f), WORLD_HEIGHT - topBodyY);
            game.getBatch().draw(pipeHeadTexture1, pipe.x, gapBottom + PIPE_GAP_HEIGHT,
                PIPE_WIDTH, WORLD_HEIGHT / 30f);
        }
    }

    private void drawCentered(BitmapFont font, String text, float y) {
        glyphLayout.setText(font, text);
        float x = (WORLD_WIDTH - glyphLayout.width) / 2f;
        font.draw(game.getBatch(), glyphLayout, x, y);
    }

    private void drawResultSprite(Texture texture, float centerY) {
        if (texture == null) {
            return;
        }
        float maxWidth = WORLD_WIDTH * 0.62f;
        float maxHeight = WORLD_HEIGHT * 0.22f;
        float scale = Math.min(maxWidth / texture.getWidth(), maxHeight / texture.getHeight());
        float width = texture.getWidth() * scale;
        float height = texture.getHeight() * scale;
        float x = (WORLD_WIDTH - width) / 2f;
        float y = centerY - (height / 2f);
        game.getBatch().draw(texture, x, y, width, height);
    }

    private Texture getResultTexture() {
        if (showWinSprite) {
            return winTexture;
        }
        if (showLostSprite) {
            return lostTexture;
        }
        if (showDrawSprite) {
            return drawTexture;
        }
        return null;
    }

    private void closeToMenu(boolean deferred) {
        if (closing) {
            return;
        }
        closing = true;
        session.setListener(null);
        session.close();
        Runnable changeScreen = new Runnable() {
            @Override
            public void run() {
                game.setScreen(new MainMenuScreen(game));
            }
        };
        if (deferred) {
            Gdx.app.postRunnable(changeScreen);
        } else {
            changeScreen.run();
        }
    }

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height, true);
        if (finalStage != null) {
            finalStage.getViewport().update(width, height, true);
        }
    }

    @Override
    public void pause() {
    }

    @Override
    public void resume() {
    }

    @Override
    public void hide() {
        dispose();
    }

    @Override
    public void dispose() {
        if (!closing) {
            session.setListener(null);
            session.close();
            closing = true;
        }

        hudFont = disposeAndNull(hudFont);
        titleFont = disposeAndNull(titleFont);
        skyTexture = disposeAndNull(skyTexture);
        groundTexture = disposeAndNull(groundTexture);
        birdTexture = disposeAndNull(birdTexture);
        opponentBirdTexture = disposeAndNull(opponentBirdTexture);
        pipeHeadTexture1 = disposeAndNull(pipeHeadTexture1);
        pipeHeadTexture2 = disposeAndNull(pipeHeadTexture2);
        pipeBodyTexture = disposeAndNull(pipeBodyTexture);
        winTexture = disposeAndNull(winTexture);
        lostTexture = disposeAndNull(lostTexture);
        drawTexture = disposeAndNull(drawTexture);
        optionsButtonTexture = disposeAndNull(optionsButtonTexture);
        mainMenuButtonTexture = disposeAndNull(mainMenuButtonTexture);
        rematchButtonTexture = disposeAndNull(rematchButtonTexture);
        if (Gdx.input.getInputProcessor() == finalStage) {
            Gdx.input.setInputProcessor(null);
        }
        finalStage = disposeAndNull(finalStage);
        finalSkin = disposeAndNull(finalSkin);
    }

    @Override
    public void onConnected(int playerId) {
        localPlayerId = playerId;
    }

    @Override
    public void onRoomUpdate(List<String> playerNames) {
        if (playerNames == null) {
            return;
        }
        for (String player : playerNames) {
            if (player == null || !player.startsWith("P")) {
                continue;
            }
            int dash = player.indexOf(" - ");
            if (dash < 2) {
                continue;
            }
            int playerId;
            try {
                playerId = Integer.parseInt(player.substring(1, dash).trim());
            } catch (Exception ignored) {
                continue;
            }
            int scoreTag = player.indexOf("[S:");
            if (scoreTag < 0) {
                continue;
            }
            int scoreEnd = player.indexOf(']', scoreTag);
            if (scoreEnd <= scoreTag + 3) {
                continue;
            }
            try {
                int score = Integer.parseInt(player.substring(scoreTag + 3, scoreEnd).trim());
                if (playerId >= 1 && playerId <= 2) {
                    serverScores[playerId] = score;
                }
            } catch (Exception ignored) {
                // Ignore malformed score tags.
            }
        }
    }

    @Override
    public void onStartGame(float startX, float startY, long delayMs) {
        birdX[1] = startX;
        birdX[2] = startX;
        resetBird(1, startY);
        resetBird(2, startY);
        pipes.clear();
        groundOffset = 0f;
        animationTime = 0f;
        matchFinished = false;
        resetFinalState();
        errorMessage = "";
        matchMessage = "";
        serverScores[1] = 0;
        serverScores[2] = 0;
        if (rematchButton != null) {
            rematchButton.setDisabled(false);
        }
        if (finalStatusLabel != null) {
            finalStatusLabel.setText("");
        }
        setFinalUiActive(false);
        gameStartAtMs = System.currentTimeMillis() + Math.max(0L, delayMs);
        gameStarted = false;
    }

    @Override
    public void onRemoteJump(int playerId) {
        if (matchFinished || !gameStarted) {
            return;
        }
        if (playerId != localPlayerId) {
            jump(playerId, false);
        }
    }

    @Override
    public void onSpawnPipe(float gapCenterY) {
        if (matchFinished) {
            return;
        }
        pipes.add(new PipeState(WORLD_WIDTH, gapCenterY));
    }

    @Override
    public void onEliminated(int playerId) {
        if (matchFinished) {
            return;
        }
        markEliminated(playerId);
    }

    @Override
    public void onGameFinished(int winnerId) {
        if (opponentDisconnected) {
            matchFinished = true;
            gameStarted = false;
            showWinSprite = true;
            showLostSprite = false;
            showDrawSprite = false;
            setFinalUiActive(false);
            return;
        }
        matchFinished = true;
        gameStarted = false;
        resetFinalState();
        if (winnerId == 0) {
            matchMessage = "";
            showDrawSprite = true;
        } else if (winnerId == localPlayerId) {
            matchMessage = "";
            showWinSprite = true;
        } else {
            matchMessage = "";
            showLostSprite = true;
        }
        setFinalUiActive(true);
    }

    @Override
    public void onPlayerLeft(int playerId) {
        if (playerId == localPlayerId || closing) {
            return;
        }
        matchFinished = true;
        gameStarted = false;
        resetFinalState();
        showWinSprite = true;
        opponentDisconnected = true;
        matchMessage = "";
        errorMessage = "";
        setFinalUiActive(false);
    }

    @Override
    public void onServerClosed(String reason) {
        if (opponentDisconnected) {
            return;
        }
        matchFinished = true;
        gameStarted = false;
        resetFinalState();
        matchMessage = "Match closed";
        errorMessage = reason == null ? "" : reason;
        setFinalUiActive(true);
    }

    @Override
    public void onError(String message) {
        if (opponentDisconnected) {
            return;
        }
        matchFinished = true;
        gameStarted = false;
        resetFinalState();
        matchMessage = "Connection error";
        errorMessage = message == null ? "" : message;
        setFinalUiActive(true);
    }

    private void createFinalUi() {
        finalStage = new Stage(new ScreenViewport(), game.getBatch());
        finalSkin = UiSkinFactory.createDefaultSkin();
        finalRoot = new Table();
        finalRoot.setFillParent(true);
        finalRoot.defaults().pad(8f);
        finalStage.addActor(finalRoot);
        setFinalView(FinalView.MENU);
    }

    private void setFinalView(FinalView view) {
        finalView = view;
        if (finalRoot == null) {
            return;
        }
        finalRoot.clearChildren();

        if (view == FinalView.MENU) {
            ImageButton optionsButton = createSpriteButton(optionsButtonTexture);
            rematchButton = createSpriteButton(rematchButtonTexture);
            ImageButton mainMenuButton = createSpriteButton(mainMenuButtonTexture);
            finalStatusLabel = new Label(rematchStatus == null ? "" : rematchStatus, finalSkin);
            optionsButton.getImage().setScaling(Scaling.fit);
            rematchButton.getImage().setScaling(Scaling.fit);
            mainMenuButton.getImage().setScaling(Scaling.fit);

            optionsButton.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                    setFinalView(FinalView.OPTIONS);
                }
            });

            rematchButton.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                    if (rematchRequested) {
                        return;
                    }
                    rematchRequested = true;
                    rematchStatus = "Waiting for opponent...";
                    finalStatusLabel.setText(rematchStatus);
                    rematchButton.setDisabled(true);
                    session.sendRematch();
                }
            });

            mainMenuButton.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                    closeToMenu(false);
                }
            });

            if (rematchRequested) {
                rematchButton.setDisabled(true);
            }

            finalRoot.add(optionsButton).width(220f).height(70f).row();
            finalRoot.add(rematchButton).width(220f).height(70f).row();
            finalRoot.add(mainMenuButton).width(220f).height(70f).row();
            finalRoot.add(finalStatusLabel).padTop(10f).row();
            return;
        }

        Label title = new Label("OPTIONS", finalSkin);
        Label volumeLabel = new Label("VOLUME", finalSkin);
        final Label volumeValue = new Label(String.format("%.2f", game.getMusicVolume()), finalSkin);
        final Slider volumeSlider = new Slider(0f, 1f, 0.01f, false, finalSkin);
        volumeSlider.setValue(game.getMusicVolume());
        final TextButton muteButton = new TextButton(game.isMuted ? "UNMUTE" : "MUTE", finalSkin);
        TextButton backButton = new TextButton("BACK", finalSkin);

        volumeSlider.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                float value = volumeSlider.getValue();
                game.setMusicVolume(value);
                volumeValue.setText(String.format("%.2f", value));
            }
        });

        muteButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                game.toggleMute();
                muteButton.setText(game.isMuted ? "UNMUTE" : "MUTE");
            }
        });

        backButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                setFinalView(FinalView.MENU);
            }
        });

        finalRoot.add(title).colspan(2).padBottom(18f).row();
        finalRoot.add(volumeLabel).left();
        finalRoot.add(volumeValue).right().row();
        finalRoot.add(volumeSlider).colspan(2).width(260f).row();
        finalRoot.add(muteButton).colspan(2).width(220f).padTop(8f).row();
        finalRoot.add(backButton).colspan(2).width(220f).padTop(12f).row();
    }

    private void setFinalUiActive(boolean active) {
        finalUiActive = active;
        if (active) {
            setFinalView(FinalView.MENU);
            Gdx.input.setInputProcessor(finalStage);
            return;
        }
        if (Gdx.input.getInputProcessor() == finalStage) {
            Gdx.input.setInputProcessor(null);
        }
    }

    private ImageButton createSpriteButton(Texture texture) {
        TextureRegionDrawable drawable = new TextureRegionDrawable(new TextureRegion(texture));
        ImageButton.ImageButtonStyle style = new ImageButton.ImageButtonStyle();
        style.imageUp = drawable;
        style.imageDown = drawable;
        return new ImageButton(style);
    }

    private void resetFinalState() {
        rematchRequested = false;
        rematchStatus = "";
        showWinSprite = false;
        showLostSprite = false;
        showDrawSprite = false;
        opponentDisconnected = false;
    }

    private static <T extends Disposable> T disposeAndNull(T disposable) {
        if (disposable != null) {
            disposable.dispose();
        }
        return null;
    }

    private static final class BirdState {
        private float y;
        private float velocity;
        private float rotation;
        private boolean alive;
    }

    private static final class PipeState {
        private float x;
        private final float gapCenterY;

        private PipeState(float x, float gapCenterY) {
            this.x = x;
            this.gapCenterY = gapCenterY;
        }
    }
}
