package com.nokta.launcher.ui;

import com.nokta.launcher.spotify.SpotifyManager;
import javafx.application.Platform;
import javafx.geometry.*;
import javafx.scene.control.*;
import javafx.scene.image.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.*;
import javafx.util.Duration;

public class SpotifyWidget extends VBox {

    private Label titleLabel;
    private Label artistLabel;
    private Slider progressSlider;
    private Label timeLabel;
    private boolean dragging = false;
    private long localProgressMs = 0;
    private long localDurationMs = 0;
    private boolean isPlaying = false;
    private Button playPauseBtn;
    private ImageView albumArt;
    private String lastArtUrl = "";

    public SpotifyWidget() {
        setSpacing(6);
        setPadding(new Insets(10, 8, 10, 8));
        setStyle("-fx-background-color:transparent;");
        buildUI();
        SpotifyManager.get().setOnTrackChanged(track ->
            Platform.runLater(() -> updateTrack(track)));
        // Lokal interpolation - 500ms'de bir slider güncelle
        javafx.animation.Timeline timeline = new javafx.animation.Timeline(
            new javafx.animation.KeyFrame(javafx.util.Duration.millis(500), e -> {
                if (!dragging && isPlaying && localDurationMs > 0) {
                    localProgressMs = Math.min(localProgressMs + 500, localDurationMs);
                    Platform.runLater(() -> {
                        progressSlider.setValue((double) localProgressMs / localDurationMs);
                        long prog = localProgressMs / 1000;
                        long dur  = localDurationMs / 1000;
                        timeLabel.setText(String.format("%d:%02d / %d:%02d",
                            prog / 60, prog % 60, dur / 60, dur % 60));
                    });
                }
            })
        );
        timeline.setCycleCount(javafx.animation.Animation.INDEFINITE);
        timeline.play();
        // Zaten çalan varsa göster
        if (SpotifyManager.get().getLastTrack() != null)
            updateTrack(SpotifyManager.get().getLastTrack());
    }

    private void buildUI() {
        Label header = new Label("🎵  MÜZİK");
        header.setStyle("-fx-text-fill:#444466;-fx-font-size:10px;-fx-font-weight:bold;");

        // Albüm kapağı + bilgi yan yana
        HBox mainRow = new HBox(10);
        mainRow.setAlignment(Pos.CENTER_LEFT);

        // Albüm kapağı (yuvarlak köşe)
        StackPane artPane = new StackPane();
        artPane.setMinSize(48, 48);
        artPane.setMaxSize(48, 48);

        Rectangle clip = new Rectangle(48, 48);
        clip.setArcWidth(8); clip.setArcHeight(8);

        albumArt = new ImageView();
        albumArt.setFitWidth(48); albumArt.setFitHeight(48);
        albumArt.setPreserveRatio(false);
        albumArt.setClip(clip);

        // Varsayılan müzik ikonu
        Label defaultIcon = new Label("🎵");
        defaultIcon.setStyle("-fx-font-size:24px;-fx-background-color:#1a1a2e;" +
            "-fx-background-radius:8;-fx-padding:8;");

        artPane.getChildren().addAll(defaultIcon, albumArt);

        // Şarkı bilgisi
        VBox infoBox = new VBox(3);
        HBox.setHgrow(infoBox, Priority.ALWAYS);

        titleLabel = new Label("Spotify açık değil");
        titleLabel.setStyle("-fx-text-fill:#555577;-fx-font-size:12px;");
        titleLabel.setMaxWidth(130);
        titleLabel.setEllipsisString("...");

        artistLabel = new Label("");
        artistLabel.setStyle("-fx-text-fill:#333355;-fx-font-size:11px;");
        artistLabel.setMaxWidth(130);

        infoBox.getChildren().addAll(titleLabel, artistLabel);
        mainRow.getChildren().addAll(artPane, infoBox);

        // Süre etiketi
        timeLabel = new Label("0:00 / 0:00");
        timeLabel.setStyle("-fx-text-fill:#555577;-fx-font-size:10px;");
        timeLabel.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(timeLabel, Priority.ALWAYS);

        // Seek slider
        progressSlider = new Slider(0, 1, 0);
        progressSlider.setPrefWidth(Double.MAX_VALUE);
        progressSlider.setPrefHeight(14);
        progressSlider.setStyle("-fx-control-inner-background:#1a1a28;");
        progressSlider.setOnMousePressed(e -> dragging = true);
        progressSlider.setOnMouseReleased(e -> {
            dragging = false;
            if (localDurationMs > 0) {
                long ms = (long)(progressSlider.getValue() * localDurationMs);
                localProgressMs = ms;
                SpotifyManager.get().seek((int) ms);
            }
        });

        // Kontrol butonları
        HBox controls = new HBox(4);
        controls.setAlignment(Pos.CENTER);
        Button prevBtn     = ctrlBtn("⏮");
        playPauseBtn       = ctrlBtn("▶");
        Button nextBtn     = ctrlBtn("⏭");
        prevBtn.setOnAction(e     -> SpotifyManager.get().prevTrack());
        playPauseBtn.setOnAction(e -> SpotifyManager.get().playPause());
        nextBtn.setOnAction(e     -> SpotifyManager.get().nextTrack());
        controls.getChildren().addAll(prevBtn, playPauseBtn, nextBtn);

        getChildren().addAll(header, mainRow, timeLabel, progressSlider, controls);
    }

    private Button ctrlBtn(String text) {
        Button b = new Button(text);
        String normal = "-fx-background-color:transparent;-fx-text-fill:#555577;" +
            "-fx-font-size:14px;-fx-cursor:hand;-fx-padding:2 8;";
        String hover  = "-fx-background-color:#1e1e30;-fx-text-fill:#aaaacc;" +
            "-fx-font-size:14px;-fx-cursor:hand;-fx-padding:2 8;-fx-background-radius:6;";
        b.setStyle(normal);
        b.setOnMouseEntered(e -> b.setStyle(hover));
        b.setOnMouseExited(e  -> b.setStyle(normal));
        return b;
    }

    private void updateTrack(SpotifyManager.TrackInfo track) {
        String title = track.title().length() > 18
            ? track.title().substring(0, 18) + "..." : track.title();
        titleLabel.setText(title);
        titleLabel.setStyle("-fx-text-fill:#ccccee;-fx-font-size:12px;");

        String artist = track.artist().length() > 20
            ? track.artist().substring(0, 20) + "..." : track.artist();
        artistLabel.setText(artist);
        artistLabel.setStyle("-fx-text-fill:#1DB954;-fx-font-size:11px;");

        localProgressMs = track.progressMs();
        localDurationMs = track.durationMs();
        isPlaying = track.playing();
        if (track.durationMs() > 0 && !dragging) {
            progressSlider.setValue((double) track.progressMs() / track.durationMs());
            long prog = track.progressMs() / 1000;
            long dur  = track.durationMs() / 1000;
            timeLabel.setText(String.format("%d:%02d / %d:%02d",
                prog / 60, prog % 60, dur / 60, dur % 60));
        }

        playPauseBtn.setText(track.playing() ? "⏸" : "▶");

        // Albüm kapağını yükle (değiştiyse)
        if (!track.albumArt().isEmpty() && !track.albumArt().equals(lastArtUrl)) {
            lastArtUrl = track.albumArt();
            new Thread(() -> {
                try {
                    Image img = new Image(track.albumArt(), 48, 48, false, true, false);
                    Platform.runLater(() -> {
                        albumArt.setImage(img);
                        albumArt.setVisible(true);
                    });
                } catch (Exception ignored) {}
            }, "art-loader").start();
        }
    }
}
