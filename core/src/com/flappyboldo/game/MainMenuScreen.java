package com.flappyboldo.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.ImageButton;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Scaling;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.flappyboldo.game.online.OnlineModeMenuScreen;

public class MainMenuScreen implements Screen {
    private final MyGdxGame game;
    private Stage stage;
    private Skin skin;

    private Texture backgroundTexture;
    private Texture titleTexture;
    private Texture playTexture;
    private Texture onlineTexture;
    private Texture optionsTexture;

    private Table root;
    private Image titleImage;
    private ImageButton playButton;
    private ImageButton onlineButton;
    private ImageButton optionsButton;
    private Label onlineStatus;

    public MainMenuScreen(MyGdxGame game) {
        this.game = game;
    }

    @Override
    public void show() {
        game.applyMusicState();

        stage = new Stage(new ScreenViewport(), game.getBatch());
        skin = UiSkinFactory.createDefaultSkin();

        backgroundTexture = new Texture("png/stage_sky.png");
        titleTexture = new Texture("png/title.png");
        playTexture = new Texture("png/playbuttom.png");
        onlineTexture = new Texture("png/online.png");
        optionsTexture = new Texture("png/options.png");

        Image background = new Image(backgroundTexture);
        background.setFillParent(true);
        background.setScaling(Scaling.fill);
        stage.addActor(background);

        root = new Table();
        root.setFillParent(true);
        root.center();
        root.defaults().center();

        titleImage = new Image(titleTexture);
        titleImage.setScaling(Scaling.fit);

        playButton = createMenuButton(playTexture);
        onlineButton = createMenuButton(onlineTexture);
        optionsButton = createMenuButton(optionsTexture);
        onlineStatus = new Label("", skin);

        playButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                switchToGameScreen();
            }
        });

        onlineButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                switchToOnlineMenu();
            }
        });

        optionsButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                switchToOptionsScreen();
            }
        });

        updateMenuLayout();
        stage.addActor(root);
        Gdx.input.setInputProcessor(stage);
    }


    private void switchToGameScreen() {
        Gdx.app.postRunnable(new Runnable() {
            @Override
            public void run() {
                game.setScreen(new GameScreen(game));
            }
        });
    }

    private void switchToOptionsScreen() {
        Gdx.app.postRunnable(new Runnable() {
            @Override
            public void run() {
                game.setScreen(new OptionsScreen(game, new Runnable() {
                    @Override
                    public void run() {
                        game.setScreen(new MainMenuScreen(game));
                    }
                }));
            }
        });
    }

    private void switchToOnlineMenu() {
        Gdx.app.postRunnable(new Runnable() {
            @Override
            public void run() {
                game.setScreen(new OnlineModeMenuScreen(game));
            }
        });
    }

    private void updateMenuLayout() {
        float viewportWidth = stage.getViewport().getWorldWidth();
        float viewportHeight = stage.getViewport().getWorldHeight();

        float[] titleSize = fitSize(titleTexture, viewportWidth * 0.72f, viewportHeight * 0.22f);
        float[] playSize = fitSize(playTexture, viewportWidth * 0.48f, viewportHeight * 0.11f);
        float[] onlineSize = fitSize(onlineTexture, viewportWidth * 0.48f, viewportHeight * 0.11f);
        float[] optionsSize = fitSize(optionsTexture, viewportWidth * 0.48f, viewportHeight * 0.11f);

        titleImage.setSize(titleSize[0], titleSize[1]);
        playButton.setSize(playSize[0], playSize[1]);
        onlineButton.setSize(onlineSize[0], onlineSize[1]);
        optionsButton.setSize(optionsSize[0], optionsSize[1]);

        float titleBottomPad = Math.max(10f, viewportHeight * 0.03f);
        float buttonPad = Math.max(8f, viewportHeight * 0.018f);

        root.clearChildren();
        root.add(titleImage).size(titleSize[0], titleSize[1]).padBottom(titleBottomPad).row();
        root.add(playButton).size(playSize[0], playSize[1]).padBottom(buttonPad).row();
        root.add(onlineButton).size(onlineSize[0], onlineSize[1]).padBottom(buttonPad).row();
        root.add(optionsButton).size(optionsSize[0], optionsSize[1]).row();
        root.add(onlineStatus).padTop(Math.max(6f, viewportHeight * 0.012f)).row();
    }

    private float[] fitSize(Texture texture, float maxWidth, float maxHeight) {
        float scale = Math.min(maxWidth / texture.getWidth(), maxHeight / texture.getHeight());
        scale = Math.min(scale, 1f);
        return new float[] {texture.getWidth() * scale, texture.getHeight() * scale};
    }

    private ImageButton createMenuButton(Texture texture) {
        TextureRegionDrawable drawable = new TextureRegionDrawable(new TextureRegion(texture));
        ImageButton.ImageButtonStyle style = new ImageButton.ImageButtonStyle();
        style.imageUp = drawable;
        style.imageDown = drawable;
        ImageButton button = new ImageButton(style);
        button.getImage().setScaling(Scaling.fit);
        return button;
    }

    @Override
    public void render(float delta) {
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            Gdx.app.exit();
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
        updateMenuLayout();
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
    }

    @Override
    public void dispose() {
        if (stage != null) {
            stage.dispose();
        }
        if (skin != null) {
            skin.dispose();
        }
        if (backgroundTexture != null) {
            backgroundTexture.dispose();
        }
        if (titleTexture != null) {
            titleTexture.dispose();
        }
        if (playTexture != null) {
            playTexture.dispose();
        }
        if (onlineTexture != null) {
            onlineTexture.dispose();
        }
        if (optionsTexture != null) {
            optionsTexture.dispose();
        }
    }
}
