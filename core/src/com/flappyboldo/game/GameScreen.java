package com.flappyboldo.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.ImageButton;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Scaling;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.badlogic.gdx.utils.viewport.Viewport;

import java.util.Random;

public class GameScreen implements Screen {
    private static final int WORLD_HEIGHT = 200;
    private static final int WORLD_WIDTH = 100;
    private static final String PREFS_NAME = "flappy-bird-online";
    private static final String PREF_BEST_SCORE = "best-score";

    private enum PauseView {
        MENU,
        OPTIONS
    }

    private final float pipeSpaceWidth = 4f * WORLD_WIDTH / 6f;
    private final float pipeSpaceHeight = WORLD_HEIGHT / 3f;

    private final MyGdxGame game;

    private OrthographicCamera camera;
    private Viewport viewport;
    private SpriteBatch batch;
    private BitmapFont hudFont;
    private BitmapFont titleFont;
    private BitmapFont scoreFont;
    private BitmapFont promptFont;
    private GlyphLayout glyphLayout;
    private Preferences preferences;

    private Texture skyTexture;
    private Texture groundTexture;
    private Texture birdTexture;
    private Texture pipeHeadTexture1;
    private Texture pipeHeadTexture2;
    private Texture pipeBodyTexture;

    private Animation<TextureRegion> birdAnimation;

    private float birdX;
    private float birdY;
    private float birdWidth;
    private float birdHeight;
    private float birdVelocity;
    private float birdRotation;

    private float groundOffset;
    private float pipeTimer;
    private float animationTime;

    private Vector2[] pipes;
    private boolean[] scoreCounted;
    private Random random;

    private boolean notReady;
    private boolean gameOver;
    private boolean newBest;
    private boolean paused;
    private PauseView pauseView;

    private int currentScore;
    private int bestScore;

    private Stage pauseStage;
    private Skin pauseSkin;
    private InputMultiplexer inputMultiplexer;
    private Table pauseRoot;
    private Slider pauseVolumeSlider;
    private Label pauseVolumeValue;
    private Texture backButtonTexture;
    private Texture optionsButtonTexture;
    private Texture mainMenuButtonTexture;

    public GameScreen(MyGdxGame game) {
        this.game = game;
    }

    @Override
    public void show() {
        camera = new OrthographicCamera();
        viewport = new FitViewport(WORLD_WIDTH, WORLD_HEIGHT, camera);
        viewport.apply();
        camera.position.set(WORLD_WIDTH / 2f, WORLD_HEIGHT / 2f, 0);
        camera.update();

        batch = game.getBatch();
        hudFont = createCrispFont(0.72f);
        titleFont = createCrispFont(1.12f);
        scoreFont = createCrispFont(0.92f);
        promptFont = createCrispFont(0.56f);
        glyphLayout = new GlyphLayout();

        game.applyMusicState();

        preferences = Gdx.app.getPreferences(PREFS_NAME);
        bestScore = preferences.getInteger(PREF_BEST_SCORE, 0);

        skyTexture = new Texture("png/stage_sky.png");
        groundTexture = new Texture("png/stage_ground.png");
        birdTexture = new Texture(Gdx.files.internal("png/bird.png"));
        pipeHeadTexture1 = new Texture("png/pipe_head_1.png");
        pipeHeadTexture2 = new Texture("png/pipe_head_2.png");
        pipeBodyTexture = new Texture("png/pipe_body.png");

        Array<TextureRegion> birdRegions = new Array<>();
        for (int i = 0; i < 3; i++) {
            birdRegions.add(new TextureRegion(birdTexture, 0, (59 + 11) * i, birdTexture.getWidth(), 59));
        }
        birdAnimation = new Animation<>(1 / 14f, birdRegions, Animation.PlayMode.LOOP_REVERSED);

        random = new Random();
        pipes = new Vector2[4];
        scoreCounted = new boolean[4];

        createPauseUi();

        resetGame();
    }

