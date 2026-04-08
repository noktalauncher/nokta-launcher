package com.nokta.launcher.ui;

import javafx.animation.*;
import javafx.application.Platform;
import javafx.geometry.*;
import javafx.scene.Scene;
import javafx.scene.canvas.*;
import javafx.scene.control.*;
import javafx.scene.effect.*;
import javafx.scene.image.*;
import javafx.scene.layout.*;
import javafx.scene.paint.*;
import javafx.scene.shape.*;
import javafx.stage.*;
import javafx.util.Duration;

public class SplashScreen {

    private final Stage stage;
    private Label statusLabel;
    private ProgressBar progressBar;
    private StackPane avatarPane;

    // MC yaratık/karakter emojileri
    private static final String[] MC_MOBS = {
        "🐉","👾","🧟","🕷","🐗","🦇","🐺","💀","👻","🧙",
        "⚔","🗡","🏹","🛡","💎","🪓","🔥","❄","⚡","🌟"
    };

    public SplashScreen(Stage stage) { this.stage = stage; }

    public void show(Runnable onComplete) {
        double W = 860;
        double H = 540;

        StackPane root = new StackPane();
        root.setPrefSize(W, H);
        root.setStyle("-fx-background-color:#080810;");

        // GIF arka plan - pencereyi tam kapla
        ImageView bgGif = new ImageView();
        bgGif.setFitWidth(W); bgGif.setFitHeight(H);
        bgGif.setPreserveRatio(false);
        try {
            var s = getClass().getResourceAsStream("/images/splash_bg.png");
            if (s != null) bgGif.setImage(new Image(s));
            else System.out.println("⚠ splash_bg.png bulunamadı!");
        } catch (Exception e) { System.out.println("⚠ " + e.getMessage()); }
        root.getChildren().add(bgGif);

        // Koyu overlay - içerik okunabilsin
        javafx.scene.shape.Rectangle overlay = new javafx.scene.shape.Rectangle(860, 540);
        overlay.setFill(new LinearGradient(0,0,0,1,true,CycleMethod.NO_CYCLE,
            new Stop(0.0, Color.web("#08081088")),
            new Stop(0.5, Color.web("#08081055")),
            new Stop(1.0, Color.web("#080810cc"))));
        root.getChildren().add(overlay);

        // Yıldız ve mob katmanı
        Pane starField = buildStarField();
        starField.setPrefSize(W, H);
        root.getChildren().add(starField);
        Pane mobs = buildMobLayer();
        mobs.setPrefSize(W, H);
        root.getChildren().add(mobs);

        // Alt içerik — logo ve progress
        VBox content = new VBox(14);
        content.setAlignment(Pos.BOTTOM_CENTER);
        content.setPadding(new Insets(0, 0, 40, 0));
        StackPane.setAlignment(content, Pos.BOTTOM_CENTER);

        // Versiyon etiketi
        Label verLabel = new Label("v1.0.0");
        verLabel.setStyle(
            "-fx-text-fill:#ffffff55;-fx-font-size:11px;-fx-font-weight:bold;");

        VBox progressBox = new VBox(8);
        progressBox.setAlignment(Pos.CENTER);
        progressBox.setMaxWidth(420);

        statusLabel = new Label("Başlatılıyor...");
        statusLabel.setStyle("-fx-text-fill:#ffffffaa;-fx-font-size:12px;");

        progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(420);
        progressBar.setPrefHeight(6);
        progressBar.setStyle(
            "-fx-accent:#cc2222;" +
            "-fx-background-color:#ffffff18;-fx-background-radius:3;");
        progressBox.getChildren().addAll(statusLabel, progressBar);

        content.getChildren().addAll(verLabel, progressBox);
        root.getChildren().add(content);

        Scene scene = new Scene(root, W, H);
        scene.setFill(Color.TRANSPARENT);

        stage.initStyle(StageStyle.UNDECORATED);
        stage.setScene(scene);
        stage.setResizable(false);
        stage.centerOnScreen();
        stage.show();

        simulateLoading(onComplete);
    }

