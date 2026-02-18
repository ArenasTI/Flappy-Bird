package com.flappyboldo.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;

public class MainScreen implements Screen {
    private final MyGdxGame game;
    private SpriteBatch batch;
    private OrthographicCamera camera;
    private Viewport viewport;
    private Sprite skyBackground;
    private Sprite ground;
    private Sprite startGameButton;
    private Sprite optionsButton;
    private Sprite logo;
    private Sprite birdSprite;
    private Vector2 logoPosition;
    private BitmapFont font;
    private Texture buttonTexture;

    private float groundOffset;

    private final int WORLD_HEIGHT = 200;
    private final int WORLD_WIDTH = 100;

    private Rectangle startGameRect;
    private Rectangle optionsRect;

    public MainScreen(MyGdxGame game) {
        this.game = game;
    }

    private void checkButtons() {
        if (startGameRect == null) {
            startGameRect = new Rectangle(
                0.15f * WORLD_WIDTH,
                0.2f * WORLD_HEIGHT,
                WORLD_WIDTH / 4f,
                WORLD_HEIGHT / 15f
            );
        }

        if (optionsRect == null) {
            optionsRect = new Rectangle(
                0.45f * WORLD_WIDTH,
                0.2f * WORLD_HEIGHT,
                WORLD_WIDTH / 4f,
                WORLD_HEIGHT / 15f
            );
        }

        Vector2 touchPoint = viewport.unproject(new Vector2(Gdx.input.getX(), Gdx.input.getY()));

        if (Gdx.input.isTouched() && startGameRect.contains(touchPoint)) {
            startGameButton.setY(0.185f * WORLD_HEIGHT);
            game.setScreen(new GameScreen(game));
            return;
        }

        if (Gdx.input.isTouched() && optionsRect.contains(touchPoint)) {
            optionsButton.setY(0.185f * WORLD_HEIGHT);
            game.setScreen(new OptionsScreen(game, new Runnable() {
                @Override
                public void run() {
                    game.setScreen(new MainScreen(game));
                }
            }));
            return;
        }

        startGameButton.setY(0.2f * WORLD_HEIGHT);
        optionsButton.setY(0.2f * WORLD_HEIGHT);
    }

    @Override
    public void show() {
        batch = game.getBatch();
        camera = new OrthographicCamera();
        viewport = new FitViewport(WORLD_WIDTH, WORLD_HEIGHT, camera);
        viewport.apply();

        font = game.getFont();

        skyBackground = new Sprite(new Texture("png/stage_sky.png"));
        //leave 1/10 of the screen in the bottom for the stage ground.
        skyBackground.setBounds(0, 0.15f * WORLD_HEIGHT, WORLD_WIDTH, 0.85f * WORLD_HEIGHT);

        ground = new Sprite(new Texture(("png/stage_ground.png")));

        buttonTexture = createSolidTexture();
        startGameButton = new Sprite(buttonTexture);
        startGameButton.setBounds(0.15f * WORLD_WIDTH, 0.2f * WORLD_HEIGHT, WORLD_WIDTH / 4f, WORLD_HEIGHT / 15f);

        optionsButton = new Sprite(buttonTexture);
        optionsButton.setBounds(0.45f * WORLD_WIDTH, 0.2f * WORLD_HEIGHT, WORLD_WIDTH / 4f, WORLD_HEIGHT / 15f);

        logo = new Sprite(new Texture("png/logo.png"));
        logo.setSize(0.7f * WORLD_WIDTH, 0.1f * WORLD_HEIGHT);
        logoPosition = new Vector2(0.1f * WORLD_WIDTH, 0.65f * WORLD_HEIGHT);

        Texture birdTexture = new Texture(Gdx.files.internal("png/bird.png"));
        birdSprite = new Sprite(birdTexture, 0, 0, birdTexture.getWidth(), 59);
        birdSprite.setSize(0.15f * WORLD_WIDTH, WORLD_HEIGHT / 17f);
    }

    private Texture createSolidTexture() {
        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(0.25f, 0.25f, 0.25f, 1f);
        pixmap.fill();
        Texture texture = new Texture(pixmap);
        pixmap.dispose();
        return texture;
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(1, 0, 0, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        viewport.apply();
        checkButtons();
        groundOffset = 0;
        logo.setPosition(logoPosition.x, logoPosition.y);
        birdSprite.setPosition(logoPosition.x + logo.getWidth() + 0.03f * WORLD_WIDTH, logoPosition.y + 0.025f * WORLD_HEIGHT);

        camera.update();
        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        skyBackground.draw(batch);
        for (int i = 0; i < 21; i++) {
            batch.draw(ground, (groundOffset + i) * WORLD_WIDTH / 20f, 0, WORLD_WIDTH / 20f, 0.15f * WORLD_HEIGHT);
        }
        startGameButton.draw(batch);
        optionsButton.draw(batch);
        font.draw(batch, "START", startGameButton.getX() + 0.05f * WORLD_WIDTH, startGameButton.getY() + 0.045f * WORLD_HEIGHT);
        font.draw(batch, "OPTIONS", optionsButton.getX() + 0.02f * WORLD_WIDTH, optionsButton.getY() + 0.045f * WORLD_HEIGHT);
        logo.draw(batch);
        birdSprite.draw(batch);
        batch.end();
    }

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height, true);
    }

    @Override
    public void hide() {

    }

    @Override
    public void dispose() {
        skyBackground.getTexture().dispose();
        ground.getTexture().dispose();
        buttonTexture.dispose();
        logo.getTexture().dispose();
        birdSprite.getTexture().dispose();
    }

    @Override
    public void pause() {

    }

    @Override
    public void resume() {

    }
}
