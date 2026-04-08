package com.nokta.launcher.ui;

import com.nokta.launcher.core.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicReference;
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
    private static final java.nio.file.Path PREFS_FILE = PathManager.getGameDir().resolve("play_prefs.json");

    private final VersionManager  versionManager;
    private final GameLauncher    gameLauncher;
    private final NativesManager  nativesManager;
    private final com.nokta.launcher.core.AuthManager    authManager;
    private final com.nokta.launcher.discord.DiscordRPC  discordRPC;
    private static volatile Process minecraftProcess = null;

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            Process p = minecraftProcess;
            if (p != null && p.isAlive()) {
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
    private VBox             chatLogBox;
    private final java.util.List<String> mcLogLines  = new java.util.ArrayList<>();
    private final java.util.List<String> chatLogLines = new java.util.ArrayList<>();
    private TextField        userField;

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

        // Ana ayarlar kartı
        VBox mainCard = new VBox(20);
        mainCard.setPadding(new Insets(28, 32, 28, 32));
        mainCard.setStyle(
            "-fx-background-color:#00000055;-fx-background-radius:16;" +
            "-fx-border-color:#1e1e30;-fx-border-radius:16;-fx-border-width:1;");

        // Sürüm & loader seçimi
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

        // İlerleme kartı
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

        // Log alanı — yan yana: sol=MC logu, sağ=Chat logu
        logBox = new VBox(3);
        logBox.setPadding(new Insets(6));
        logBox.setStyle("-fx-background-color:#00000044;-fx-background-radius:8;");

        chatLogBox = new VBox(3);
        chatLogBox.setPadding(new Insets(6));
        chatLogBox.setStyle("-fx-background-color:#00000033;-fx-background-radius:8;");

        javafx.scene.control.ScrollPane logScroll = new javafx.scene.control.ScrollPane(logBox);
        logScroll.setFitToWidth(true);
        logScroll.setPrefHeight(200);
        HBox.setHgrow(logScroll, Priority.ALWAYS);
        logScroll.setStyle("-fx-background:#00000000;-fx-background-color:transparent;-fx-border-color:#1a1a2888;-fx-border-radius:8;-fx-border-width:1;");

        javafx.scene.control.ScrollPane chatScroll = new javafx.scene.control.ScrollPane(chatLogBox);
        chatScroll.setFitToWidth(true);
        chatScroll.setPrefHeight(200);
        chatScroll.setPrefWidth(320);
        chatScroll.setStyle("-fx-background:#00000000;-fx-background-color:transparent;-fx-border-color:#6c63ff44;-fx-border-radius:8;-fx-border-width:1;");

        Label logTitle  = new Label("📋 Minecraft Logu");
        logTitle.setStyle("-fx-text-fill:#555577;-fx-font-size:11px;");
        Label chatTitle = new Label("💬 Chat Logu");
        chatTitle.setStyle("-fx-text-fill:#6c63ff;-fx-font-size:11px;");

        VBox logPane  = new VBox(4, logTitle,  logScroll);
        VBox chatPane = new VBox(4, chatTitle, chatScroll);
        HBox.setHgrow(logPane, Priority.ALWAYS);

        HBox logTabPane = new HBox(12, logPane, chatPane);
        logTabPane.setVisible(true);

        // Butonlar
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
        actionBtn.setStyle(
            "-fx-background-color:linear-gradient(to right,#6c63ff,#3b82f6);" +
            "-fx-text-fill:white;-fx-font-size:15px;-fx-font-weight:bold;" +
            "-fx-background-radius:12;-fx-cursor:hand;" +
            "-fx-effect:dropshadow(gaussian,#6c63ff88,12,0,0,3);");
        actionBtn.setOnAction(e -> launchGame());

        btnRow.getChildren().addAll(installBtn, actionBtn);
        getChildren().addAll(title, sub, mainCard, progressCard, logTabPane, btnRow);
    }

    private void loadVersionsAsync() {
        new Thread(() -> {
            try {
                List<VersionManager.MCVersion> versions = versionManager.fetchVersionList();
                Platform.runLater(() -> {
                    versionCombo.getItems().clear();
                    for (VersionManager.MCVersion v : versions) {
                        if (v.type.equals("release")) versionCombo.getItems().add(v.id);
                    }
                    if (!versionCombo.getItems().isEmpty())
                        versionCombo.setValue(versionCombo.getItems().get(0));
                });
            } catch (Exception ex) {
                Platform.runLater(() -> versionCombo.setPromptText("İnternet yok"));
            }
        }, "version-loader").start();
    }

    private void installVersion(String versionId) {
        if (versionId == null) return;
        AtomicReference<String> versionRef = new AtomicReference<>(versionId);

        setStatus("⬇  Kuruluyor: " + versionId, "#f59e0b");
        progressBar.setVisible(true);
        logBox.setVisible(true);
        installBtn.setDisable(true);
        actionBtn.setDisable(true);

        AssetDownloader assetDownloader = new AssetDownloader(PathManager.getGameDir());

        new Thread(() -> {
            try {
                // 1) Sürümü indir (0-40%)
                addLog("📦 Sürüm indiriliyor: " + versionRef.get());
                versionManager.downloadVersion(versionRef.get(), (msg, pct) ->
                    Platform.runLater(() -> {
                        progressBar.setProgress(pct / 250.0);
                        progressLabel.setText(msg);
                        if (pct % 10 == 0) addLog(pct + "% " + msg);
                    })
                );
                // 1.5) Loader kurulumu
                String loader = loaderCombo.getValue();
                if ("Forge".equals(loader)) {
                    Platform.runLater(() -> setStatus("⚒ Forge kuruluyor...", "#f59e0b"));
                    addLog("⚒ Forge kuruluyor...");
                    com.nokta.launcher.core.ForgeInstaller fi =
                        new com.nokta.launcher.core.ForgeInstaller(
                            com.nokta.launcher.utils.PathManager.getGameDir());
                    String forgeId = fi.installForge(versionRef.get(), (msg, pct) ->
                        Platform.runLater(() -> {
                            progressLabel.setText(msg);
                            addLog(msg);
                        })
                    );
                    versionRef.set(forgeId);
                } else if ("NeoForge".equals(loader)) {
                    Platform.runLater(() -> setStatus("⚒ NeoForge kuruluyor...", "#f59e0b"));
                    addLog("⚒ NeoForge kuruluyor...");
                    com.nokta.launcher.core.ForgeInstaller fi =
                        new com.nokta.launcher.core.ForgeInstaller(
                            com.nokta.launcher.utils.PathManager.getGameDir());
                    String neoId = fi.installNeoForge(versionRef.get(), (msg, pct) ->
                        Platform.runLater(() -> {
                            progressLabel.setText(msg);
                            addLog(msg);
                        })
                    );
                    versionRef.set(neoId);
                } else if ("Fabric".equals(loader)) {
                    Platform.runLater(() -> setStatus("🧵 Fabric kuruluyor...", "#f59e0b"));
                    addLog("🧵 Fabric Loader kuruluyor...");
                    com.nokta.launcher.core.FabricInstaller fi =
                        new com.nokta.launcher.core.FabricInstaller(com.nokta.launcher.utils.PathManager.getGameDir());
                    fi.install(versionRef.get(), (msg, pct) ->
                        Platform.runLater(() -> {
                            progressLabel.setText(msg);
                            addLog(msg);
                        })
                    );
                }

                // 2) Assets indir (40-80%)
                Platform.runLater(() -> setStatus("🖼  Assets indiriliyor...", "#f59e0b"));
                addLog("🖼 Assets indiriliyor (texture, ses, dil dosyaları)...");
                assetDownloader.downloadAssets(versionRef.get(), (msg, pct) ->
                    Platform.runLater(() -> {
                        progressBar.setProgress(0.4 + pct / 250.0);
                        progressLabel.setText(msg);
                        if (pct % 20 == 0) addLog(pct + "% " + msg);
                    })
                );

                // 3) Natives çıkart (80-100%)
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

    private void launchGame() {
        String versionId = versionCombo.getValue();
        if (versionId == null) { setStatus("❌ Sürüm seçiniz!", "#f04040"); return; }
        if (!versionManager.isInstalled(versionId)) {
            setStatus("⚠ Önce 'Sürümü Kur' butonuna basın!", "#f59e0b"); return;
        }

        // Fabric seçildiyse fabric version id'yi kullan
        String loader = loaderCombo.getValue();
        if ("Fabric".equals(loader)) {
            try {
                com.nokta.launcher.core.FabricInstaller fi =
                    new com.nokta.launcher.core.FabricInstaller(com.nokta.launcher.utils.PathManager.getGameDir());
                String fabricId = fi.install(versionId, (msg, pct) -> {});
                versionId = fabricId; // versionRef scope dışı — intentional
            } catch (Exception e) {
                addLog("⚠ Fabric bulunamadı, Vanilla ile başlatılıyor.");
            }
        }
        // AuthManager'dan hesap bilgilerini al
        com.nokta.launcher.core.AuthManager.Account acc = authManager.getCurrentAccount();
        GameLauncher.LaunchConfig cfg = new GameLauncher.LaunchConfig();
        cfg.versionId    = versionId;
        String savedUsername = userField.getText() != null && !userField.getText().isEmpty()
            ? userField.getText() : "Oyuncu";
        cfg.username     = (acc != null && acc.username != null && !acc.username.isEmpty())
            ? acc.username : savedUsername;
        cfg.uuid         = (acc != null) ? acc.uuid : cfg.uuid;
        cfg.accessToken  = (acc != null) ? acc.accessToken : "0";
        cfg.userType     = (acc != null && acc.microsoft) ? "msa" : "legacy";
        cfg.ramMB        = (int) ramSlider.getValue();
        cfg.javaPath     = findJava();
        cfg.useAikarsFlags = true;

        // Son oynananı kaydet
        savePrefs();

        setStatus("🚀  Başlatılıyor...", "#3b82f6");
        logBox.setVisible(true);
        logBox.getChildren().clear();
        actionBtn.setText("⏹  DURDUR");
        actionBtn.setStyle("-fx-background-color:linear-gradient(to right,#f04040,#e53e3e);"
                    + "-fx-text-fill:white;-fx-font-size:15px;-fx-font-weight:bold;"
                    + "-fx-background-radius:12;-fx-cursor:hand;"
                    + "-fx-effect:dropshadow(gaussian,#f0404088,12,0,0,3);");
        actionBtn.setOnAction(ev -> {
            if (minecraftProcess != null && minecraftProcess.isAlive())
                minecraftProcess.destroyForcibly();
        });
        new Thread(() -> {
            try {
                Process process = gameLauncher.launch(cfg);
                minecraftProcess = process;
                final long startTime = System.currentTimeMillis();
                // Discord RPC: Minecraft oynuyor
                final String finalVersionId = cfg.versionId;
                final String finalLoader = loaderCombo.getValue();
                Platform.runLater(() -> {
                    if (discordRPC != null)
                        discordRPC.setPlayingMinecraft(finalVersionId, finalLoader);
                });
                // Canlı playtime sayacı — her MC açılışında 0'dan başlar
                final long[] sessionStart = {System.currentTimeMillis()};
                java.util.concurrent.ScheduledExecutorService ticker =
                    java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
                ticker.scheduleAtFixedRate(() -> {
                    long elapsed = System.currentTimeMillis() - sessionStart[0];
                    long secs  = (elapsed / 1000) % 60;
                    long mins  = (elapsed / 60000) % 60;
                    long hours = elapsed / 3600000;
                    String t = String.format("%02d:%02d:%02d", hours, mins, secs);
                    Platform.runLater(() -> {
                        if (MainWindow.instance != null)
                            MainWindow.instance.updateSessionPlaytime(t);
                    });
                }, 5, 5, java.util.concurrent.TimeUnit.SECONDS);

                // Log okuyucu — FPS parse + iki log (komut / chat)
                new Thread(() -> {
                    try {
                        var reader = new java.io.BufferedReader(
                            new java.io.InputStreamReader(process.getInputStream()));
                        String line2;
                        while ((line2 = reader.readLine()) != null) {
                            final String l = line2;
                            // FPS parse: "[xx fps]" veya "fps:" içeren satır
                            if (l.contains(" fps") || l.contains("FPS")) {
                                java.util.regex.Matcher m = java.util.regex.Pattern
                                    .compile("(\\d+)\\s*fps", java.util.regex.Pattern.CASE_INSENSITIVE)
                                    .matcher(l);
                                if (m.find()) {
                                    final String fps = m.group(1);
                                    Platform.runLater(() -> {
                                        if (MainWindow.instance != null)
                                            MainWindow.instance.updateFps(fps);
                                    });
                                }
                            }
                            // Chat logu: [CHAT] içeren satırlar
                            if (l.contains("[CHAT]") || l.contains("]: <")) {
                                Platform.runLater(() -> addChatLog(l));
                            } else {
                                Platform.runLater(() -> addLog(l));
                            }
                        }
                    } catch (Exception ignored) {}
                    ticker.shutdownNow();
                }, "mc-log-reader").start();

                // Oyun çalışırken sunucu bilgisini Discord'a yansıt
                Thread discordPolling = new Thread(() -> {
                    java.nio.file.Path serverFile = PathManager.getGameDir().resolve("server_info.json");
                    String lastServer = "";
                    while (process.isAlive()) {
                        try {
                            Thread.sleep(3000);
                            if (java.nio.file.Files.exists(serverFile)) {
                                com.google.gson.JsonObject obj = com.google.gson.JsonParser.parseString(
                                    java.nio.file.Files.readString(serverFile)).getAsJsonObject();
                                String server = obj.has("server") ? obj.get("server").getAsString() : "";
                                if (!server.equals(lastServer)) {
                                    lastServer = server;
                                    final String s = server;
                                    final String loaderName = loaderCombo.getValue();
                                    if (discordRPC != null) {
                                        if (s.isEmpty()) {
                                            discordRPC.setPlayingMinecraft(finalVersionId, loaderName);
                                        } else {
                                            discordRPC.setPresence(
                                                "Nokta Client",
                                                s.equals("Singleplayer") ? "Tek oyunculu" : "☁ " + s + " · MC " + finalVersionId
                                            );
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
                long elapsed = System.currentTimeMillis() - startTime;
                try {
                    java.nio.file.Path ptf = PathManager.getGameDir().resolve("playtime.json");
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
                // API'ye playtime gönder
                final String apiUsername = cfg.username;
                final long apiElapsed = elapsed;
                new Thread(() -> {
                    try {
                        com.google.gson.JsonObject body = new com.google.gson.JsonObject();
                        body.addProperty("username", apiUsername);
                        body.addProperty("ms", apiElapsed);
                        okhttp3.RequestBody reqBody = okhttp3.RequestBody.create(
                            body.toString(), okhttp3.MediaType.get("application/json"));
                        okhttp3.Request req = new okhttp3.Request.Builder()
                            .url(com.nokta.launcher.core.NokTaConfig.API_BASE + "/api/playtime")
                            .header("X-Nokta-Key", com.nokta.launcher.core.NokTaConfig.API_KEY)
                            .post(reqBody)
                            .build();
                        okhttp3.OkHttpClient client = new okhttp3.OkHttpClient();
                        try (okhttp3.Response res = client.newCall(req).execute()) {
                            System.out.println("📊 Playtime API: " + res.code());
                        }
                    } catch (Exception e) {
                        System.out.println("📊 Playtime API hata: " + e.getMessage());
                    }
                }, "playtime-api").start();
                Platform.runLater(() -> {
                    minecraftProcess = null;
                    if (MainWindow.instance != null) MainWindow.instance.showHome();
                    setStatus("⏹  Oyun kapandı (kod: " + exitCode + ")", "#666688");
                    actionBtn.setText("▶  OYNA");
                    actionBtn.setStyle(
                        "-fx-background-color:linear-gradient(to right,#6c63ff,#3b82f6);" +
                        "-fx-text-fill:white;-fx-font-size:15px;-fx-font-weight:bold;" +
                        "-fx-background-radius:12;-fx-cursor:hand;" +
                        "-fx-effect:dropshadow(gaussian,#6c63ff88,12,0,0,3);");
                    actionBtn.setOnAction(ev -> launchGame());
                    actionBtn.setDisable(false);
                    if (exitCode != 0) {
                        // Crash sebebini son log satırlarından bul
                        String crashReason = mcLogLines.stream()
                            .filter(l -> l.contains("ERROR") || l.contains("Exception") || l.contains("FATAL") || l.contains("crash"))
                            .reduce((a, b) -> b).orElse("Bilinmeyen hata");
                        addLog("💥 Minecraft çöktü! Sebep: " + crashReason);
                        Platform.runLater(() -> setStatus("💥 Çökme tespit edildi: " + crashReason.substring(0, Math.min(80, crashReason.length())), "#f04040"));
                    } else {
                        addLog("⏹ Minecraft kapatıldı. Exit: " + exitCode);
                    }
                    if (discordRPC != null) discordRPC.setInLauncher();
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    setStatus("❌  " + ex.getMessage(), "#f04040");
                    actionBtn.setText("▶  OYNA");
                    actionBtn.setStyle(
                        "-fx-background-color:linear-gradient(to right,#6c63ff,#3b82f6);" +
                        "-fx-text-fill:white;-fx-font-size:15px;-fx-font-weight:bold;" +
                        "-fx-background-radius:12;-fx-cursor:hand;" +
                        "-fx-effect:dropshadow(gaussian,#6c63ff88,12,0,0,3);");
                    actionBtn.setOnAction(ev -> launchGame());
                    actionBtn.setDisable(false);
                    addLog("❌ " + ex.getMessage());
                });
            }
        }, "game-launcher").start();
    }

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
        // Otomatik scroll
        javafx.application.Platform.runLater(() -> {
            javafx.scene.Parent p = logBox.getParent();
            if (p instanceof javafx.scene.control.ScrollPane sp) sp.setVvalue(1.0);
        });
    }
    private void addChatLog(String msg) {
        // "[CHAT] <oyuncu> mesaj" formatını temizle
        String clean = msg.replaceAll(".*\\[CHAT\\]\\s*", "").replaceAll(".*\\]: ", "");
        chatLogLines.add(clean);
        Label lbl = new Label(clean);
        lbl.setStyle("-fx-text-fill:#ddddff;-fx-font-size:12px;");
        lbl.setWrapText(true);
        chatLogBox.getChildren().add(lbl);
        javafx.application.Platform.runLater(() -> {
            javafx.scene.Parent p = chatLogBox.getParent();
            if (p instanceof javafx.scene.control.ScrollPane sp) sp.setVvalue(1.0);
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
            // version: loadVersionsAsync bittikten sonra set edilecek
            if (j.has("version")) {
                String savedVer = j.get("version").getAsString();
                versionCombo.getItems().addListener((javafx.collections.ListChangeListener<String>) c -> {
                    if (versionCombo.getItems().contains(savedVer))
                        javafx.application.Platform.runLater(() -> versionCombo.setValue(savedVer));
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
    private String findJava() {
        // 1. JAVA_HOME
        String javaHome = System.getenv("JAVA_HOME");
        if (javaHome != null && !javaHome.isEmpty()) {
            java.io.File f = new java.io.File(javaHome,
                System.getProperty("os.name").toLowerCase().contains("win")
                    ? "bin/javaw.exe" : "bin/java");
            if (f.exists()) return f.getAbsolutePath();
        }
        // 2. java.home (mevcut JVM'nin yolu)
        String javaHomeJVM = System.getProperty("java.home");
        if (javaHomeJVM != null) {
            java.io.File f = new java.io.File(javaHomeJVM,
                System.getProperty("os.name").toLowerCase().contains("win")
                    ? "bin/javaw.exe" : "bin/java");
            if (f.exists()) return f.getAbsolutePath();
        }
        // 3. Windows registry'den
        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            try {
                Process p = Runtime.getRuntime().exec(new String[]{"where", "javaw"});
                p.waitFor(3, java.util.concurrent.TimeUnit.SECONDS);
                String out = new String(p.getInputStream().readAllBytes()).trim();
                if (!out.isEmpty()) return out.split("\n")[0].trim();
            } catch (Exception ignored) {}
        }
        // 4. macOS — /usr/libexec/java_home
        if (System.getProperty("os.name").toLowerCase().contains("mac")) {
            try {
                Process p = Runtime.getRuntime().exec(new String[]{"/usr/libexec/java_home", "-v", "21"});
                p.waitFor(3, java.util.concurrent.TimeUnit.SECONDS);
                String out = new String(p.getInputStream().readAllBytes()).trim();
                if (!out.isEmpty()) {
                    java.io.File f = new java.io.File(out, "bin/java");
                    if (f.exists()) return f.getAbsolutePath();
                }
            } catch (Exception ignored) {}
        }
        // 5. Fallback
        return "java";
    }

}