    // ── Güneş/Yıldız avatar ─────────────────────────────────────────
    private StackPane buildSunAvatar() {
        StackPane pane = new StackPane();
        pane.setMinSize(170, 170);
        pane.setMaxSize(170, 170);

        // En dış parıltı
        Circle glow3 = new Circle(84);
        glow3.setFill(new RadialGradient(0,0,0.5,0.5,0.5,true, CycleMethod.NO_CYCLE,
            new Stop(0.7, Color.TRANSPARENT),
            new Stop(1.0, Color.web("#6c63ff22"))));

        // Kesik çizgili dönen dış halka
        Circle outerRing = new Circle(82);
        outerRing.setFill(Color.TRANSPARENT);
        outerRing.setStroke(Color.web("#6c63ff66"));
        outerRing.setStrokeWidth(1.5);
        outerRing.getStrokeDashArray().addAll(6.0, 4.0);
        outerRing.setEffect(new Glow(0.4));

        // Orta halka (nabız)
        Circle midRing = new Circle(0);
        midRing.setVisible(false); // gizle
        midRing.setFill(Color.TRANSPARENT);
        midRing.setStroke(Color.web("#3b82f699"));
        midRing.setStrokeWidth(2);
        midRing.getStrokeDashArray().addAll(3.0, 2.0);

        // İç halka
        Circle innerRing = new Circle(0);
        innerRing.setVisible(false); // gizle
        innerRing.setFill(Color.TRANSPARENT);
        innerRing.setStroke(Color.web("#6c63ffaa"));
        innerRing.setStrokeWidth(2.5);

        // GIF - kare, tam boyut
        ImageView gifView = new ImageView();
        gifView.setFitWidth(160); gifView.setFitHeight(160);
        gifView.setPreserveRatio(false);
        javafx.scene.shape.Rectangle squareClip = new javafx.scene.shape.Rectangle(160, 160);
        squareClip.setArcWidth(20); squareClip.setArcHeight(20);
        gifView.setClip(squareClip);
        gifView.setEffect(new Glow(0.25));
        try {
            var s = getClass().getResourceAsStream("/assets/avatar.gif");
            if (s != null) gifView.setImage(new Image(s));
        } catch (Exception ignored) {}

        pane.getChildren().addAll(glow3, outerRing, gifView);
        return pane;
    }

    // ── Güneş ışınları ───────────────────────────────────────────────
    private Pane buildSunRays() {
        Pane pane = new Pane();
        pane.setPrefSize(680, 420);
        pane.setMouseTransparent(true);

        double cx = 340, cy = 210;
        int rayCount = 16;
        for (int i = 0; i < rayCount; i++) {
            double angle = Math.toRadians(i * (360.0 / rayCount));
            double x2 = cx + Math.cos(angle) * 280;
            double y2 = cy + Math.sin(angle) * 280;
            Line ray = new Line(cx, cy, x2, y2);
            ray.setStroke(Color.web("#6c63ff11"));
            ray.setStrokeWidth(1.5);
            ray.setEffect(new GaussianBlur(2));
            pane.getChildren().add(ray);
        }

        // Döndür
        RotateTransition rt = new RotateTransition(Duration.millis(30000), pane);
        rt.setFromAngle(0); rt.setToAngle(360);
        rt.setCycleCount(Animation.INDEFINITE);
        rt.setInterpolator(Interpolator.LINEAR);
        rt.play();
        return pane;
    }

    // ── Yıldız alanı ─────────────────────────────────────────────────
    private Pane buildStarField() {
        Pane pane = new Pane();
        pane.setPrefSize(680, 420);
        pane.setMouseTransparent(true);

        java.util.Random rnd = new java.util.Random(42);
        for (int i = 0; i < 80; i++) {
            double x = rnd.nextDouble() * 680;
            double y = rnd.nextDouble() * 420;
            double r = 0.5 + rnd.nextDouble() * 1.5;
            Circle star = new Circle(x, y, r);
            star.setFill(Color.web("#ffffff" + String.format("%02x", 20 + rnd.nextInt(60))));

            // Titreme animasyonu
            FadeTransition ft = new FadeTransition(
                Duration.millis(1000 + rnd.nextInt(2000)), star);
            ft.setFromValue(0.1 + rnd.nextDouble() * 0.3);
            ft.setToValue(0.6 + rnd.nextDouble() * 0.4);
            ft.setAutoReverse(true);
            ft.setCycleCount(Animation.INDEFINITE);
            ft.setDelay(Duration.millis(rnd.nextInt(2000)));
            ft.play();
            pane.getChildren().add(star);
        }
        return pane;
    }

