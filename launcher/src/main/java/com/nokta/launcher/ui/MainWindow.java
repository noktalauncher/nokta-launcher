package com.nokta.launcher.ui;

import com.nokta.launcher.utils.PathManager;
import com.google.gson.*;
import com.nokta.launcher.NoktaLauncher;
import com.nokta.launcher.core.UpdateManager;
import com.nokta.launcher.core.AuthManager;
import com.nokta.launcher.spotify.SpotifyManager;
import javafx.application.Platform;
import javafx.geometry.*;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.*;
import javafx.scene.shape.*;
import javafx.stage.Stage;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.*;
import javafx.animation.*;
import javafx.util.Duration;
import com.nokta.launcher.core.ThemeManager;

import java.nio.file.*;

public class MainWindow {
    public static MainWindow instance;
    private Stage stage;
    public static javafx.scene.image.ImageView bgView = null;
    private double xOffset = 0, yOffset = 0;
    private StackPane contentPane;
    private final boolean[] isMaximized = {false};
    private Button[] navBtns;

    // Spotify widget referansı
    private SpotifyWidget spotifyWidget;

    public MainWindow(Stage stage) {
        instance = this; this.stage = stage;
    }

    public void show() {
        BorderPane root = new BorderPane();
        // Arka plan resmi - her zaman oluştur
        bgView = new javafx.scene.image.ImageView();
        bgView.setPreserveRatio(false);
        bgView.fitWidthProperty().bind(root.widthProperty());
        bgView.fitHeightProperty().bind(root.heightProperty());
        root.getChildren().add(0, bgView);
        try {
            java.nio.file.Path _bgp = PathManager.getGameDir().resolve("bg_prefs.json");
            if (java.nio.file.Files.exists(_bgp)) {
                com.google.gson.JsonObject _bj = com.google.gson.JsonParser
                    .parseString(java.nio.file.Files.readString(_bgp)).getAsJsonObject();
                bgView.setImage(new javafx.scene.image.Image(
                    new java.io.File(_bj.get("path").getAsString()).toURI().toString()));
            } else {
                java.io.InputStream _s = getClass().getResourceAsStream("/assets/background.png");
                if (_s != null) bgView.setImage(new javafx.scene.image.Image(_s));
            }
        } catch (Exception _e) { _e.printStackTrace(); }
        root.setStyle("-fx-background-color:#0f0f17;");
        ScrollPane sidebar = buildSidebar();
        root.setLeft(sidebar);

        VBox rightArea = new VBox();
        rightArea.setStyle("-fx-background-color:transparent;");

        HBox titleBar = buildTitleBar();
        rightArea.getChildren().add(titleBar);

        contentPane = new StackPane();
        VBox.setVgrow(contentPane, Priority.ALWAYS);
        try {
            var bgStream = getClass().getResourceAsStream("/images/splash_bg.png");
            if (bgStream != null) {
                ImageView bg = new ImageView(new Image(bgStream));
                bg.setPreserveRatio(false);
                bg.fitWidthProperty().bind(contentPane.widthProperty());
                bg.fitHeightProperty().bind(contentPane.heightProperty());
                bg.setOpacity(0.12);
                contentPane.getChildren().add(bg);
            }
        } catch (Exception ignored) {}
        contentPane.setStyle("-fx-background-color:transparent;");
        rightArea.getChildren().add(contentPane);

        root.setCenter(rightArea);

        titleBar.setOnMousePressed(e -> {
            if (!isMaximized[0]) {
                xOffset = e.getSceneX(); yOffset = e.getSceneY();
            }
        });
        titleBar.setOnMouseDragged(e -> {
            if (!isMaximized[0]) {
                stage.setX(e.getScreenX() - xOffset);
                stage.setY(e.getScreenY() - yOffset);
            }
        });

        showHome();

        Scene scene = new Scene(root, 1280, 720);
        scene.setFill(Color.web("#0f0f17"));
        try {
            String css = getClass().getResource("/css/main.css").toExternalForm();
            scene.getStylesheets().add(css);
        } catch (Exception ignored) {}

        // Uygulama ikonu
        try {
            java.io.InputStream iconStream = getClass().getResourceAsStream("/assets/nokta_logo.png");
            if (iconStream != null) {
                javafx.scene.image.Image icon = new javafx.scene.image.Image(iconStream);
                stage.getIcons().add(icon);
            }
        } catch (Exception ignored) {}
        stage.setScene(scene);
        stage.setTitle("Nokta Launcher");
        stage.setMinWidth(1000);
        stage.setMinHeight(600);
        stage.setResizable(true);
        stage.show();

        // ── Auto-update kontrolü (arka planda) ──────────────────────────
        new Thread(() -> {
            UpdateManager.UpdateInfo info = UpdateManager.checkForUpdates();
            if (!info.available()) return;
            javafx.application.Platform.runLater(() -> {
                javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                    javafx.scene.control.Alert.AlertType.CONFIRMATION);
                alert.setTitle("Güncelleme Mevcut");
                alert.setHeaderText("Nokta Launcher " + info.version() + " yayında!");
                alert.setContentText(
                    (info.changelog() != null && !info.changelog().isEmpty()
                        ? info.changelog().substring(0, Math.min(300, info.changelog().length())) + "\n\n"
                        : "")
                    + "Şimdi güncellemek ister misin?");
                alert.getButtonTypes().setAll(
                    javafx.scene.control.ButtonType.YES,
                    javafx.scene.control.ButtonType.NO);
                alert.showAndWait().ifPresent(btn -> {
                    if (btn == javafx.scene.control.ButtonType.YES) {
                        UpdateManager.downloadAndInstall(
                            info.downloadUrl(),
                            null,
                            pct -> javafx.application.Platform.runLater(() ->
                                System.out.println("⬇ Güncelleme: " + pct + "%")));
                    }
                });
            });
        }, "update-checker").start();
        stage.setOnCloseRequest(e -> {
            Process mc = com.nokta.launcher.ui.PlayScreen.getMinecraftProcess();
            if (mc != null && mc.isAlive()) {
                mc.destroy();
                try { if (!mc.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)) mc.destroyForcibly(); }
                catch (Exception ignored) {}
            }
            javafx.application.Platform.exit();
            System.exit(0);
        });
    }

    private HBox buildTitleBar() {
        HBox bar = new HBox();
        bar.setAlignment(Pos.CENTER_RIGHT);
        bar.setPadding(new Insets(10, 15, 10, 15));
        bar.setStyle("-fx-background-color:#00000033;");
        bar.setPrefHeight(45);

        Label title = new Label("NOKTA LAUNCHER  v1.0.0");
        title.setStyle("-fx-text-fill:#222244;-fx-font-size:11px;-fx-font-weight:bold;");
        HBox.setHgrow(title, Priority.ALWAYS);
        title.setMaxWidth(Double.MAX_VALUE);

        Button minBtn   = buildWinBtn("—", "#f0c040");
        Button maxBtn   = buildWinBtn("⬜", "#40c040");
        Button closeBtn = buildWinBtn("✕", "#f04040");
        minBtn.setOnAction(e -> stage.setIconified(true));
        final double[] savedB = {100, 100, 1280, 720};
        maxBtn.setOnAction(e -> {
            if (!isMaximized[0]) {
                savedB[0]=stage.getX(); savedB[1]=stage.getY();
                savedB[2]=stage.getWidth(); savedB[3]=stage.getHeight();
                javafx.geometry.Rectangle2D scr =
                    javafx.stage.Screen.getPrimary().getVisualBounds();
                stage.setX(scr.getMinX());
                stage.setY(scr.getMinY());
                stage.setWidth(scr.getWidth());
                stage.setHeight(scr.getHeight());
                isMaximized[0] = true;
            } else {
                stage.setX(savedB[0]);
                stage.setY(savedB[1]);
                stage.setWidth(savedB[2]);
                stage.setHeight(savedB[3]);
                isMaximized[0] = false;
            }
        });
        closeBtn.setOnAction(e -> {
            Process mc = com.nokta.launcher.ui.PlayScreen.getMinecraftProcess();
            if (mc != null && mc.isAlive()) {
                mc.destroy();
                try { if (!mc.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)) mc.destroyForcibly(); }
                catch (Exception ignored) {}
            }
            Platform.exit();
        });

        bar.getChildren().addAll(title, minBtn, maxBtn, closeBtn);
        return bar;
    }

    private Button buildWinBtn(String t, String c) {
        Button b = new Button(t);
        String base  = "-fx-background-color:transparent;-fx-text-fill:"+c+";-fx-font-size:13px;-fx-cursor:hand;-fx-padding:2 10;";
        String hover = "-fx-background-color:rgba(255,255,255,0.08);-fx-text-fill:"+c+";-fx-font-size:13px;-fx-cursor:hand;-fx-background-radius:6;-fx-padding:2 10;";
        b.setStyle(base);
        b.setOnMouseEntered(e -> b.setStyle(hover));
        b.setOnMouseExited(e  -> b.setStyle(base));
        return b;
    }

    private ScrollPane buildSidebar() {
        VBox sb = new VBox(4);
        sb.setPrefWidth(220);
        sb.setPadding(new Insets(20, 12, 20, 12));
        sb.setStyle("-fx-background-color:#00000022;-fx-border-color:#ffffff11;-fx-border-width:0 1 0 0;");

        // Logo - Animasyonlu GIF avatar
        VBox logo = new VBox(4);
        logo.setPadding(new Insets(0, 0, 24, 8));

        // Oyuncu adı - logoStack'ten önce tanımla
        Label name = new Label("...");
        name.setStyle("-fx-text-fill:#ffffff;-fx-font-size:16px;-fx-font-weight:bold;");

        // Büyük skin avatarı
        StackPane logoStack = new StackPane();
        logoStack.setMinSize(80, 80); logoStack.setMaxSize(80, 80);
        javafx.scene.shape.Rectangle skinBgBig = new javafx.scene.shape.Rectangle(80, 80);
        skinBgBig.setArcWidth(14); skinBgBig.setArcHeight(14);
        skinBgBig.setFill(javafx.scene.paint.Color.web("#1a1a2e"));
        Label initBig = new Label("?");
        initBig.setStyle("-fx-text-fill:white;-fx-font-size:32px;-fx-font-weight:bold;");
        logoStack.getChildren().addAll(skinBgBig, initBig);
        // Adı ve skini birlikte yükle
        new Thread(() -> {
            String pName = getPlayerName();
            javafx.application.Platform.runLater(() -> {
                name.setText(pName);
                initBig.setText(pName.isEmpty() ? "?" : pName.substring(0,1).toUpperCase());
            });
            try {
                javafx.scene.image.Image img = new javafx.scene.image.Image(
                    "https://minotar.net/helm/" + pName + "/80", 80, 80, false, true);
                if (!img.isError()) {
                    javafx.scene.image.ImageView iv = new javafx.scene.image.ImageView(img);
                    iv.setFitWidth(80); iv.setFitHeight(80);
                    javafx.scene.shape.Rectangle clip = new javafx.scene.shape.Rectangle(80, 80);
                    clip.setArcWidth(14); clip.setArcHeight(14);
                    iv.setClip(clip);
                    javafx.application.Platform.runLater(() -> logoStack.getChildren().setAll(iv));
                }
            } catch (Exception ignored) {}
        }, "skin-loader-big").start();

        String playerName = getPlayerName();
        Label ver = new Label("Launcher  v1.0.0");
        ver.setStyle("-fx-text-fill:#444466;-fx-font-size:11px;");

        logo.getChildren().addAll(logoStack, name, ver);

        Separator s1 = new Separator();
        s1.setStyle("-fx-background-color:#ffffff11;");
        VBox.setMargin(s1, new Insets(0, 0, 12, 0));

        // Nav butonları
        navBtns = new Button[]{
            buildNavBtn("🏠  Ana Sayfa",   true),
            buildNavBtn("🎮  Oyna",        false),
            buildNavBtn("🧩  Modlar",      false),
            buildNavBtn("⚡  Performans",  false),
            buildNavBtn("📦  Sürümler",    false),
            buildNavBtn("👤  Hesap",       false)
        };

        navBtns[0].setOnAction(e -> { setActive(0); showHome(); });
        navBtns[1].setOnAction(e -> { setActive(1); showPlay(); });
        navBtns[2].setOnAction(e -> { setActive(2); showMods(); });
        navBtns[3].setOnAction(e -> { setActive(3); showPerformance(); });
        navBtns[4].setOnAction(e -> { setActive(4); showVersions(); });
        navBtns[5].setOnAction(e -> { setActive(5); showAccount(); });

        VBox nav = new VBox(4);
        nav.getChildren().addAll(navBtns);

        // Discord Ses Widget
        Separator voiceSep = new Separator();
        voiceSep.setStyle("-fx-background-color:#ffffff0a;");
        VBox.setMargin(voiceSep, new Insets(12, 0, 0, 0));

        // Spotify Widget
        Separator spotifySep = new Separator();
        spotifySep.setStyle("-fx-background-color:#ffffff0a;");
        spotifyWidget = new SpotifyWidget();

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        Separator s2 = new Separator();
        s2.setStyle("-fx-background-color:#ffffff11;");
        VBox.setMargin(s2, new Insets(0, 0, 8, 0));

        Button settingsBtn = buildNavBtn("⚙️  Ayarlar", false);
        Button helpBtn     = buildNavBtn("❓  Yardım",  false);
        settingsBtn.setOnAction(e -> { setActive(-1); showSettings(); });

        sb.getChildren().addAll(
            logo, s1, nav,
            spotifySep, spotifyWidget,
            spacer, s2,
            settingsBtn, helpBtn
        );
        ScrollPane sbScroll = new ScrollPane(sb);
        sbScroll.setFitToWidth(true);
        sbScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        sbScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        sbScroll.setStyle(
            "-fx-background:transparent;-fx-background-color:transparent;" +
            "-fx-border-color:transparent;");
        sbScroll.setPrefWidth(220);
        sbScroll.setMinWidth(220);
        sbScroll.setMaxWidth(220);
        return sbScroll;
    }

    private Button buildNavBtn(String text, boolean active) {
        Button b = new Button(text);
        b.setMaxWidth(Double.MAX_VALUE);
        if (active) b.setUserData("active");
        applyNavStyle(b, active);
        b.setOnMouseEntered(e -> { if (!isActive(b)) applyNavHover(b); });
        b.setOnMouseExited(e  -> { if (!isActive(b)) applyNavNormal(b); });
        return b;
    }

    private boolean isActive(Button b) { return "active".equals(b.getUserData()); }

    private void setActive(int idx) {
        for (int i = 0; i < navBtns.length; i++) {
            navBtns[i].setUserData(i == idx ? "active" : null);
            applyNavStyle(navBtns[i], i == idx);
        }
    }

    private void applyNavStyle(Button b, boolean active) {
        if (active) b.setStyle(
            "-fx-background-color:linear-gradient(to right,#6c63ff22,#6c63ff11);" +
            "-fx-text-fill:#a78bfa;-fx-font-size:13px;-fx-alignment:CENTER_LEFT;" +
            "-fx-padding:10 16;-fx-background-radius:8;" +
            "-fx-border-color:#6c63ff55;-fx-border-width:0 0 0 3;-fx-border-radius:8;" +
            "-fx-cursor:hand;-fx-max-width:Infinity;");
        else applyNavNormal(b);
    }

    private void applyNavNormal(Button b) {
        b.setStyle(
            "-fx-background-color:transparent;-fx-text-fill:#8888aa;-fx-font-size:13px;" +
            "-fx-alignment:CENTER_LEFT;-fx-padding:10 16;-fx-background-radius:8;" +
            "-fx-cursor:hand;-fx-max-width:Infinity;");
    }

    private void applyNavHover(Button b) {
        b.setStyle(
            "-fx-background-color:#ffffff11;-fx-text-fill:#ccccee;-fx-font-size:13px;" +
            "-fx-alignment:CENTER_LEFT;-fx-padding:10 16;-fx-background-radius:8;" +
            "-fx-cursor:hand;-fx-max-width:Infinity;");
    }

    // ── Ekran geçişleri ──────────────────────────────────────────────

    private ScrollPane wrapScroll(javafx.scene.Node node) {
        ScrollPane sp = new ScrollPane(node);
        sp.setFitToWidth(true);
        sp.setStyle("-fx-background:transparent;-fx-background-color:transparent;-fx-border-color:transparent;");
        sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        return sp;
    }

    public void showHome()         { contentPane.getChildren().setAll(wrapScroll(buildHomeContent())); }
    private void showPlay()        { contentPane.getChildren().setAll(wrapScroll(new PlayScreen())); }
    private void showMods()        { contentPane.getChildren().setAll(wrapScroll(new ModsScreen())); }
    private void showPerformance() { contentPane.getChildren().setAll(wrapScroll(new PerformanceScreen())); }
    private void showAccount()     { contentPane.getChildren().setAll(wrapScroll(new AccountScreen())); }
    private void showSettings()    { contentPane.getChildren().setAll(wrapScroll(new SettingsScreen())); }
    private void showVersions()    { contentPane.getChildren().setAll(wrapScroll(new VersionsScreen())); }

    // ── Ana sayfa ────────────────────────────────────────────────────

    private VBox buildHomeContent() {
        VBox c = new VBox(24);
        c.setPadding(new Insets(32, 40, 32, 40));
        c.setStyle("-fx-background-color:transparent;");

        // Oyuncu adını al
        String playerName = getPlayerName();
        // Tarih & Saat (şeffaf arka plan)
        Label clockLabel = new Label();
        clockLabel.setStyle("-fx-text-fill:#ffffff;-fx-font-size:32px;-fx-font-weight:bold;-fx-effect:dropshadow(gaussian,#000000aa,8,0,0,2);");
        Label dateLabel = new Label();
        dateLabel.setStyle("-fx-text-fill:#aaaacc;-fx-font-size:13px;-fx-effect:dropshadow(gaussian,#000000aa,6,0,0,1);");
        javafx.animation.Timeline clock = new javafx.animation.Timeline(
            new javafx.animation.KeyFrame(javafx.util.Duration.seconds(1), e2 -> {
                java.time.LocalDateTime now = java.time.LocalDateTime.now();
                clockLabel.setText(String.format("%02d:%02d:%02d", now.getHour(), now.getMinute(), now.getSecond()));
                String[] DAYS = {"Pazar","Pazartesi","Salı","Çarşamba","Perşembe","Cuma","Cumartesi"};
                String[] MONTHS = {"Ocak","Şubat","Mart","Nisan","Mayıs","Haziran","Temmuz","Ağustos","Eylül","Ekim","Kasım","Aralık"};
                dateLabel.setText(DAYS[now.getDayOfWeek().getValue() % 7] + ", " + now.getDayOfMonth() + " " + MONTHS[now.getMonthValue()-1] + " " + now.getYear());
            })
        );
        clock.setCycleCount(javafx.animation.Animation.INDEFINITE);
        clock.play();
        // İlk değer hemen
        java.time.LocalDateTime nowInit = java.time.LocalDateTime.now();
        clockLabel.setText(String.format("%02d:%02d:%02d", nowInit.getHour(), nowInit.getMinute(), nowInit.getSecond()));
        String[] DAYS0 = {"Pazar","Pazartesi","Salı","Çarşamba","Perşembe","Cuma","Cumartesi"};
        String[] MONTHS0 = {"Ocak","Şubat","Mart","Nisan","Mayıs","Haziran","Temmuz","Ağustos","Eylül","Ekim","Kasım","Aralık"};
        dateLabel.setText(DAYS0[nowInit.getDayOfWeek().getValue() % 7] + ", " + nowInit.getDayOfMonth() + " " + MONTHS0[nowInit.getMonthValue()-1] + " " + nowInit.getYear());

        VBox clockBox = new VBox(2, clockLabel, dateLabel);
        clockBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        Label welcome = new Label("Hoş Geldin, " + playerName + "! 👋");
        welcome.setStyle("-fx-text-fill:#ffffff;-fx-font-size:28px;-fx-font-weight:bold;");
        Label sub = new Label("Oynamaya hazır mısın? Sürümünü seç ve başlat.");
        sub.setStyle("-fx-text-fill:#666688;-fx-font-size:14px;");

        // Son oynanan oyun kartı
        VBox lastPlayedCard = buildLastPlayedCard();

        // Gerçek veriler
        String totalPlayStr = getTotalPlayTime();
        String modCountStr  = getModCount();
        String versionCount = getVersionCount();

        HBox statsRow = new HBox(16);
        statsRow.getChildren().addAll(
            statCard("🎮", "Toplam Oyun",  "0 dk",       "#6c63ff", "playtime"),
            statCard("🧩", "Yüklü Mod",    modCountStr,  "#3b82f6"),
            statCard("📦", "Sürümler",     versionCount, "#10b981"),
            statCard("⚡", "Son FPS",      "— fps",      "#f59e0b", "fps")
        );

        Label newsT = new Label("📰  Son Güncellemeler");
        newsT.setStyle("-fx-text-fill:#aaaacc;-fx-font-size:15px;-fx-font-weight:bold;");

        HBox newsRow = new HBox(16);
        newsRow.getChildren().addAll(
            newsCard("🚀 Nokta Launcher v1.0",
                "İlk sürüm! Mod yönetimi, Microsoft girişi ve daha fazlası.", "#6c63ff"),
            newsCard("⚡ Aikar's Flags",
                "Otomatik JVM optimizasyonu ile yüksek FPS!", "#10b981"),
            newsCard("🧩 Modrinth + CurseForge",
                "Launcher içinden iki platformdan mod kur!", "#f59e0b")
        );

        // Spotify bağlantı kartı
        c.getChildren().addAll(clockBox, welcome, sub, lastPlayedCard, statsRow, newsT, newsRow);
        return c;
    }

    private VBox buildSpotifyConnectCard() {
        com.nokta.launcher.spotify.SpotifyWebAPI webAPI = new com.nokta.launcher.spotify.SpotifyWebAPI();
        VBox card = new VBox(0);
        card.setStyle(
            "-fx-background-color:linear-gradient(to right,#0d1f12,#0a1a0f);" +
            "-fx-background-radius:16;" +
            "-fx-border-color:#1db95440;-fx-border-radius:16;-fx-border-width:1;");
        card.setPadding(new Insets(20, 24, 20, 24));
        HBox row = new HBox(16);
        row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        // PP görüntüsü - başlangıçta yeşil daire
        javafx.scene.shape.Circle clipCircle = new javafx.scene.shape.Circle(22);
        javafx.scene.image.ImageView ppView = new javafx.scene.image.ImageView();
        ppView.setFitWidth(44); ppView.setFitHeight(44);
        ppView.setClip(new javafx.scene.shape.Circle(22, 22, 22));
        javafx.scene.image.ImageView logo = new javafx.scene.image.ImageView(
            new javafx.scene.image.Image(MainWindow.class.getResourceAsStream("/com/nokta/launcher/spotify_logo.png"))
        );
        logo.setFitWidth(44); logo.setFitHeight(44);
        javafx.scene.shape.Circle spotifyCircle = new javafx.scene.shape.Circle(22);

        VBox textBox = new VBox(4);
        Label title = new Label();
        Label desc  = new Label();
        title.setStyle("-fx-text-fill:white;-fx-font-size:15px;-fx-font-weight:bold;");
        desc.setStyle("-fx-text-fill:#aaaaaa;-fx-font-size:12px;");
        javafx.scene.control.Button btn = new javafx.scene.control.Button();
        String btnBase = "-fx-text-fill:white;-fx-font-size:13px;-fx-font-weight:bold;" +
            "-fx-background-radius:10;-fx-cursor:hand;-fx-padding:8 20 8 20;";
        if (webAPI.hasToken()) {
            title.setText("Spotify Bağlı ✓");
            desc.setText("Web API aktif — albüm kapağı, seek ve tam kontrol çalışıyor");
            btn.setText("Yeniden Bağla");
            btn.setStyle("-fx-background-color:#14532d;" + btnBase);
            // Profili arka planda yükle
            new Thread(() -> {
                try {
                    String[] profile = webAPI.fetchUserProfile();
                    if (profile == null) return;
                    javafx.application.Platform.runLater(() -> {
                        title.setText("✓ Bağlandı — " + profile[0]);
                        if (!profile[1].isEmpty()) {
                            javafx.scene.image.Image ppImg = new javafx.scene.image.Image(profile[1], 44, 44, true, true, true);
                            ppView.setImage(ppImg);
                            logo.setImage(ppImg);
                        }
                    });
                } catch (Exception ignored) {}
            }, "spotify-profile").start();
        } else {
            title.setText("Spotify'ı Bağla");
            desc.setText("Albüm kapağı, şarkı süresi ve tam kontrol için Spotify hesabına bağlan");
            btn.setText("▶  Spotify ile Bağlan");
            btn.setStyle("-fx-background-color:#1db954;" + btnBase);
        }
        btn.setOnAction(e -> {
            btn.setText("⏳ Bağlanıyor...");
            btn.setDisable(true);
            new Thread(() -> {
                try {
                    boolean ok = webAPI.login();
                    javafx.application.Platform.runLater(() -> {
                        if (ok) {
                            try {
                                String[] profile = webAPI.fetchUserProfile();
                                String uname = profile != null ? profile[0] : "EnesPr0";
                                String ppUrl = profile[1];
                                title.setText("✓ Bağlandı — " + uname);
                                desc.setText("Web API aktif — albüm kapağı, seek ve tam kontrol çalışıyor");
                                // PP yükle
                                if (!ppUrl.isEmpty()) {
                                    javafx.scene.image.Image ppImg = new javafx.scene.image.Image(ppUrl, 44, 44, true, true, true);
                                    ppView.setImage(ppImg);
                                    logo.setImage(ppImg);
                                }
                            } catch (Exception ignored) {
                                title.setText("Spotify Bağlı ✓");
                                desc.setText("Web API aktif — albüm kapağı, seek ve tam kontrol çalışıyor");
                            }
                            btn.setText("Yeniden Bağla");
                            btn.setStyle("-fx-background-color:#14532d;" + btnBase);
                            com.nokta.launcher.spotify.SpotifyManager.get().fetchCurrentTrack();
                            // Kartı yenile
                            if (MainWindow.instance != null) MainWindow.instance.showHome();
                        } else {
                            desc.setText("Bağlantı başarısız, tekrar dene.");
                            btn.setText("▶  Spotify ile Bağlan");
                            btn.setStyle("-fx-background-color:#1db954;" + btnBase);
                            btn.setDisable(false);
                        }
                    });
                } catch (Exception ex) {
                    javafx.application.Platform.runLater(() -> {
                        btn.setText("▶  Spotify ile Bağlan");
                        btn.setDisable(false);
                    });
                }
            }, "spotify-login").start();
        });
        javafx.scene.layout.Region spacer = new javafx.scene.layout.Region();
        HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);
        textBox.getChildren().addAll(title, desc);
        row.getChildren().addAll(logo, textBox, spacer, btn);
        card.getChildren().add(row);
        return card;
    }

    // ── Canlı MC istatistikleri ───────────────────────────────────────────
    private javafx.scene.control.Label sessionPlaytimeLabel = null;
    private javafx.scene.control.Label fpsLabel = null;
    // Son bilinen değerler — sayfa geçişinde kaybolmasın
    private String lastPlaytime = "00:00:00";
    private String lastFps      = "—";

    public void updateSessionPlaytime(String t) {
        lastPlaytime = t;
        if (sessionPlaytimeLabel != null) sessionPlaytimeLabel.setText(t);
    }
    public void updateFps(String fps) {
        lastFps = fps;
        if (fpsLabel != null) fpsLabel.setText(fps + " fps");
    }
    public void registerPlaytimeLabel(javafx.scene.control.Label l) { sessionPlaytimeLabel = l; }
    public void registerFpsLabel(javafx.scene.control.Label l)      { fpsLabel = l; }


    private String getPlayerName() {
        try {
            java.nio.file.Path accountsFile = PathManager.getGameDir().resolve("accounts.json");
            if (!java.nio.file.Files.exists(accountsFile)) return "Oyuncu";
            JsonObject j = JsonParser.parseString(
                Files.readString(accountsFile)).getAsJsonObject();
            // Düz obje formatı: {"username":"..."}
            if (j.has("username")) return j.get("username").getAsString();
            if (j.has("activeAccount")) {
                String active = j.get("activeAccount").getAsString();
                if (j.has("accounts")) {
                    for (JsonElement el : j.getAsJsonArray("accounts")) {
                        JsonObject acc = el.getAsJsonObject();
                        if (acc.has("username") && active.equals(
                            acc.has("uuid") ? acc.get("uuid").getAsString() : ""))
                            return acc.get("username").getAsString();
                        if (acc.has("username"))
                            return acc.get("username").getAsString();
                    }
                }
            }
        } catch (Exception ignored) {}
        return "Oyuncu";
    }

    private VBox buildLastPlayedCard() {
        VBox card = new VBox(12);
        card.setPadding(new Insets(20, 24, 20, 24));
        card.setStyle(
            "-fx-background-color:linear-gradient(to right,#1a1a2e,#16213e);" +
            "-fx-background-radius:16;" +
            "-fx-border-color:#6c63ff44;-fx-border-radius:16;-fx-border-width:1;");

        // Son oynanan versiyon
        String lastVersion = getLastPlayedVersion();
        long   lastTime    = getLastPlayedTime();

        HBox row = new HBox(16);
        row.setAlignment(Pos.CENTER_LEFT);

        // Minecraft ikonu
        Label icon = new Label("⛏");
        icon.setStyle("-fx-font-size:40px;");

        VBox info = new VBox(4);
        HBox.setHgrow(info, Priority.ALWAYS);
        Label title = new Label(lastVersion != null
            ? "Minecraft " + lastVersion : "Henüz oyun oynanmadı");
        title.setStyle("-fx-text-fill:#ffffff;-fx-font-size:18px;-fx-font-weight:bold;");
        Label time = new Label(lastVersion != null
            ? "Son oyun: " + formatTime(lastTime) : "Bir sürüm seç ve oynamaya başla!");
        time.setStyle("-fx-text-fill:#666688;-fx-font-size:13px;");
        info.getChildren().addAll(title, time);

        Button playBtn = new Button(lastVersion != null ? "▶  Tekrar Oyna" : "▶  Oyna");
        playBtn.setStyle(
            "-fx-background-color:linear-gradient(to right,#6c63ff,#3b82f6);" +
            "-fx-text-fill:white;-fx-font-size:14px;-fx-font-weight:bold;" +
            "-fx-padding:10 24;-fx-background-radius:8;-fx-cursor:hand;");
        playBtn.setOnAction(e -> { setActive(1); showPlay(); });
        playBtn.setOnMouseEntered(ev -> playBtn.setStyle(
            "-fx-background-color:linear-gradient(to right,#7c73ff,#4b92f6);" +
            "-fx-text-fill:white;-fx-font-size:14px;-fx-font-weight:bold;" +
            "-fx-padding:10 24;-fx-background-radius:8;-fx-cursor:hand;"));
        playBtn.setOnMouseExited(ev -> playBtn.setStyle(
            "-fx-background-color:linear-gradient(to right,#6c63ff,#3b82f6);" +
            "-fx-text-fill:white;-fx-font-size:14px;-fx-font-weight:bold;" +
            "-fx-padding:10 24;-fx-background-radius:8;-fx-cursor:hand;"));

        row.getChildren().addAll(icon, info, playBtn);
        card.getChildren().add(row);
        return card;
    }

    private String getLastPlayedVersion() {
        try {
            java.nio.file.Path f = PathManager.getGameDir().resolve("last_played.json");
            if (!Files.exists(f)) return null;
            JsonObject j = JsonParser.parseString(Files.readString(f)).getAsJsonObject();
            return j.has("version") ? j.get("version").getAsString() : null;
        } catch (Exception e) { return null; }
    }

    private long getLastPlayedTime() {
        try {
            java.nio.file.Path f = PathManager.getGameDir().resolve("last_played.json");
            if (!Files.exists(f)) return 0;
            JsonObject j = JsonParser.parseString(Files.readString(f)).getAsJsonObject();
            return j.has("time") ? j.get("time").getAsLong() : 0;
        } catch (Exception e) { return 0; }
    }

    private String formatTime(long millis) {
        if (millis == 0) return "Bilinmiyor";
        long diff = System.currentTimeMillis() - millis;
        if (diff < 60000)   return "Az önce";
        if (diff < 3600000) return (diff/60000) + " dakika önce";
        if (diff < 86400000) return (diff/3600000) + " saat önce";
        return (diff/86400000) + " gün önce";
    }


    private String getTotalPlayTime() {
        try {
            java.nio.file.Path f = PathManager.getGameDir().resolve("playtime.json");
            if (!java.nio.file.Files.exists(f)) return "0 saat";
            JsonObject j = JsonParser.parseString(java.nio.file.Files.readString(f)).getAsJsonObject();
            long totalMs = j.has("totalMs") ? j.get("totalMs").getAsLong() : 0;
            long hours = totalMs / 3600000;
            long mins  = (totalMs % 3600000) / 60000;
            long secs  = (totalMs % 60000) / 1000;
            if (hours > 0) return hours + " sa " + mins + " dk " + secs + " sn";
            if (mins  > 0) return mins + " dk " + secs + " sn";
            return secs + " sn";
        } catch (Exception e) { return "0 saat"; }
    }

    private String getModCount() {
        try {
            java.nio.file.Path modsDir = PathManager.getGameDir().resolve("mods").resolve("1.21.4");
            if (!java.nio.file.Files.exists(modsDir)) return "0 mod";
            long count = java.nio.file.Files.list(modsDir)
                .filter(p -> p.toString().endsWith(".jar")).count();
            return count + " mod";
        } catch (Exception e) { return "0 mod"; }
    }

    private String getVersionCount() {
        try {
            java.nio.file.Path versDir = PathManager.getGameDir().resolve("versions");
            if (!java.nio.file.Files.exists(versDir)) return "0 sürüm";
            long count = java.nio.file.Files.list(versDir)
                .filter(java.nio.file.Files::isDirectory).count();
            return count + " sürüm";
        } catch (Exception e) { return "0 sürüm"; }
    }

    private VBox statCard(String icon, String label, String value, String color) {
        return statCard(icon, label, value, color, null);
    }
    private VBox statCard(String icon, String label, String value, String color, String registerId) {
        VBox card = new VBox(6);
        card.setPadding(new Insets(20, 24, 20, 24));
        HBox.setHgrow(card, Priority.ALWAYS);
        card.setStyle("-fx-background-color:#00000044;-fx-background-radius:12;" +
            "-fx-border-color:#1e1e3088;-fx-border-radius:12;-fx-border-width:1;");
        Label l1 = new Label(icon + "  " + label);
        l1.setStyle("-fx-text-fill:#555577;-fx-font-size:12px;");
        Label l2 = new Label(value);
        l2.setStyle("-fx-text-fill:" + color + ";-fx-font-size:22px;-fx-font-weight:bold;");
        if ("fps".equals(registerId)) {
            fpsLabel = l2;
            l2.setText(lastFps.equals("—") ? "— fps" : lastFps + " fps");
        }
        if ("playtime".equals(registerId)) {
            sessionPlaytimeLabel = l2;
            l2.setText(lastPlaytime);
        }
        card.getChildren().addAll(l1, l2);
        return card;
    }

    private VBox newsCard(String title, String desc, String accent) {
        VBox card = new VBox(10);
        card.setPadding(new Insets(20));
        HBox.setHgrow(card, Priority.ALWAYS);
        card.setStyle("-fx-background-color:#00000033;-fx-background-radius:12;" +
            "-fx-border-color:" + accent + "44;-fx-border-radius:12;-fx-border-width:1;");
        Label t = new Label(title);
        t.setStyle("-fx-text-fill:#ddddff;-fx-font-size:13px;-fx-font-weight:bold;");
        Label d = new Label(desc);
        d.setStyle("-fx-text-fill:#666688;-fx-font-size:12px;");
        d.setWrapText(true);
        card.getChildren().addAll(t, d);
        return card;
    }
}