    private void createPauseUi() {
        pauseStage = new Stage(new ScreenViewport(), batch);
        pauseSkin = UiSkinFactory.createDefaultSkin();
        backButtonTexture = new Texture("png/back.png");
        optionsButtonTexture = new Texture("png/options.png");
        mainMenuButtonTexture = new Texture("png/mainmenu.png");
        pauseRoot = new Table();
        pauseRoot.setFillParent(true);
        pauseStage.addActor(pauseRoot);

        inputMultiplexer = new InputMultiplexer();
        inputMultiplexer.addProcessor(pauseStage);

        setPauseView(PauseView.MENU);
        setPaused(false);
    }

    private void setPaused(boolean pausedState) {
        paused = pausedState;
        if (pauseRoot != null) {
            pauseRoot.setVisible(pausedState);
            pauseRoot.setTouchable(pausedState ? Touchable.enabled : Touchable.disabled);
        }
        Gdx.input.setInputProcessor(pausedState ? inputMultiplexer : null);
    }

    private void setPauseView(PauseView view) {
        pauseView = view;
        pauseRoot.clearChildren();
        pauseRoot.defaults().pad(10f);

        if (view == PauseView.MENU) {
            Label title = new Label("PAUSED", pauseSkin);
            ImageButton returnButton = new ImageButton(new TextureRegionDrawable(new TextureRegion(backButtonTexture)));
            ImageButton optionsButton = new ImageButton(new TextureRegionDrawable(new TextureRegion(optionsButtonTexture)));
            ImageButton mainMenuButton = new ImageButton(new TextureRegionDrawable(new TextureRegion(mainMenuButtonTexture)));
            returnButton.getImage().setScaling(Scaling.fit);
            optionsButton.getImage().setScaling(Scaling.fit);
            mainMenuButton.getImage().setScaling(Scaling.fit);

            returnButton.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                    setPaused(false);
                }
            });

            optionsButton.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                    setPauseView(PauseView.OPTIONS);
                }
            });

            mainMenuButton.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                    setPaused(false);
                    game.setScreen(new MainMenuScreen(game));
                }
            });

            pauseRoot.add(title).padBottom(20f).row();
            pauseRoot.add(returnButton).size(220f, 72f).row();
            pauseRoot.add(optionsButton).size(220f, 72f).row();
            pauseRoot.add(mainMenuButton).size(240f, 72f).row();
        } else {
            Label title = new Label("PAUSE OPTIONS", pauseSkin);
            Label volumeLabel = new Label("VOLUME", pauseSkin);
            pauseVolumeValue = new Label(String.format("%.2f", game.getMusicVolume()), pauseSkin);
            pauseVolumeSlider = new Slider(0f, 1f, 0.01f, false, pauseSkin);
            pauseVolumeSlider.setValue(game.getMusicVolume());
            final TextButton muteButton = new TextButton(game.isMuted ? "UNMUTE" : "MUTE", pauseSkin);
            TextButton backButton = new TextButton("BACK", pauseSkin);

            pauseVolumeSlider.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                    float value = pauseVolumeSlider.getValue();
                    game.setMusicVolume(value);
                    pauseVolumeValue.setText(String.format("%.2f", value));
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
                    setPauseView(PauseView.MENU);
                }
            });

            pauseRoot.add(title).colspan(2).padBottom(20f).row();
            pauseRoot.add(volumeLabel).left();
            pauseRoot.add(pauseVolumeValue).right().row();
            pauseRoot.add(pauseVolumeSlider).colspan(2).width(260f).row();
            pauseRoot.add(muteButton).colspan(2).width(220f).padTop(8f).row();
            pauseRoot.add(backButton).colspan(2).width(220f).padTop(16f);
        }
    }

    private void resetGame() {
        birdWidth = 0.15f * WORLD_WIDTH;
        birdHeight = WORLD_HEIGHT / 17f;
        birdX = 0.25f * WORLD_WIDTH;
        birdY = 0.5f * WORLD_HEIGHT;
        birdVelocity = 0;
        birdRotation = 0;

        pipes[0] = new Vector2(2f * WORLD_WIDTH, 0.5f * WORLD_HEIGHT);
        scoreCounted[0] = false;
        for (int i = 1; i < 4; i++) {
            pipes[i] = new Vector2(pipes[i - 1].x + pipeSpaceWidth, randomPipeY());
            scoreCounted[i] = false;
        }

        setPaused(false);
        setPauseView(PauseView.MENU);
        notReady = true;
        gameOver = false;
        newBest = false;
        currentScore = 0;
        groundOffset = 0;
        pipeTimer = 0;
        animationTime = 0;
    }

    private float randomPipeY() {
        return (random.nextFloat() * 0.4f + 0.2f) * WORLD_HEIGHT;
    }

    private boolean jumpPressed() {
        return Gdx.input.justTouched()
            || Gdx.input.isKeyJustPressed(Input.Keys.SPACE)
            || Gdx.input.isKeyJustPressed(Input.Keys.ENTER);
    }

    private void update(float delta) {
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE) && !gameOver) {
            if (paused) {
                if (pauseView == PauseView.OPTIONS) {
                    setPauseView(PauseView.MENU);
                } else {
                    setPaused(false);
                }
            } else {
                setPaused(true);
                setPauseView(PauseView.MENU);
            }
        }

        if (paused) {
            pauseStage.act(delta);
            return;
        }

        animationTime += delta;

        if (gameOver) {
            if (jumpPressed()) {
                resetGame();
            }
            return;
        }

        if (jumpPressed()) {
            notReady = false;
            birdVelocity = 130;
            birdRotation = 0;
        }

        if (!notReady) {
            pipeTimer += delta;
            while (0.005f < pipeTimer) {
                pipeTimer -= 0.005f;
                for (int i = 0; i < 4; i++) {
                    pipes[i].x -= 0.0025f * WORLD_WIDTH;

                    if (pipes[i].x < -WORLD_WIDTH / 6f) {
                        pipes[i].x = pipes[(i + 3) % 4].x + pipeSpaceWidth;
                        pipes[i].y = randomPipeY();
                        scoreCounted[i] = false;
                    }

                    if (!scoreCounted[i] && pipes[i].x < (birdX + birdWidth / 2f)) {
                        currentScore++;
                        scoreCounted[i] = true;
                    }
                }
            }

            birdVelocity -= 400 * delta;
            birdY += birdVelocity * delta;
            if (birdVelocity < 0) {
                birdRotation = -45;
            }

            checkCollision();

            groundOffset -= WORLD_WIDTH / 20f;
            if (groundOffset <= -WORLD_WIDTH / 20f) {
                groundOffset = 0;
            }
        }
    }

    private void checkCollision() {
        if (birdY >= WORLD_HEIGHT - birdHeight) {
            birdY = WORLD_HEIGHT - birdHeight;
            birdVelocity = 0;
        }

        if (0.15f * WORLD_HEIGHT > birdY) {
            handleGameOver();
            return;
        }

        for (Vector2 pipe : pipes) {
            if (birdX + birdWidth >= pipe.x && birdX < pipe.x + WORLD_WIDTH / 6f) {
                if (birdY < (pipe.y + WORLD_HEIGHT / 30f) || birdY + birdHeight > pipe.y + pipeSpaceHeight) {
                    handleGameOver();
                    return;
                }
            }
        }
    }

    private BitmapFont createCrispFont(float scale) {
        BitmapFont font = new BitmapFont();
        font.getData().setScale(scale);
        font.setUseIntegerPositions(true);
        for (TextureRegion region : font.getRegions()) {
            region.getTexture().setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        }
        return font;
    }

    private void handleGameOver() {
        gameOver = true;
        birdRotation = -90;
        birdY = 0.15f * WORLD_HEIGHT;

        if (currentScore > bestScore) {
            bestScore = currentScore;
            newBest = true;
            preferences.putInteger(PREF_BEST_SCORE, bestScore);
            preferences.flush();
        }
    }

    private void drawCenteredText(BitmapFont font, String text, float y, boolean bold) {
        glyphLayout.setText(font, text);
        float x = (WORLD_WIDTH - glyphLayout.width) / 2f;
        font.draw(batch, glyphLayout, x, y);
        if (bold) {
            font.draw(batch, glyphLayout, x + 0.2f, y);
        }
    }

    @Override
    public void render(float delta) {
        update(delta);

        Gdx.gl.glClearColor(0, 0, 0, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        viewport.apply();
        camera.update();
        batch.setProjectionMatrix(camera.combined);

        batch.begin();

        batch.draw(skyTexture, 0, 0.15f * WORLD_HEIGHT, WORLD_WIDTH, 0.85f * WORLD_HEIGHT);

        for (Vector2 pipe : pipes) {
            batch.draw(pipeHeadTexture2, pipe.x, pipe.y, WORLD_WIDTH / 6f, WORLD_HEIGHT / 30f);
            batch.draw(pipeBodyTexture, pipe.x + (WORLD_WIDTH / 200f), 0.15f * WORLD_HEIGHT, (WORLD_WIDTH / 6f) - (WORLD_WIDTH / 100f), pipe.y - 0.15f * WORLD_HEIGHT);
            batch.draw(pipeBodyTexture, pipe.x + (WORLD_WIDTH / 200f), pipe.y + pipeSpaceHeight + (WORLD_WIDTH / 30f), (WORLD_WIDTH / 6f) - (WORLD_WIDTH / 100f), WORLD_HEIGHT / 2f);
            batch.draw(pipeHeadTexture1, pipe.x, pipe.y + pipeSpaceHeight, WORLD_WIDTH / 6f, WORLD_HEIGHT / 30f);
        }

        TextureRegion birdFrame = birdAnimation.getKeyFrame(animationTime, true);
        batch.draw(birdFrame, birdX, birdY, birdWidth / 2f, birdHeight / 2f, birdWidth, birdHeight, 1f, 1f, birdRotation);

        for (int i = 0; i < 25; i++) {
            batch.draw(groundTexture, groundOffset + i * WORLD_WIDTH / 20f, 0, WORLD_WIDTH / 20f, 0.15f * WORLD_HEIGHT);
        }

        if (gameOver) {
            drawCenteredText(titleFont, "GAME OVER", 0.65f * WORLD_HEIGHT, true);
            drawCenteredText(scoreFont, "SCORE: " + currentScore, 0.55f * WORLD_HEIGHT, true);
            if (newBest) {
                drawCenteredText(scoreFont, "NEW BEST!", 0.46f * WORLD_HEIGHT, true);
            }
            drawCenteredText(promptFont, "SPACE/ENTER to restart", 0.32f * WORLD_HEIGHT, true);
        } else {
            drawCenteredText(scoreFont, "SCORE: " + currentScore, 0.92f * WORLD_HEIGHT, true);
            if (notReady) {
                drawCenteredText(hudFont, "TAP/SPACE", 0.7f * WORLD_HEIGHT, true);
            }
        }

        batch.end();

        if (paused) {
            pauseStage.getViewport().apply();
            pauseStage.draw();
        }
    }

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height, true);
        if (pauseStage != null) {
            pauseStage.getViewport().update(width, height, true);
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
        Gdx.input.setInputProcessor(null);
        dispose();
    }

    @Override
    public void dispose() {
        if (hudFont != null) {
            hudFont.dispose();
            hudFont = null;
        }
        if (titleFont != null) {
            titleFont.dispose();
            titleFont = null;
        }
        if (scoreFont != null) {
            scoreFont.dispose();
            scoreFont = null;
        }
        if (promptFont != null) {
            promptFont.dispose();
            promptFont = null;
        }
        if (skyTexture != null) {
            skyTexture.dispose();
            skyTexture = null;
        }
        if (groundTexture != null) {
            groundTexture.dispose();
            groundTexture = null;
        }
        if (birdTexture != null) {
            birdTexture.dispose();
            birdTexture = null;
        }
        if (pipeHeadTexture1 != null) {
            pipeHeadTexture1.dispose();
            pipeHeadTexture1 = null;
        }
        if (pipeHeadTexture2 != null) {
            pipeHeadTexture2.dispose();
            pipeHeadTexture2 = null;
        }
        if (pipeBodyTexture != null) {
            pipeBodyTexture.dispose();
            pipeBodyTexture = null;
        }
        if (pauseStage != null) {
            pauseStage.dispose();
            pauseStage = null;
        }
        if (pauseSkin != null) {
            pauseSkin.dispose();
            pauseSkin = null;
        }
        if (backButtonTexture != null) {
            backButtonTexture.dispose();
            backButtonTexture = null;
        }
        if (optionsButtonTexture != null) {
            optionsButtonTexture.dispose();
            optionsButtonTexture = null;
        }
        if (mainMenuButtonTexture != null) {
            mainMenuButtonTexture.dispose();
            mainMenuButtonTexture = null;
        }
    }
}