    // ── MC yaratıkları ve karakterler ───────────────────────────────
    private Pane buildMobLayer() {
        Pane pane = new Pane();
        pane.setPrefSize(680, 420);
        pane.setMouseTransparent(true);

        java.util.Random rnd = new java.util.Random(7);

        // Sol taraf mob'ları
        String[] leftMobs = {"🧟","🕷","🐺","💀","👻","🦇"};
        for (int i = 0; i < leftMobs.length; i++) {
            Label mob = new Label(leftMobs[i]);
            double size = 18 + rnd.nextInt(14);
            mob.setStyle("-fx-font-size:" + size + "px;-fx-opacity:0.18;");
            mob.setLayoutX(10 + rnd.nextInt(80));
            mob.setLayoutY(30 + i * 58);

            // Yavaş sal
            TranslateTransition tt = new TranslateTransition(
                Duration.millis(3000 + rnd.nextInt(2000)), mob);
            tt.setByY(-8 - rnd.nextInt(8));
            tt.setByX(rnd.nextInt(6) - 3);
            tt.setAutoReverse(true);
            tt.setCycleCount(Animation.INDEFINITE);
            tt.play();
            pane.getChildren().add(mob);
        }

        // Sağ taraf mob'ları
        String[] rightMobs = {"⚔","💎","🏹","🔥","🛡","🪓"};
        for (int i = 0; i < rightMobs.length; i++) {
            Label mob = new Label(rightMobs[i]);
            double size = 18 + rnd.nextInt(14);
            mob.setStyle("-fx-font-size:" + size + "px;-fx-opacity:0.18;");
            mob.setLayoutX(580 + rnd.nextInt(80));
            mob.setLayoutY(30 + i * 58);

            TranslateTransition tt = new TranslateTransition(
                Duration.millis(3000 + rnd.nextInt(2000)), mob);
            tt.setByY(-8 - rnd.nextInt(8));
            tt.setByX(rnd.nextInt(6) - 3);
            tt.setAutoReverse(true);
            tt.setCycleCount(Animation.INDEFINITE);
            tt.play();
            pane.getChildren().add(mob);
        }

        // Alt MC blokları
        String[] blocks = {"⬛","🟫","🟩","⬜","🟦","🟧"};
        for (int i = 0; i < 10; i++) {
            Label b = new Label(blocks[i % blocks.length]);
            b.setStyle("-fx-font-size:14px;-fx-opacity:0.1;");
            b.setLayoutX(80 + i * 55);
            b.setLayoutY(370 + rnd.nextInt(20));
            pane.getChildren().add(b);
        }

        // Üst MC blokları
        for (int i = 0; i < 10; i++) {
            Label b = new Label(blocks[i % blocks.length]);
            b.setStyle("-fx-font-size:14px;-fx-opacity:0.1;");
            b.setLayoutX(80 + i * 55);
            b.setLayoutY(5 + rnd.nextInt(15));
            pane.getChildren().add(b);
        }

        return pane;
    }

    // ── Animasyonlar ─────────────────────────────────────────────────
    private void startAnimations() {
        // Dış halka döner
        RotateTransition outerRot = new RotateTransition(
            Duration.millis(8000), avatarPane.getChildren().get(1));
        outerRot.setFromAngle(0); outerRot.setToAngle(360);
        outerRot.setCycleCount(Animation.INDEFINITE);
        outerRot.setInterpolator(Interpolator.LINEAR);
        outerRot.play();

        // Orta halka ters döner
        RotateTransition midRot = new RotateTransition(
            Duration.millis(12000), avatarPane.getChildren().get(2));
        midRot.setFromAngle(360); midRot.setToAngle(0);
        midRot.setCycleCount(Animation.INDEFINITE);
        midRot.setInterpolator(Interpolator.LINEAR);
        midRot.play();

        // Nabız efekti
        ScaleTransition pulse = new ScaleTransition(Duration.millis(1600), avatarPane);
        pulse.setFromX(1.0); pulse.setToX(1.06);
        pulse.setFromY(1.0); pulse.setToY(1.06);
        pulse.setAutoReverse(true);
        pulse.setCycleCount(Animation.INDEFINITE);
        pulse.play();
    }

    // ── Yükleme ──────────────────────────────────────────────────────
    private void simulateLoading(Runnable onComplete) {
        String[][] steps = {
            {"Discord bağlanıyor...", "0.2"},
            {"Spotify hazırlanıyor...", "0.4"},
            {"Sürümler kontrol ediliyor...", "0.6"},
            {"Tema uygulanıyor...", "0.8"},
            {"Hazır! ✨", "1.0"}
        };

        Timeline tl = new Timeline();
        for (int i = 0; i < steps.length; i++) {
            final String msg = steps[i][0];
            final double val = Double.parseDouble(steps[i][1]);
            tl.getKeyFrames().add(new KeyFrame(Duration.millis(400 + i * 450), e -> {
                statusLabel.setText(msg);
                progressBar.setProgress(val);
            }));
        }
        tl.setOnFinished(e -> {
            PauseTransition pause = new PauseTransition(Duration.millis(400));
            pause.setOnFinished(ev -> { stage.close(); onComplete.run(); });
            pause.play();
        });
        tl.play();
    }

    public void setStatus(String msg, double progress) {
        Platform.runLater(() -> {
            if (statusLabel != null) statusLabel.setText(msg);
            if (progressBar != null) progressBar.setProgress(progress);
        });
    }
}
