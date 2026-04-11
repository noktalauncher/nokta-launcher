package com.nokta.launcher;

import com.nokta.launcher.core.NokTaConfig;
import com.nokta.launcher.discord.DiscordRPC;
import com.nokta.launcher.spotify.SpotifyManager;
import com.nokta.launcher.ui.MainWindow;
import com.nokta.launcher.ui.SplashScreen;
import com.nokta.launcher.utils.PathManager;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

public class NoktaLauncher extends Application {

    public static final DiscordRPC discord = new DiscordRPC();

    @Override
    public void start(Stage primaryStage) throws Exception {
        PathManager.createDirs();
        discord.connect();
        SpotifyManager.get().connect();

        // Uygulama ikonu — Linux/Windows/macOS
        try {
            java.io.InputStream iconStream = getClass().getResourceAsStream("/assets/nokta_logo.png");
            if (iconStream != null) {
                primaryStage.getIcons().add(new javafx.scene.image.Image(iconStream));
            }
        } catch (Exception ignored) {}

        // WM_CLASS — Linux taskbar ikonunu .desktop ile eşleştirir
        try {
            com.sun.glass.ui.Application.GetApplication().setName("nokta-launcher");
        } catch (Exception ignored) {}

        SplashScreen splash = new SplashScreen(primaryStage);
        splash.show(() -> {
            try {
                Stage mainStage = new Stage();
                mainStage.initStyle(StageStyle.UNDECORATED);
                MainWindow mainWindow = new MainWindow(mainStage);
                mainWindow.show();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    @Override
    public void stop() throws Exception {
        discord.disconnect();
        super.stop();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
