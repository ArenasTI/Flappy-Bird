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
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Scaling;
import com.badlogic.gdx.utils.viewport.ScreenViewport;

public class OptionsScreen implements Screen {
    private final MyGdxGame game;
    private final Runnable onBack;

    private Stage stage;
    private Skin skin;
    private Texture backgroundTexture;
    private Texture backTexture;

    public OptionsScreen(MyGdxGame game, Runnable onBack) {
        this.game = game;
        this.onBack = onBack;
    }

    @Override
    public void show() {
        game.applyMusicState();

        stage = new Stage(new ScreenViewport(), game.getBatch());
        skin = UiSkinFactory.createDefaultSkin();

        backgroundTexture = new Texture("png/stage_sky.png");
        backTexture = new Texture("png/back.png");

        Image backgroundImage = new Image(backgroundTexture);
        backgroundImage.setFillParent(true);
        backgroundImage.setScaling(Scaling.fill);
        stage.addActor(backgroundImage);

        Table table = new Table();
        table.setFillParent(true);
        table.defaults().pad(10f);

        Label title = new Label("OPTIONS", skin);
        Label volumeLabel = new Label("VOLUME", skin);
        Label valueLabel = new Label(String.format("%.2f", game.getMusicVolume()), skin);

        final Slider volumeSlider = new Slider(0f, 1f, 0.01f, false, skin);
        volumeSlider.setValue(game.getMusicVolume());

        final TextButton muteButton = new TextButton(game.isMuted ? "UNMUTE" : "MUTE", skin);
        ImageButton backButton = new ImageButton(new TextureRegionDrawable(new TextureRegion(backTexture)));
        backButton.getImage().setScaling(Scaling.fit);

        volumeSlider.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                float value = volumeSlider.getValue();
                game.setMusicVolume(value);
                valueLabel.setText(String.format("%.2f", value));
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
                if (onBack != null) {
                    onBack.run();
                }
            }
        });

        table.add(title).colspan(2).padBottom(24f).row();
        table.add(volumeLabel).left();
        table.add(valueLabel).right().row();
        table.add(volumeSlider).colspan(2).size(360f, 64f).row();
        table.add(muteButton).colspan(2).width(220f).padTop(8f).row();
        table.add(backButton).colspan(2).size(220f, 72f).padTop(16f);

        stage.addActor(table);
        Gdx.input.setInputProcessor(stage);
    }

    @Override
    public void render(float delta) {
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            if (onBack != null) {
                onBack.run();
            }
            return;
        }

        Gdx.gl.glClearColor(0.08f, 0.08f, 0.1f, 1f);
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
        if (backTexture != null) {
            backTexture.dispose();
        }
    }
}
