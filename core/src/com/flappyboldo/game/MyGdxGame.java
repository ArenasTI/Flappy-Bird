package com.flappyboldo.game;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;

/**
 * Created by liray on 12/11/2015.
 */
public class MyGdxGame extends Game {
    private static final String PREFS_NAME = "flappy-bird-online";
    private static final String PREF_MUSIC_VOLUME = "music-volume";
    private static final String PREF_MUSIC_MUTED = "music-muted";

    private SpriteBatch batch;
    private BitmapFont font;
    public Music backgroundMusic;
    private Preferences preferences;
    public float volume;
    public boolean isMuted;

    @Override
    public void create() {
        batch = new SpriteBatch();
        font = new BitmapFont();
        preferences = Gdx.app.getPreferences(PREFS_NAME);
        volume = preferences.getFloat(PREF_MUSIC_VOLUME, 0.5f);
        isMuted = preferences.getBoolean(PREF_MUSIC_MUTED, false);

        FileHandle preferredMusic = Gdx.files.internal("sound/Juego35.wav");
        FileHandle fallbackMusic = Gdx.files.internal("png/Juego 35.wav");
        FileHandle selectedMusic = preferredMusic.exists() ? preferredMusic : fallbackMusic;

        backgroundMusic = Gdx.audio.newMusic(selectedMusic);
        backgroundMusic.setLooping(true);
        backgroundMusic.play();
        applyMusicState();

        setScreen(new MainMenuScreen(this));
    }

    public SpriteBatch getBatch() {
        return batch;
    }

    public BitmapFont getFont() {
        return font;
    }

    public void setMusicVolume(float volume) {
        this.volume = Math.max(0f, Math.min(1f, volume));
        applyMusicState();
        if (preferences != null) {
            preferences.putFloat(PREF_MUSIC_VOLUME, this.volume);
            preferences.flush();
        }
    }

    public void setMuted(boolean muted) {
        isMuted = muted;
        applyMusicState();
        if (preferences != null) {
            preferences.putBoolean(PREF_MUSIC_MUTED, isMuted);
            preferences.flush();
        }
    }

    public void toggleMute() {
        setMuted(!isMuted);
    }

    public void applyMusicState() {
        if (backgroundMusic != null) {
            backgroundMusic.setVolume(isMuted ? 0f : volume);
        }
    }

    public float getMusicVolume() {
        return volume;
    }

    @Override
    public void dispose() {
        super.dispose();
        if (backgroundMusic != null) {
            backgroundMusic.stop();
            backgroundMusic.dispose();
            backgroundMusic = null;
        }

        font.dispose();
        batch.dispose();
    }
}
