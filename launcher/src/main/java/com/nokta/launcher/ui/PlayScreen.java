package com.nokta.launcher.ui;

import com.nokta.launcher.core.*;
import java.util.concurrent.atomic.AtomicReference;
import com.nokta.launcher.NoktaLauncher;
import com.nokta.launcher.core.AssetDownloader;
import com.nokta.launcher.utils.PathManager;
import javafx.application.Platform;
import javafx.geometry.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.io.*;
import java.util.List;

public class PlayScreen extends VBox {
    private static final java.nio.file.Path PREFS_FILE =
        PathManager.getGameDir().resolve("play_prefs.json");

    private final VersionManager  versionManager;
    private final GameLauncher    gameLauncher;
    private final NativesManager  nativesManager;
    private final com.nokta.launcher.core.AuthManager   authManager;
    private final com.nokta.launcher.discord.DiscordRPC discordRPC;
    private static volatile Process minecraftProcess = null;
    private static long   sessionStartMs  = 0;
    private static String lastSessionUser = null;

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            Process p = minecraftProcess;
            if (p != null && p.isAlive()) {
                // Playtime'ı kaydet — launcher kapanırken
                long elapsed = System.currentTimeMillis() - sessionStartMs;
                if (elapsed > 5000 && lastSessionUser != null) {
                    try {
                        java.nio.file.Path ptf = com.nokta.launcher.utils.PathManager.getGameDir().resolve("playtime.json");
                        long existing = 0;
                        if (java.nio.file.Files.exists(ptf)) {
                            com.google.gson.JsonObject pj = com.google.gson.JsonParser
                                .parseString(java.nio.file.Files.readString(ptf)).getAsJsonObject();
                            existing = pj.has("totalMs") ? pj.get("totalMs").getAsLong() : 0;
                        }
                        com.google.gson.JsonObject pj2 = new com.google.gson.JsonObject();
                        pj2.addProperty("totalMs", existing + elapsed);
                        java.nio.file.Files.writeString(ptf, pj2.toString());
                    } catch (Exception ignored) {}
                }
                p.destroy();
                try { if (!p.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)) p.destroyForcibly(); }
                catch (Exception ignored) {}
            }
        }, "mc-kill-hook"));
    }

    private ComboBox<String> versionCombo;
    private ComboBox<String> loaderCombo;
    private Slider           ramSlider;
    private Label            ramLabel;
    private Label            statusLabel;
    private ProgressBar      progressBar;
    private Label            progressLabel;
    private Button           actionBtn;
    private Button           installBtn;
    private VBox             logBox;
    private String           lastFps = "—";
    private final java.util.List<String> mcLogLines = new java.util.ArrayList<>();
    private TextField        userField;
    private VBox             logPane;

    public PlayScreen() {
        this.versionManager = new VersionManager(PathManager.getGameDir());
        this.gameLauncher   = new GameLauncher(PathManager.getGameDir());
        this.nativesManager = new NativesManager(PathManager.getGameDir());
        this.authManager    = new com.nokta.launcher.core.AuthManager(PathManager.getGameDir());
        this.discordRPC     = NoktaLauncher.discord;

        setSpacing(20);
        setPadding(new Insets(32, 40, 32, 40));
        setStyle("-fx-background-color:transparent;");
        buildUI();
        loadPrefs();
        loadVersionsAsync();
    }

    private void buildUI() {
        Label title = new Label("🎮  Oyunu Başlat");
        title.setStyle("-fx-text-fill:#ffffff;-fx-font-size:24px;-fx-font-weight:bold;");
        Label sub = new Label("Sürüm ve mod yükleyicisini seç, ardından başlat.");
        sub.setStyle("-fx-text-fill:#666688;-fx-font-size:13px;");

        // ── Ana ayarlar kartı ────────────────────────────────────────
        VBox mainCard = new VBox(20);
        mainCard.setPadding(new Insets(28, 32, 28, 32));
        mainCard.setStyle(
            "-fx-background-color:#00000055;-fx-background-radius:16;" +
            "-fx-border-color:#1e1e30;-fx-border-radius:16;-fx-border-width:1;");

        // Sürüm & loader
        HBox versionRow = new HBox(16);
        versionRow.setAlignment(Pos.CENTER_LEFT);

        VBox vBox = new VBox(8);
        HBox.setHgrow(vBox, Priority.ALWAYS);
        Label vLbl = new Label("Minecraft Sürümü");
        vLbl.setStyle("-fx-text-fill:#aaaacc;-fx-font-size:12px;-fx-font-weight:bold;");
        versionCombo = new ComboBox<>();
        versionCombo.setMaxWidth(Double.MAX_VALUE);
        versionCombo.setPromptText("Yükleniyor...");
        styleCombo(versionCombo);
        versionCombo.setOnAction(e -> savePrefs());
        vBox.getChildren().addAll(vLbl, versionCombo);

        VBox lBox = new VBox(8);
        HBox.setHgrow(lBox, Priority.ALWAYS);
        Label lLbl = new Label("Mod Yükleyici");
        lLbl.setStyle("-fx-text-fill:#aaaacc;-fx-font-size:12px;-fx-font-weight:bold;");
        loaderCombo = new ComboBox<>();
        loaderCombo.getItems().addAll("Vanilla","Fabric","Forge","Quilt","NeoForge");
        loaderCombo.setValue("Vanilla");
        loaderCombo.setMaxWidth(Double.MAX_VALUE);
        styleCombo(loaderCombo);
        lBox.getChildren().addAll(lLbl, loaderCombo);

        versionRow.getChildren().addAll(vBox, lBox);

        // RAM slider
        VBox ramBox = new VBox(8);
        HBox ramHeader = new HBox(10);
        ramHeader.setAlignment(Pos.CENTER_LEFT);
        Label ramTitle = new Label("RAM Miktarı");
        ramTitle.setStyle("-fx-text-fill:#aaaacc;-fx-font-size:12px;-fx-font-weight:bold;");
        ramLabel = new Label("4 GB");
        ramLabel.setStyle("-fx-text-fill:#6c63ff;-fx-font-size:12px;-fx-font-weight:bold;");
        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
        ramHeader.getChildren().addAll(ramTitle, sp, ramLabel);

        ramSlider = new Slider(512, 16384, 4096);
        ramSlider.setStyle("-fx-control-inner-background:#252540;-fx-accent:#6c63ff;");
        ramSlider.valueProperty().addListener((obs, o, n) -> {
            int mb = (int)(Math.round(n.doubleValue() / 512.0) * 512);
            ramSlider.setValue(mb);
            ramLabel.setText(mb >= 1024 ? mb / 1024 + " GB" : mb + " MB");
            savePrefs();
        });

        HBox presets = new HBox(8);
        for (int[] p : new int[][]{{1024},{2048},{4096},{8192}}) {
            int mb = p[0];
            Button b = new Button(mb >= 1024 ? mb/1024 + " GB" : mb + " MB");
            b.setStyle("-fx-background-color:#ffffff11;-fx-text-fill:#8888aa;" +
                "-fx-background-radius:6;-fx-padding:4 12;-fx-cursor:hand;-fx-font-size:11px;");
            b.setOnAction(e -> ramSlider.setValue(mb));
            presets.getChildren().add(b);
        }
        ramBox.getChildren().addAll(ramHeader, ramSlider, presets);

        // Kullanıcı adı
        VBox userBox = new VBox(8);
        Label userLbl = new Label("Oyuncu Adı");
        userLbl.setStyle("-fx-text-fill:#aaaacc;-fx-font-size:12px;-fx-font-weight:bold;");
        userField = new TextField("Oyuncu");
        userField.textProperty().addListener((o,ov,nv) -> savePrefs());
        userField.setStyle(
            "-fx-background-color:#6c63ff44;-fx-text-fill:#ffffff;" +
            "-fx-border-color:#3a3a60;-fx-border-radius:8;-fx-background-radius:8;" +
            "-fx-font-size:13px;-fx-padding:10 14;");
        userBox.getChildren().addAll(userLbl, userField);

        mainCard.getChildren().addAll(versionRow, ramBox, userBox);

        // ── İlerleme kartı ───────────────────────────────────────────
        VBox progressCard = new VBox(10);
        progressCard.setPadding(new Insets(20, 24, 20, 24));
        progressCard.setStyle(
            "-fx-background-color:#00000044;-fx-background-radius:12;" +
            "-fx-border-color:#1e1e30;-fx-border-radius:12;-fx-border-width:1;");

        statusLabel = new Label("✅  Hazır");
        statusLabel.setStyle("-fx-text-fill:#44dd88;-fx-font-size:13px;");

        progressBar = new ProgressBar(0);
        progressBar.setMaxWidth(Double.MAX_VALUE);
        progressBar.setPrefHeight(8);
        progressBar.setStyle("-fx-accent:#6c63ff;-fx-background-color:#ffffff11;" +
            "-fx-background-radius:4;-fx-border-radius:4;");
        progressBar.setVisible(false);

        progressLabel = new Label("");
        progressLabel.setStyle("-fx-text-fill:#555577;-fx-font-size:11px;");

        progressCard.getChildren().addAll(statusLabel, progressBar, progressLabel);

        // ── Log alanı — başlık + klasör butonları ───────────────────
        logBox = new VBox(3);
        logBox.setPadding(new Insets(6));
        logBox.setStyle("-fx-background-color:#00000044;-fx-background-radius:8;");

        ScrollPane logScroll = new ScrollPane(logBox);
        logScroll.setFitToWidth(true);
        logScroll.setPrefHeight(200);
        logScroll.setStyle(
            "-fx-background:#00000000;-fx-background-color:transparent;" +
            "-fx-border-color:#1a1a2888;-fx-border-radius:8;-fx-border-width:1;");

        // Klasör aç butonları
        Button modsBtn = folderButton("📂  Mods",  "#6c63ff44", "#9d98ff",
            PathManager.getModsDir(versionCombo.getValue() != null
                ? versionCombo.getValue() : ""));
        Button logsBtn = folderButton("📋  Logs",  "#ffffff11", "#8888aa",
            PathManager.getGameDir().resolve("logs"));

        // versionCombo değişince modsBtn path'i güncelle
        versionCombo.setOnAction(e -> {
            savePrefs();
            modsBtn.setOnAction(ev ->
                openFolder(PathManager.getModsDir(versionCombo.getValue() != null
                    ? versionCombo.getValue() : "")));
        });

        HBox logHeader = new HBox(10);
        logHeader.setAlignment(Pos.CENTER_LEFT);
        Label logTitle = new Label("📋 Minecraft Logu");
        logTitle.setStyle("-fx-text-fill:#555577;-fx-font-size:11px;");
        Region logSpacer = new Region();
        HBox.setHgrow(logSpacer, Priority.ALWAYS);
        logHeader.getChildren().addAll(logTitle, logSpacer, modsBtn, logsBtn);

        logPane = new VBox(6, logHeader, logScroll);
        logPane.setVisible(false);
        logPane.setManaged(false);

        // ── Alt buton satırı ─────────────────────────────────────────
        HBox btnRow = new HBox(12);
        btnRow.setAlignment(Pos.CENTER_RIGHT);

        installBtn = new Button("⬇  Sürümü Kur");
        installBtn.setStyle(
            "-fx-background-color:#ffffff11;-fx-text-fill:#aaaacc;" +
            "-fx-font-size:13px;-fx-background-radius:10;-fx-padding:12 24;-fx-cursor:hand;");
        installBtn.setOnAction(e -> installVersion(versionCombo.getValue()));

        actionBtn = new Button("▶  OYNA");
        actionBtn.setPrefWidth(160);
        actionBtn.setPrefHeight(48);
        stylePlayBtn(actionBtn, false);
        actionBtn.setOnAction(e -> launchGame());

        btnRow.getChildren().addAll(installBtn, actionBtn);

        getChildren().addAll(title, sub, mainCard, progressCard, logPane, btnRow);
    }

    // ── Klasör butonu fabrikası ──────────────────────────────────────
    private Button folderButton(String text, String bg, String fg,
                                java.nio.file.Path path) {
        Button btn = new Button(text);
        btn.setStyle(
            "-fx-background-color:" + bg + ";-fx-text-fill:" + fg + ";" +
            "-fx-background-radius:6;-fx-padding:4 12;-fx-cursor:hand;-fx-font-size:11px;");
        btn.setOnAction(e -> openFolder(path));
        // Hover efekti
        btn.setOnMouseEntered(e -> btn.setStyle(
            "-fx-background-color:#6c63ff66;-fx-text-fill:#ffffff;" +
            "-fx-background-radius:6;-fx-padding:4 12;-fx-cursor:hand;-fx-font-size:11px;"));
        btn.setOnMouseExited(e -> btn.setStyle(
            "-fx-background-color:" + bg + ";-fx-text-fill:" + fg + ";" +
            "-fx-background-radius:6;-fx-padding:4 12;-fx-cursor:hand;-fx-font-size:11px;"));
        return btn;
    }

    // ── Klasörü dosya yöneticisinde aç (Win/Linux/macOS) ────────────
    private void openFolder(java.nio.file.Path path) {
        try {
            // Klasör yoksa oluştur
            java.nio.file.Files.createDirectories(path);
            String os = System.getProperty("os.name").toLowerCase();
            ProcessBuilder pb;
            if (os.contains("win")) {
                pb = new ProcessBuilder("explorer", path.toAbsolutePath().toString());
            } else if (os.contains("mac")) {
                pb = new ProcessBuilder("open", path.toAbsolutePath().toString());
            } else {
                // Linux: xdg-open, nautilus, thunar — sırayla dene
                pb = new ProcessBuilder("xdg-open", path.toAbsolutePath().toString());
            }
            pb.start();
        } catch (Exception ex) {
            addLog("⚠ Klasör açılamadı: " + ex.getMessage());
        }
    }

    // ── Versiyon listesi ─────────────────────────────────────────────
    private void loadVersionsAsync() {
        new Thread(() -> {
            try {
                List<VersionManager.MCVersion> versions = versionManager.fetchVersionList();
                Platform.runLater(() -> {
                    versionCombo.getItems().clear();
                    for (VersionManager.MCVersion v : versions)
                        if (v.type.equals("release")) versionCombo.getItems().add(v.id);
                    if (!versionCombo.getItems().isEmpty())
                        versionCombo.setValue(versionCombo.getItems().get(0));
                });
            } catch (Exception ex) {
                Platform.runLater(() -> versionCombo.setPromptText("İnternet yok"));
            }
        }, "version-loader").start();
    }

    // ── Kurulum ──────────────────────────────────────────────────────
    private void installVersion(String versionId) {
        if (versionId == null) return;
        AtomicReference<String> versionRef = new AtomicReference<>(versionId);

        setStatus("⬇  Kuruluyor: " + versionId, "#f59e0b");
        progressBar.setVisible(true);
        installBtn.setDisable(true);
        actionBtn.setDisable(true);

        AssetDownloader assetDownloader = new AssetDownloader(PathManager.getGameDir());

        new Thread(() -> {
            try {
                addLog("📦 Sürüm indiriliyor: " + versionRef.get());
                versionManager.downloadVersion(versionRef.get(), (msg, pct) ->
                    Platform.runLater(() -> {
                        progressBar.setProgress(pct / 250.0);
                        progressLabel.setText(msg);
                        if (pct % 10 == 0) addLog(pct + "% " + msg);
                    })
                );

                String loader = loaderCombo.getValue();
                if ("Forge".equals(loader)) {
                    Platform.runLater(() -> setStatus("⚒ Forge kuruluyor...", "#f59e0b"));
                    addLog("⚒ Forge kuruluyor...");
                    com.nokta.launcher.core.ForgeInstaller fi =
                        new com.nokta.launcher.core.ForgeInstaller(PathManager.getGameDir());
                    String forgeId = fi.installForge(versionRef.get(), (msg, pct) ->
                        Platform.runLater(() -> { progressLabel.setText(msg); addLog(msg); }));
                    versionRef.set(forgeId);
                } else if ("NeoForge".equals(loader)) {
                    Platform.runLater(() -> setStatus("⚒ NeoForge kuruluyor...", "#f59e0b"));
                    addLog("⚒ NeoForge kuruluyor...");
                    com.nokta.launcher.core.ForgeInstaller fi =
                        new com.nokta.launcher.core.ForgeInstaller(PathManager.getGameDir());
                    String neoId = fi.installNeoForge(versionRef.get(), (msg, pct) ->
                        Platform.runLater(() -> { progressLabel.setText(msg); addLog(msg); }));
                    versionRef.set(neoId);
                } else if ("Fabric".equals(loader)) {
                    Platform.runLater(() -> setStatus("🧵 Fabric kuruluyor...", "#f59e0b"));
                    addLog("🧵 Fabric Loader kuruluyor...");
                    com.nokta.launcher.core.FabricInstaller fi =
                        new com.nokta.launcher.core.FabricInstaller(PathManager.getGameDir());
                    fi.install(versionRef.get(), (msg, pct) ->
                        Platform.runLater(() -> { progressLabel.setText(msg); addLog(msg); }));
                }

                Platform.runLater(() -> setStatus("🖼  Assets indiriliyor...", "#f59e0b"));
                addLog("🖼 Assets indiriliyor...");
                assetDownloader.downloadAssets(versionRef.get(), (msg, pct) ->
                    Platform.runLater(() -> {
                        progressBar.setProgress(0.4 + pct / 250.0);
                        progressLabel.setText(msg);
                        if (pct % 20 == 0) addLog(pct + "% " + msg);
                    })
                );

                Platform.runLater(() -> setStatus("🔧 Natives hazırlanıyor...", "#f59e0b"));
                addLog("🔧 Natives çıkartılıyor...");
                nativesManager.extractNatives(versionRef.get(), (msg, pct) ->
                    Platform.runLater(() -> {
                        progressBar.setProgress(0.8 + pct / 500.0);
                        progressLabel.setText(msg);
                    })
                );

                Platform.runLater(() -> {
                    setStatus("✅  Kurulum tamamlandı: " + versionRef.get(), "#44dd88");
                    progressBar.setProgress(1.0);
                    installBtn.setDisable(false);
                    actionBtn.setDisable(false);
                    addLog("✅ Hazır! Oynamak için ▶ OYNA butonuna bas.");
                });

            } catch (Exception ex) {
                Platform.runLater(() -> {
                    setStatus("❌  Hata: " + ex.getMessage(), "#f04040");
                    installBtn.setDisable(false);
                    actionBtn.setDisable(false);
                    addLog("❌ HATA: " + ex.getMessage());
                });
            }
        }, "installer").start();
    }

    // ── Oyun başlatma ────────────────────────────────────────────────
    private void launchGame() {
        String versionId = versionCombo.getValue();
        if (versionId == null) { setStatus("❌ Sürüm seçiniz!", "#f04040"); return; }
        if (!versionManager.isInstalled(versionId)) {
            setStatus("⚠ Önce 'Sürümü Kur' butonuna basın!", "#f59e0b"); return;
        }

        String loader = loaderCombo.getValue();
        if ("Fabric".equals(loader)) {
            try {
                com.nokta.launcher.core.FabricInstaller fi =
                    new com.nokta.launcher.core.FabricInstaller(PathManager.getGameDir());
                String fabricId = fi.install(versionId, (msg, pct) -> {});
                versionId = fabricId;
            } catch (Exception e) {
                addLog("⚠ Fabric bulunamadı, Vanilla ile başlatılıyor.");
            }
        }

        com.nokta.launcher.core.AuthManager.Account acc = authManager.getCurrentAccount();
        GameLauncher.LaunchConfig cfg = new GameLauncher.LaunchConfig();
        cfg.versionId   = versionId;
        String savedUsername = userField.getText() != null && !userField.getText().isEmpty()
            ? userField.getText() : "Oyuncu";
        cfg.username    = (acc != null && acc.username != null && !acc.username.isEmpty())
            ? acc.username : savedUsername;
        cfg.uuid        = (acc != null) ? acc.uuid : cfg.uuid;
        cfg.accessToken = (acc != null) ? acc.accessToken : "0";
        cfg.userType    = (acc != null && acc.microsoft) ? "msa" : "legacy";
        cfg.ramMB       = (int) ramSlider.getValue();
        cfg.javaPath    = findJava();
        cfg.useAikarsFlags = true;

        savePrefs();
        setStatus("🚀  Başlatılıyor...", "#3b82f6");
        logBox.getChildren().clear();
        mcLogLines.clear();
        logPane.setVisible(true);
        logPane.setManaged(true);
        // Ana sayfa kartlarını güncelle
        if (MainWindow.instance != null) {
            MainWindow.instance.setPlayingState(true);
        }

        stylePlayBtn(actionBtn, true);
        actionBtn.setOnAction(ev -> {
            if (minecraftProcess != null && minecraftProcess.isAlive())
                minecraftProcess.destroyForcibly();
        });

        final String finalVersionId = cfg.versionId;
        final String finalLoader    = loaderCombo.getValue();

        new Thread(() -> {
            try {
                Process process = gameLauncher.launch(cfg);
                minecraftProcess = process;

                // Session sayacını sıfırla
                Platform.runLater(() -> {
                    if (MainWindow.instance != null)
                        MainWindow.instance.updateSessionPlaytime("00:00:00");
                });
                // Discord: MC açıldı
                Platform.runLater(() -> {
                    if (discordRPC != null)
                        discordRPC.setPlayingMinecraft(finalVersionId, finalLoader);
                });

                // Playtime sayacı
                if (MainWindow.instance != null)
                    MainWindow.instance.updateSessionPlaytime("00:00:00");
                final long[] sessionStart = {System.currentTimeMillis()};
                java.util.concurrent.ScheduledExecutorService ticker =
                    java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
                // Başlangıçtaki toplam süreyi oku
                final long[] existingMs = {0};
                try {
                    java.nio.file.Path ptf = PathManager.getGameDir().resolve("playtime.json");
                    if (java.nio.file.Files.exists(ptf)) {
                        com.google.gson.JsonObject pj = com.google.gson.JsonParser
                            .parseString(java.nio.file.Files.readString(ptf)).getAsJsonObject();
                        existingMs[0] = pj.has("totalMs") ? pj.get("totalMs").getAsLong() : 0;
                    }
                } catch (Exception ignored) {}

                ticker.scheduleAtFixedRate(() -> {
                    long elapsed = System.currentTimeMillis() - sessionStart[0];
                    // Session
                    String sess = String.format("%02d:%02d:%02d",
                        elapsed / 3600000, (elapsed / 60000) % 60, (elapsed / 1000) % 60);
                    // Total = mevcut + bu oturum
                    long totalMs = existingMs[0] + elapsed;
                    String total = String.format("%02d:%02d:%02d",
                        totalMs / 3600000, (totalMs / 60000) % 60, (totalMs / 1000) % 60);
                    // HUD için
                    try {
                        java.nio.file.Files.writeString(
                            PathManager.getGameDir().resolve("session_time.txt"), sess);
                    } catch (Exception ignored) {}
                    Platform.runLater(() -> {
                        if (MainWindow.instance != null) {
                            MainWindow.instance.updateSessionPlaytime(sess);
                            MainWindow.instance.updateTotalPlaytime(total);
                        }
                    });
                }, 1, 1, java.util.concurrent.TimeUnit.SECONDS);

                // Log okuyucu
                new Thread(() -> {
                    try {
                        var reader = new java.io.BufferedReader(
                            new java.io.InputStreamReader(process.getInputStream()));
                        String line;
                        while ((line = reader.readLine()) != null) {
                            final String l = line;
                            // FPS parse
                            if (l.contains(" fps") || l.contains("FPS")) {
                                java.util.regex.Matcher m = java.util.regex.Pattern
                                    .compile("(\\d+)\\s*fps",
                                        java.util.regex.Pattern.CASE_INSENSITIVE)
                                    .matcher(l);
                                if (m.find()) {
                                    final String fps = m.group(1);
                                    lastFps = fps;
                                    Platform.runLater(() -> {
                                        if (MainWindow.instance != null)
                                            MainWindow.instance.updateFps(fps);
                                    });
                                }
                            }
                            Platform.runLater(() -> addLog(l));
                        }
                    } catch (Exception ignored) {}
                    ticker.shutdownNow();
                }, "mc-log-reader").start();

                // Discord: sunucu bilgisi polling
                // Yeni DiscordRPC metodlarını kullanıyoruz — setPresence() artık yok
                Thread discordPolling = new Thread(() -> {
                    java.nio.file.Path serverFile =
                        PathManager.getGameDir().resolve("server_info.json");
                    String lastServer = "";
                    while (process.isAlive()) {
                        try {
                            Thread.sleep(3000);
                            if (java.nio.file.Files.exists(serverFile)) {
                                com.google.gson.JsonObject obj =
                                    com.google.gson.JsonParser.parseString(
                                        java.nio.file.Files.readString(serverFile))
                                    .getAsJsonObject();
                                String server = obj.has("server")
                                    ? obj.get("server").getAsString() : "";
                                if (!server.equals(lastServer)) {
                                    lastServer = server;
                                    final String s = server;
                                    if (discordRPC != null) {
                                        if (s.isEmpty()) {
                                            // Menüde
                                            discordRPC.setPlayingMinecraft(
                                                finalVersionId, finalLoader);
                                        } else if (s.equals("Singleplayer")) {
                                            discordRPC.setPlayingSingleplayer(
                                                finalVersionId, finalLoader);
                                        } else {
                                            discordRPC.setPlayingOnServer(
                                                s, finalVersionId, finalLoader);
                                        }
                                    }
                                }
                            }
                        } catch (Exception ignored) {}
                    }
                }, "discord-server-poll");
                discordPolling.setDaemon(true);
                discordPolling.start();

                int exitCode = process.waitFor();

                // Playtime kaydet
                long elapsed = System.currentTimeMillis() - sessionStart[0];
                savePlaytime(cfg.username, elapsed);

                Platform.runLater(() -> {
                    minecraftProcess = null;
                    // showHome kaldırıldı — süre ve FPS korunsun
                    stylePlayBtn(actionBtn, false);
                    actionBtn.setOnAction(ev -> launchGame());
                    actionBtn.setDisable(false);
                    if (exitCode != 0) {
                        String crashReason = mcLogLines.stream()
                            .filter(l -> l.contains("ERROR") || l.contains("Exception")
                                || l.contains("FATAL") || l.contains("crash"))
                            .reduce((a, b) -> b).orElse("Bilinmeyen hata");
                        addLog("💥 Minecraft çöktü! Sebep: " + crashReason);
                        setStatus("💥 Çökme: " +
                            crashReason.substring(0, Math.min(80, crashReason.length())),
                            "#f04040");
                    } else {
                        setStatus("⏹  Oyun kapandı", "#666688");
                        addLog("⏹ Minecraft kapatıldı. Exit: " + exitCode);
                    }
                    if (discordRPC != null) discordRPC.setInLauncher();
                    if (MainWindow.instance != null) {
                        MainWindow.instance.setPlayingState(false);
                        MainWindow.instance.refreshTotalPlaytime();
                    }
                });

            } catch (Exception ex) {
                Platform.runLater(() -> {
                    setStatus("❌  " + ex.getMessage(), "#f04040");
                    stylePlayBtn(actionBtn, false);
                    actionBtn.setOnAction(ev -> launchGame());
                    actionBtn.setDisable(false);
                    addLog("❌ " + ex.getMessage());
                });
            }
        }, "game-launcher").start();
    }

    // ── Buton stilleri ───────────────────────────────────────────────
    private void stylePlayBtn(Button btn, boolean running) {
        btn.setText(running ? "⏹  DURDUR" : "▶  OYNA");
        String color = running ? "#f04040,#e53e3e" : "#6c63ff,#3b82f6";
        String shadow = running ? "#f0404088" : "#6c63ff88";
        btn.setStyle(
            "-fx-background-color:linear-gradient(to right," + color + ");" +
            "-fx-text-fill:white;-fx-font-size:15px;-fx-font-weight:bold;" +
            "-fx-background-radius:12;-fx-cursor:hand;" +
            "-fx-effect:dropshadow(gaussian," + shadow + ",12,0,0,3);");
    }

    // ── Playtime kaydet + API ────────────────────────────────────────
    private void savePlaytime(String username, long elapsedMs) {
        try {
            // Son oturum süresini kalıcı kaydet
            long h = elapsedMs / 3600000, m = (elapsedMs / 60000) % 60, s = (elapsedMs / 1000) % 60;
            String formatted = String.format("%02d:%02d:%02d", h, m, s);
            com.google.gson.JsonObject ls = new com.google.gson.JsonObject();
            ls.addProperty("ms", elapsedMs);
            ls.addProperty("formatted", formatted);
            java.nio.file.Files.writeString(
                PathManager.getGameDir().resolve("last_session.json"), ls.toString());
        } catch (Exception ignored) {}
        try {
            java.nio.file.Path ptf = PathManager.getGameDir().resolve("playtime.json");
            long existing = 0;
            if (java.nio.file.Files.exists(ptf)) {
                com.google.gson.JsonObject pj = com.google.gson.JsonParser
                    .parseString(java.nio.file.Files.readString(ptf)).getAsJsonObject();
                existing = pj.has("totalMs") ? pj.get("totalMs").getAsLong() : 0;
            }
            com.google.gson.JsonObject pj2 = new com.google.gson.JsonObject();
            pj2.addProperty("totalMs", existing + elapsedMs);
            java.nio.file.Files.writeString(ptf, pj2.toString());
        } catch (Exception ignored) {}

        new Thread(() -> {
            try {
                com.google.gson.JsonObject body = new com.google.gson.JsonObject();
                body.addProperty("username", username);
                body.addProperty("ms", elapsedMs);
                okhttp3.RequestBody reqBody = okhttp3.RequestBody.create(
                    body.toString(), okhttp3.MediaType.get("application/json"));
                okhttp3.Request req = new okhttp3.Request.Builder()
                    .url(com.nokta.launcher.core.NokTaConfig.API_BASE + "/api/playtime")
                    .header("X-Nokta-Key", com.nokta.launcher.core.NokTaConfig.API_KEY)
                    .post(reqBody)
                    .build();
                try (okhttp3.Response res =
                        new okhttp3.OkHttpClient().newCall(req).execute()) {
                    System.out.println("📊 Playtime API: " + res.code());
                }
            } catch (Exception e) {
                System.out.println("📊 Playtime API hata: " + e.getMessage());
            }
        }, "playtime-api").start();
    }

    // ── Yardımcılar ──────────────────────────────────────────────────
    private void setStatus(String msg, String color) {
        statusLabel.setText(msg);
        statusLabel.setStyle("-fx-text-fill:" + color + ";-fx-font-size:13px;");
    }

    private void addLog(String msg) {
        mcLogLines.add(msg);
        Label lbl = new Label(msg);
        lbl.setStyle("-fx-text-fill:#aaaacc;-fx-font-size:11px;-fx-font-family:monospace;");
        lbl.setWrapText(true);
        logBox.getChildren().add(lbl);
        Platform.runLater(() -> {
            javafx.scene.Parent p = logBox.getParent();
            if (p instanceof ScrollPane sp) sp.setVvalue(1.0);
        });
    }

    private void savePrefs() {
        try {
            com.google.gson.JsonObject j = new com.google.gson.JsonObject();
            if (versionCombo.getValue() != null) j.addProperty("version", versionCombo.getValue());
            if (loaderCombo.getValue()  != null) j.addProperty("loader",  loaderCombo.getValue());
            j.addProperty("ram", (int) ramSlider.getValue());
            if (userField.getText() != null) j.addProperty("username", userField.getText());
            java.nio.file.Files.writeString(PREFS_FILE, j.toString());
        } catch (Exception ignored) {}
    }

    private void loadPrefs() {
        try {
            if (!java.nio.file.Files.exists(PREFS_FILE)) return;
            com.google.gson.JsonObject j = com.google.gson.JsonParser.parseString(
                java.nio.file.Files.readString(PREFS_FILE)).getAsJsonObject();
            if (j.has("loader"))   loaderCombo.setValue(j.get("loader").getAsString());
            if (j.has("ram"))      ramSlider.setValue(j.get("ram").getAsDouble());
            if (j.has("username")) userField.setText(j.get("username").getAsString());
            if (j.has("version")) {
                String savedVer = j.get("version").getAsString();
                versionCombo.getItems().addListener(
                    (javafx.collections.ListChangeListener<String>) c -> {
                        if (versionCombo.getItems().contains(savedVer))
                            Platform.runLater(() -> versionCombo.setValue(savedVer));
                    });
            }
        } catch (Exception ignored) {}
    }

    private void styleCombo(ComboBox<String> cb) {
        cb.setStyle(
            "-fx-background-color:#6c63ff44;-fx-text-fill:#ffffff;" +
            "-fx-border-color:#3a3a60;-fx-border-radius:8;" +
            "-fx-background-radius:8;-fx-font-size:13px;-fx-padding:6 10;");
    }

    public static Process getMinecraftProcess() { return minecraftProcess; }

    public void setRunningState(boolean running) {
        if (running) {
            stylePlayBtn(actionBtn, true);
            actionBtn.setOnAction(ev -> {
                if (minecraftProcess != null && minecraftProcess.isAlive())
                    minecraftProcess.destroyForcibly();
            });
            // Ana sayfa kartlarını güncelle
            if (MainWindow.instance != null)
                MainWindow.instance.setPlayingState(true);
        }
    }

    private String findJava() {
        String javaHome = System.getenv("JAVA_HOME");
        if (javaHome != null && !javaHome.isEmpty()) {
            java.io.File f = new java.io.File(javaHome,
                System.getProperty("os.name").toLowerCase().contains("win")
                    ? "bin/javaw.exe" : "bin/java");
            if (f.exists()) return f.getAbsolutePath();
        }
        String javaHomeJVM = System.getProperty("java.home");
        if (javaHomeJVM != null) {
            java.io.File f = new java.io.File(javaHomeJVM,
                System.getProperty("os.name").toLowerCase().contains("win")
                    ? "bin/javaw.exe" : "bin/java");
            if (f.exists()) return f.getAbsolutePath();
        }
        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            try {
                Process p = Runtime.getRuntime().exec(new String[]{"where", "javaw"});
                p.waitFor(3, java.util.concurrent.TimeUnit.SECONDS);
                String out = new String(p.getInputStream().readAllBytes()).trim();
                if (!out.isEmpty()) return out.split("\n")[0].trim();
            } catch (Exception ignored) {}
        }
        if (System.getProperty("os.name").toLowerCase().contains("mac")) {
            try {
                Process p = Runtime.getRuntime().exec(
                    new String[]{"/usr/libexec/java_home", "-v", "21"});
                p.waitFor(3, java.util.concurrent.TimeUnit.SECONDS);
                String out = new String(p.getInputStream().readAllBytes()).trim();
                if (!out.isEmpty()) {
                    java.io.File f = new java.io.File(out, "bin/java");
                    if (f.exists()) return f.getAbsolutePath();
                }
            } catch (Exception ignored) {}
        }
        return "java";
    }
}
