package com.flappyboldo.game.desktop;

import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.flappyboldo.game.MyGdxGame;

import java.io.File;

public class DesktopLauncher {
    private static final int BASE_WIDTH = 800;
    private static final int BASE_HEIGHT = 480;

    private static void configureAssetsWorkingDirectory() {
        File assetsDir = new File("assets");
        if (!assetsDir.exists()) {
            assetsDir = new File("desktop/assets");
        }

        if (assetsDir.exists() && assetsDir.isDirectory()) {
            System.setProperty("user.dir", assetsDir.getAbsolutePath());
        }
    }

    public static void main(String[] arg) {
        configureAssetsWorkingDirectory();

        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Flappy Bird");
        config.setWindowedMode(BASE_WIDTH, BASE_HEIGHT);
        config.setResizable(true);
        config.useVsync(true);
        config.setForegroundFPS(60);
        config.setWindowIcon("png/icono.jpg");

        if (arg != null && arg.length > 0 && "--fullscreen".equalsIgnoreCase(arg[0])) {
            Graphics.DisplayMode displayMode = Lwjgl3ApplicationConfiguration.getDisplayMode();
            config.setFullscreenMode(displayMode);
        }

        new Lwjgl3Application(new MyGdxGame(), config);
    }
}
