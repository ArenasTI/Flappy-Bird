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
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Scaling;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.flappyboldo.game.MyGdxGame;
import com.flappyboldo.game.UiSkinFactory;
import com.flappyboldo.game.online.net.OnlineProtocol;
import com.flappyboldo.game.online.net.OnlineSession;

public class OnlineJoinRoomScreen implements Screen {
    private final MyGdxGame game;

    private Stage stage;
    private Skin skin;
    private Texture backgroundTexture;
    private TextField nameField;
    private TextField ipField;
    private TextField portField;
    private Label statusLabel;

    public OnlineJoinRoomScreen(MyGdxGame game) {
        this.game = game;
    }

    @Override
    public void show() {
        stage = new Stage(new ScreenViewport(), game.getBatch());
        skin = UiSkinFactory.createDefaultSkin();
        backgroundTexture = new Texture("png/stage_sky.png");

        Image backgroundImage = new Image(backgroundTexture);
        backgroundImage.setFillParent(true);
        backgroundImage.setScaling(Scaling.fill);
        stage.addActor(backgroundImage);

        Table table = new Table();
        table.setFillParent(true);
        table.defaults().pad(8f);

        Label titleLabel = new Label("CONNECT SERVER", skin);
        Label nameLabel = new Label("NAME", skin);
        Label ipLabel = new Label("SERVER IP", skin);
        Label portLabel = new Label("SERVER PORT", skin);
        nameField = new TextField("", skin);
        nameField.setMessageText("Your name");
        nameField.setMaxLength(20);
        nameField.setTextFieldFilter((textField, c) ->
            c != ':' && c != '|' && c != ',' && c != '\n' && c != '\r');

        ipField = new TextField("", skin);
        ipField.setMessageText("Ex: 192.168.1.20");
        ipField.setMaxLength(40);

        portField = new TextField(String.valueOf(OnlineProtocol.PORT), skin);
        portField.setMessageText("Ex: 5555");
        portField.setMaxLength(5);
        portField.setTextFieldFilter((textField, c) -> Character.isDigit(c));

        TextButton joinButton = new TextButton("CONNECT", skin);
        TextButton backButton = new TextButton("BACK", skin);
        statusLabel = new Label("", skin);

        joinButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                joinRoom();
            }
        });

        backButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                game.setScreen(new OnlineModeMenuScreen(game));
            }
        });

        table.add(titleLabel).colspan(2).padBottom(14f).row();
        table.add(nameLabel).left();
        table.add(nameField).width(260f).height(46f).row();
        table.add(ipLabel).left();
        table.add(ipField).width(260f).height(46f).row();
        table.add(portLabel).left();
        table.add(portField).width(260f).height(46f).row();
        table.add(joinButton).width(170f).height(62f);
        table.add(backButton).width(170f).height(62f).row();
        table.add(statusLabel).colspan(2).padTop(12f).row();

        stage.addActor(table);
        Gdx.input.setInputProcessor(stage);
        stage.setKeyboardFocus(nameField);
    }

    private void joinRoom() {
        String name = nameField.getText() == null ? "" : nameField.getText().trim();
        String ip = ipField.getText() == null ? "" : ipField.getText().trim();
        String portRaw = portField.getText() == null ? "" : portField.getText().trim();

        if (name.isEmpty()) {
            statusLabel.setText("Enter a name");
            return;
        }
        if (!OnlineSession.isValidIp(ip)) {
            statusLabel.setText("Invalid IP");
            return;
        }
        if (!OnlineSession.isValidPort(portRaw)) {
            statusLabel.setText("Invalid port");
            return;
        }

        try {
            int port = OnlineSession.parsePort(portRaw);
            OnlineSession session = OnlineSession.joinRoom(name, ip, port);
            game.setScreen(new OnlineLobbyScreen(game, session));
        } catch (IllegalArgumentException e) {
            statusLabel.setText(e.getMessage());
        } catch (Exception e) {
            statusLabel.setText("Could not connect");
        }
    }

    @Override
    public void render(float delta) {
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            game.setScreen(new OnlineModeMenuScreen(game));
            return;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.ENTER)) {
            joinRoom();
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
}
