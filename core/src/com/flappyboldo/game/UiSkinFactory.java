package com.flappyboldo.game;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;

public final class UiSkinFactory {
    private UiSkinFactory() {
    }

    public static Skin createDefaultSkin() {
        Skin skin = new Skin();

        BitmapFont font = new BitmapFont();
        skin.add("default", font);

        Texture darkTexture = createSolidTexture(new Color(0.2f, 0.2f, 0.25f, 1f));
        Texture lightTexture = createSolidTexture(new Color(0.8f, 0.8f, 0.85f, 1f));
        Texture accentTexture = createSolidTexture(new Color(0.3f, 0.7f, 0.35f, 1f));
        Texture inputTexture = createSolidTexture(new Color(0.08f, 0.08f, 0.1f, 0.95f));
        Texture cursorTexture = createSolidTexture(new Color(1f, 1f, 1f, 1f));
        Texture selectionTexture = createSolidTexture(new Color(0.2f, 0.45f, 0.9f, 0.55f));

        skin.add("dark", darkTexture);
        skin.add("light", lightTexture);
        skin.add("accent", accentTexture);
        skin.add("input", inputTexture);
        skin.add("cursor", cursorTexture);
        skin.add("selection", selectionTexture);

        Label.LabelStyle labelStyle = new Label.LabelStyle(font, Color.WHITE);
        skin.add("default", labelStyle);

        TextButton.TextButtonStyle buttonStyle = new TextButton.TextButtonStyle();
        buttonStyle.font = font;
        buttonStyle.fontColor = Color.WHITE;
        buttonStyle.up = new TextureRegionDrawable(new TextureRegion(darkTexture));
        buttonStyle.down = new TextureRegionDrawable(new TextureRegion(lightTexture));
        buttonStyle.over = new TextureRegionDrawable(new TextureRegion(accentTexture));
        skin.add("default", buttonStyle);

        Slider.SliderStyle sliderStyle = new Slider.SliderStyle();
        sliderStyle.background = new TextureRegionDrawable(new TextureRegion(darkTexture));
        sliderStyle.knob = new TextureRegionDrawable(new TextureRegion(lightTexture));
        sliderStyle.knobOver = new TextureRegionDrawable(new TextureRegion(accentTexture));
        skin.add("default-horizontal", sliderStyle);

        TextField.TextFieldStyle textFieldStyle = new TextField.TextFieldStyle();
        textFieldStyle.font = font;
        textFieldStyle.fontColor = Color.WHITE;
        textFieldStyle.cursor = new TextureRegionDrawable(new TextureRegion(cursorTexture));
        textFieldStyle.selection = new TextureRegionDrawable(new TextureRegion(selectionTexture));
        textFieldStyle.background = new TextureRegionDrawable(new TextureRegion(inputTexture));
        textFieldStyle.focusedBackground = new TextureRegionDrawable(new TextureRegion(darkTexture));
        textFieldStyle.disabledBackground = new TextureRegionDrawable(new TextureRegion(darkTexture));
        textFieldStyle.messageFont = font;
        textFieldStyle.messageFontColor = Color.LIGHT_GRAY;
        skin.add("default", textFieldStyle);

        return skin;
    }

    private static Texture createSolidTexture(Color color) {
        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(color);
        pixmap.fill();
        Texture texture = new Texture(pixmap);
        pixmap.dispose();
        return texture;
    }
}
