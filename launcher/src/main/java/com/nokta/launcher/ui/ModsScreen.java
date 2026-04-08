package com.nokta.launcher.ui;

import com.nokta.launcher.mods.ModrinthAPI;
import com.nokta.launcher.mods.CurseForgeAPI;
import com.nokta.launcher.utils.PathManager;
import javafx.application.Platform;
import javafx.geometry.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.*;
import javafx.scene.image.*;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.shape.Circle;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

public class ModsScreen extends VBox {

    private final ModrinthAPI   modrinth   = new ModrinthAPI();
    private final CurseForgeAPI curseforge = new CurseForgeAPI();

    private TextField        searchField;
    private ComboBox<String> versionFilter;
    private ComboBox<String> loaderFilter;
    private Label            statusLabel;
    private VBox             modListBox;

    // Sekme butonları
    private Button tabSearch, tabInstalled;
    private StackPane contentArea;
    private final ConcurrentHashMap<String,Image> iconCache = new ConcurrentHashMap<>();
    private VBox installedModList;
    private Label installedModCount;

    private static final String SRC_MODRINTH   = "Modrinth";
    private static final String SRC_CURSEFORGE = "CurseForge";
    private static final String SRC_BOTH       = "Her İkisi";

    public ModsScreen() {
        setSpacing(0);
        setPadding(new Insets(32, 40, 32, 40));
        setStyle("-fx-background-color:transparent;");
        buildUI();
    }

    private void buildUI() {
        Label title = new Label("🧩  Mod Yöneticisi");
        title.setStyle("-fx-text-fill:#ffffff;-fx-font-size:24px;-fx-font-weight:bold;");
        Label sub = new Label("Modları ara, indir ve yüklü modları yönet.");
        sub.setStyle("-fx-text-fill:#666688;-fx-font-size:13px;");
        VBox.setMargin(sub, new Insets(4, 0, 20, 0));

        // Ana sekmeler
        HBox mainTabs = new HBox(8);
        mainTabs.setPadding(new Insets(0, 0, 20, 0));
        tabSearch    = buildMainTab("🔍  Mod Ara",       true);
        tabInstalled = buildMainTab("📦  Yüklü Modlar",  false);
        mainTabs.getChildren().addAll(tabSearch, tabInstalled);

        tabSearch.setOnAction(e -> {
            setMainTab(tabSearch, tabInstalled);
            contentArea.getChildren().setAll(buildSearchPane());
        });
        tabInstalled.setOnAction(e -> {
            setMainTab(tabInstalled, tabSearch);
            contentArea.getChildren().setAll(buildInstalledPane());
        });

        contentArea = new StackPane();
        contentArea.getChildren().add(buildSearchPane());

        getChildren().addAll(title, sub, mainTabs, contentArea);
    }

    // ── ARAMA PANELİ ────────────────────────────────────────────────
    private VBox buildSearchPane() {
        VBox pane = new VBox(14);

        HBox sourceTabs = new HBox(8);
        Button mrBtn  = buildSourceTab("🟢 Modrinth",   "#10b981", true);
        Button cfBtn  = buildSourceTab("🔶 CurseForge", "#f59e0b", false);
        Button allBtn = buildSourceTab("🌐 Her İkisi",  "#6c63ff", false);
        sourceTabs.getChildren().addAll(mrBtn, cfBtn, allBtn);

        mrBtn.setOnAction(e  -> { applySourceStyle(mrBtn,"#10b981"); applySourceStyle(cfBtn,null); applySourceStyle(allBtn,null); doSearch(SRC_MODRINTH); });
        cfBtn.setOnAction(e  -> { applySourceStyle(cfBtn,"#f59e0b"); applySourceStyle(mrBtn,null); applySourceStyle(allBtn,null); doSearch(SRC_CURSEFORGE); });
        allBtn.setOnAction(e -> { applySourceStyle(allBtn,"#6c63ff"); applySourceStyle(mrBtn,null); applySourceStyle(cfBtn,null); doSearch(SRC_BOTH); });

        HBox searchBar = new HBox(10);
        searchBar.setAlignment(Pos.CENTER_LEFT);

        searchField = new TextField();
        searchField.setPromptText("🔍  Mod ara... (Sodium, JEI, Waystones...)");
        searchField.setStyle(
            "-fx-background-color:#ffffff11;-fx-text-fill:#ffffff;" +
            "-fx-border-color:#3a3a60;-fx-border-radius:10;" +
            "-fx-background-radius:10;-fx-font-size:14px;-fx-padding:12 16;");
        HBox.setHgrow(searchField, Priority.ALWAYS);

        versionFilter = new ComboBox<>();
        versionFilter.getItems().addAll("1.21.4","1.21.1","1.20.1","1.19.4","1.18.2","1.16.5","1.12.2");
        versionFilter.setValue("1.21.4");
        styleCombo(versionFilter, 110);

        loaderFilter = new ComboBox<>();
        loaderFilter.getItems().addAll("fabric","forge","quilt","neoforge");
        loaderFilter.setValue("fabric");
        styleCombo(loaderFilter, 110);

        Button searchBtn = new Button("Ara");
        searchBtn.setStyle(
            "-fx-background-color:linear-gradient(to right,#6c63ff,#3b82f6);" +
            "-fx-text-fill:white;-fx-font-size:13px;-fx-font-weight:bold;" +
            "-fx-background-radius:10;-fx-padding:12 24;-fx-cursor:hand;");
        searchBtn.setOnAction(e -> doSearch(getActiveSource(mrBtn, cfBtn, allBtn)));
        searchField.setOnAction(e -> searchBtn.fire());
        searchBar.getChildren().addAll(searchField, versionFilter, loaderFilter, searchBtn);

        statusLabel = new Label("⏳ Yükleniyor...");
        statusLabel.setStyle("-fx-text-fill:#555577;-fx-font-size:12px;");

        modListBox = new VBox(10);
        pane.getChildren().addAll(sourceTabs, searchBar, statusLabel, modListBox);

        searchMods("featured", "1.21.4", "fabric", SRC_MODRINTH);
        return pane;
    }

    private void doSearch(String source) {
        String q = searchField != null && !searchField.getText().trim().isEmpty()
            ? searchField.getText().trim() : "featured";
        String ver    = versionFilter != null ? versionFilter.getValue() : "1.21.4";
        String loader = loaderFilter  != null ? loaderFilter.getValue()  : "fabric";
        searchMods(q, ver, loader, source);
    }

    // ── YÜKLÜ MODLAR PANELİ ─────────────────────────────────────────
    private VBox buildInstalledPane() {
        VBox pane = new VBox(14);

        HBox topRow = new HBox(10);
        topRow.setAlignment(Pos.CENTER_LEFT);
        Label vLbl = new Label("MC Sürümü:");
        vLbl.setStyle("-fx-text-fill:#888899;-fx-font-size:13px;");

        ComboBox<String> versionCb = new ComboBox<>();
        styleCombo(versionCb, 120);
        try {
            Path modsRoot = PathManager.getGameDir().resolve("mods");
            if (Files.exists(modsRoot)) {
                Files.list(modsRoot)
                    .filter(Files::isDirectory)
                    .map(p -> p.getFileName().toString())
                    .sorted(Comparator.reverseOrder())
                    .forEach(v -> versionCb.getItems().add(v));
            }
        } catch (Exception ignored) {}
        if (!versionCb.getItems().isEmpty()) versionCb.setValue(versionCb.getItems().get(0));
        else versionCb.getItems().add("1.21.4");

        installedModCount = new Label();
        installedModCount.setStyle("-fx-text-fill:#444466;-fx-font-size:12px;");
        HBox spacer = new HBox(); HBox.setHgrow(spacer, Priority.ALWAYS);

        Button enableAll  = buildSmallBtn("✅ Hepsini Etkinleştir", "#44cc66");
        Button disableAll = buildSmallBtn("⛔ Hepsini Devre Dışı",  "#ff6644");
        topRow.getChildren().addAll(vLbl, versionCb, spacer, enableAll, disableAll, installedModCount);

        ScrollPane scroll = new ScrollPane();
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background:transparent;-fx-background-color:transparent;-fx-border-color:transparent;");
        scroll.setFitToHeight(false);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setPrefHeight(500);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        installedModList = new VBox(8);
        installedModList.setPadding(new Insets(0, 0, 20, 0));
        scroll.setContent(installedModList);

        versionCb.setOnAction(e -> loadInstalledMods(versionCb.getValue()));

        enableAll.setOnAction(e -> {
            String ver = versionCb.getValue(); if (ver == null) return;
            Path dir = PathManager.getGameDir().resolve("mods").resolve(ver);
            new Thread(() -> {
                try { Files.list(dir)
                    .filter(p -> p.getFileName().toString().endsWith(".jar.disabled"))
                    .forEach(p -> { try {
                        String n = p.getFileName().toString();
                        Files.move(p, p.resolveSibling(n.replace(".jar.disabled",".jar")));
                    } catch (Exception ignored) {} }); } catch (Exception ignored) {}
                Platform.runLater(() -> loadInstalledMods(ver));
            }).start();
        });

        disableAll.setOnAction(e -> {
            String ver = versionCb.getValue(); if (ver == null) return;
            Path dir = PathManager.getGameDir().resolve("mods").resolve(ver);
            new Thread(() -> {
                try { Files.list(dir)
                    .filter(p -> p.getFileName().toString().endsWith(".jar"))
                    .forEach(p -> { try {
                        Files.move(p, p.resolveSibling(p.getFileName()+".disabled"));
                    } catch (Exception ignored) {} }); } catch (Exception ignored) {}
                Platform.runLater(() -> loadInstalledMods(ver));
            }).start();
        });

        // İlk yükleme
        if (versionCb.getValue() != null)
            new Thread(() -> Platform.runLater(() -> loadInstalledMods(versionCb.getValue()))).start();

        pane.getChildren().addAll(topRow, scroll);
        return pane;
    }

    private void loadInstalledMods(String ver) {
        if (installedModList == null || ver == null) return;
        installedModList.getChildren().clear();
        Path modsDir = PathManager.getGameDir().resolve("mods").resolve(ver);
        new Thread(() -> {
            try {
                if (!Files.exists(modsDir)) {
                    Platform.runLater(() -> {
                        Label e = new Label("Bu sürüm için mod klasörü bulunamadı.");
                        e.setStyle("-fx-text-fill:#444466;-fx-font-size:13px;-fx-padding:20;");
                        installedModList.getChildren().add(e);
                        if (installedModCount != null) installedModCount.setText("0 mod");
                    }); return;
                }
                List<Path> jars = Files.list(modsDir)
                    .filter(p -> { String n = p.getFileName().toString();
                        return (n.endsWith(".jar") || n.endsWith(".jar.disabled"))
                            && !n.startsWith("nokta-overlay"); })
                    .sorted(Comparator.comparing(p -> p.getFileName().toString().toLowerCase()))
                    .collect(java.util.stream.Collectors.toList());
                Platform.runLater(() -> {
                    if (installedModCount != null) installedModCount.setText(jars.size() + " mod");
                    if (jars.isEmpty()) {
                        Label e = new Label("Yüklü mod bulunamadı.");
                        e.setStyle("-fx-text-fill:#444466;-fx-font-size:13px;-fx-padding:20;");
                        installedModList.getChildren().add(e); return;
                    }
                    for (Path jar : jars)
                        installedModList.getChildren().add(buildInstalledModCard(jar, ver));
                });
            } catch (Exception e) {
                Platform.runLater(() -> { if (installedModCount != null) installedModCount.setText("Hata!"); });
            }
        }, "load-mods").start();
    }

    // ── Yüklü mod kartı
    // ── Yüklü mod kartı ─────────────────────────────────────────────
    private HBox buildInstalledModCard(Path jarPath, String ver) {
        String fileName = jarPath.getFileName().toString();
        boolean enabled = fileName.endsWith(".jar");
        String modName  = fileName
            .replace(".jar.disabled", "")
            .replace(".jar", "");

        HBox card = new HBox(14);
        card.setPadding(new Insets(14, 18, 14, 18));
        card.setAlignment(Pos.CENTER_LEFT);
        card.setStyle(
            "-fx-background-color:" + (enabled ? "#13131e" : "#0e0e18") + ";" +
            "-fx-background-radius:12;" +
            "-fx-border-color:" + (enabled ? "#6c63ff44" : "#33333344") + ";" +
            "-fx-border-radius:12;-fx-border-width:1;");

        // İkon - JAR içinden oku veya renkli harf göster
        int hash = Math.abs(modName.hashCode());
        String[] colors = {"#6c63ff","#10b981","#f59e0b","#3b82f6","#ec4899","#14b8a6","#f97316","#8b5cf6"};
        String iconColor = enabled ? colors[hash % colors.length] : "#444455";
        StackPane icon = new StackPane();
        icon.setMinSize(48, 48); icon.setMaxSize(48, 48);
        Circle iconBg = new Circle(24);
        iconBg.setFill(Color.web(iconColor + "33"));
        iconBg.setStroke(Color.web(iconColor + (enabled ? "99" : "44")));
        iconBg.setStrokeWidth(1.5);
        String firstLetter = modName.isEmpty() ? "M" : modName.substring(0,1).toUpperCase();
        Label iconLbl = new Label(firstLetter);
        iconLbl.setStyle("-fx-text-fill:" + iconColor + ";-fx-font-size:15px;-fx-font-weight:bold;");
        icon.getChildren().addAll(iconBg, iconLbl);

        // JAR içinden ikon yükle
        new Thread(() -> {
            try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(jarPath.toFile())) {
                // fabric.mod.json oku
                java.util.zip.ZipEntry metaEntry = zip.getEntry("fabric.mod.json");
                if (metaEntry == null) metaEntry = zip.getEntry("META-INF/mods.toml");
                String iconPath = null;
                if (metaEntry != null) {
                    String meta = new String(zip.getInputStream(metaEntry).readAllBytes());
                    // icon field'ı bul
                    java.util.regex.Matcher m = java.util.regex.Pattern
                        .compile("\"icon\"\\s*:\\s*\"([^\"]+)\"").matcher(meta);
                    if (m.find()) iconPath = m.group(1);
                }
                if (iconPath == null) iconPath = "pack.png"; // fallback
                java.util.zip.ZipEntry imgEntry = zip.getEntry(iconPath);
                if (imgEntry == null) return;
                byte[] imgBytes = zip.getInputStream(imgEntry).readAllBytes();
                BufferedImage bi = ImageIO.read(new java.io.ByteArrayInputStream(imgBytes));
                if (bi == null) return;
                Image img = SwingFXUtils.toFXImage(bi, null);
                javafx.application.Platform.runLater(() -> {
                    ImageView iv = new ImageView(img);
                    iv.setFitWidth(48); iv.setFitHeight(48);
                    iv.setPreserveRatio(false);
                    javafx.scene.shape.Circle clip = new javafx.scene.shape.Circle(24, 24, 24);
                    iv.setClip(clip);
                    icon.getChildren().setAll(iv);
                });
            } catch (Exception ignored) {}
        }, "jar-icon").start();

        // İsim ve dosya
        VBox info = new VBox(3);
        HBox.setHgrow(info, Priority.ALWAYS);
        Label nameLbl = new Label(formatModName(modName));
        nameLbl.setStyle("-fx-text-fill:" + (enabled ? "#ddddff" : "#555566") +
            ";-fx-font-size:13px;-fx-font-weight:bold;");
        Label fileLbl = new Label(fileName);
        fileLbl.setStyle("-fx-text-fill:#333355;-fx-font-size:10px;-fx-font-family:monospace;");
        
        // Dosya boyutu
        String sizeStr = "";
        try {
            long size = Files.size(jarPath);
            sizeStr = size > 1024*1024
                ? String.format("%.1f MB", size/1024.0/1024.0)
                : String.format("%.0f KB", size/1024.0);
        } catch (Exception ignored) {}
        Label sizeLbl = new Label(sizeStr);
        sizeLbl.setStyle("-fx-text-fill:#333355;-fx-font-size:10px;");
        
        info.getChildren().addAll(nameLbl, fileLbl);

        // Sağ taraf butonlar
        HBox btns = new HBox(8);
        btns.setAlignment(Pos.CENTER);

        // Toggle butonu
        ToggleButton toggle = new ToggleButton(enabled ? "Aktif" : "Devre Dışı");
        toggle.setSelected(enabled);
        String onStyle  = "-fx-background-color:#6c63ff22;-fx-text-fill:#a78bfa;" +
            "-fx-border-color:#6c63ff55;-fx-border-radius:8;-fx-background-radius:8;" +
            "-fx-padding:6 14;-fx-cursor:hand;-fx-font-size:12px;";
        String offStyle = "-fx-background-color:#ff444422;-fx-text-fill:#ff6644;" +
            "-fx-border-color:#ff664433;-fx-border-radius:8;-fx-background-radius:8;" +
            "-fx-padding:6 14;-fx-cursor:hand;-fx-font-size:12px;";
        toggle.setStyle(enabled ? onStyle : offStyle);

        toggle.setOnAction(e -> {
            toggle.setDisable(true);
            new Thread(() -> {
                try {
                    String name = jarPath.getFileName().toString();
                    if (name.endsWith(".jar")) {
                        // Devre dışı bırak
                        Files.move(jarPath, jarPath.resolveSibling(name + ".disabled"),
                            StandardCopyOption.REPLACE_EXISTING);
                    } else {
                        // Etkinleştir
                        String newName = name.replace(".jar.disabled", ".jar");
                        Files.move(jarPath, jarPath.resolveSibling(newName),
                            StandardCopyOption.REPLACE_EXISTING);
                    }
                    Platform.runLater(() -> loadInstalledMods(ver));
                } catch (Exception ex) {
                    Platform.runLater(() -> {
                        toggle.setDisable(false);
                        toggle.setText("Hata!");
                    });
                }
            }).start();
        });

        toggle.selectedProperty().addListener((obs, o, n) ->
            toggle.setStyle(n ? onStyle : offStyle));

        // Sil butonu
        Button delBtn = new Button("🗑");
        delBtn.setStyle(
            "-fx-background-color:#ff222222;-fx-text-fill:#ff4444;" +
            "-fx-border-color:#ff444433;-fx-border-radius:8;-fx-background-radius:8;" +
            "-fx-padding:6 10;-fx-cursor:hand;-fx-font-size:13px;");
        delBtn.setOnMouseEntered(ev -> delBtn.setStyle(
            "-fx-background-color:#3a1010;-fx-text-fill:#ff6666;" +
            "-fx-border-color:#ff444466;-fx-border-radius:8;-fx-background-radius:8;" +
            "-fx-padding:6 10;-fx-cursor:hand;-fx-font-size:13px;"));
        delBtn.setOnMouseExited(ev -> delBtn.setStyle(
            "-fx-background-color:#ff222222;-fx-text-fill:#ff4444;" +
            "-fx-border-color:#ff444433;-fx-border-radius:8;-fx-background-radius:8;" +
            "-fx-padding:6 10;-fx-cursor:hand;-fx-font-size:13px;"));
        delBtn.setOnAction(e -> {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Modu Sil");
            alert.setHeaderText(null);
            alert.setContentText("\"" + formatModName(modName) + "\" silinsin mi?");
            alert.showAndWait().ifPresent(btn -> {
                if (btn == ButtonType.OK) {
                    try {
                        Files.deleteIfExists(jarPath);
                        Platform.runLater(() -> loadInstalledMods(ver));
                    } catch (Exception ex) {
                        System.out.println("⚠ Silinemedi: " + ex.getMessage());
                    }
                }
            });
        });

        btns.getChildren().addAll(sizeLbl, toggle, delBtn);
        card.getChildren().addAll(icon, info, btns);
        return card;
    }

    private String formatModName(String raw) {
        // "sodium-fabric-0.6.0+mc1.21.4" -> "Sodium Fabric 0.6.0"
        String clean = raw.replaceAll("[_-]", " ")
            .replaceAll("\\+mc[0-9.]+", "")
            .replaceAll("\\+[0-9.]+", "");
        // Büyük harf
        String[] words = clean.split(" ");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (!w.isEmpty()) {
                sb.append(Character.toUpperCase(w.charAt(0)));
                if (w.length() > 1) sb.append(w.substring(1));
                sb.append(" ");
            }
        }
        return sb.toString().trim();
    }

    // ── Arama fonksiyonu ────────────────────────────────────────────
    private void searchMods(String query, String version, String loader, String source) {
        if (modListBox == null) return;
        modListBox.getChildren().clear();
        statusLabel.setText("⏳ Aranıyor: \"" + query + "\"");

        new Thread(() -> {
            try {
                if (source.equals(SRC_MODRINTH) || source.equals(SRC_BOTH)) {
                    List<ModrinthAPI.Mod> mods = modrinth.search(query, version, loader, 15);
                    Platform.runLater(() -> {
                        for (ModrinthAPI.Mod m : mods)
                            modListBox.getChildren().add(buildModrinthCard(m, version, loader));
                        statusLabel.setText("✅ " + mods.size() + " mod bulundu — Modrinth");
                    });
                }
                if (source.equals(SRC_CURSEFORGE) || source.equals(SRC_BOTH)) {
                    List<CurseForgeAPI.CFMod> mods = curseforge.search(query, version, loader, 15);
                    Platform.runLater(() -> {
                        for (CurseForgeAPI.CFMod m : mods)
                            modListBox.getChildren().add(buildCurseForgeCard(m, version, loader));
                        statusLabel.setText("✅ " + mods.size() + " mod bulundu — CurseForge");
                    });
                }
            } catch (Exception ex) {
                Platform.runLater(() -> statusLabel.setText("❌ Hata: " + ex.getMessage()));
            }
        }, "mod-search").start();
    }

    // ── Modrinth Kart ───────────────────────────────────────────────
    private HBox buildModrinthCard(ModrinthAPI.Mod mod, String mcVersion, String loader) {
        HBox card = buildBaseCard("#10b981");
        StackPane iconBox = buildIconWithUrl(mod.title, "#10b981", mod.iconUrl);
        VBox info = new VBox(5);
        HBox.setHgrow(info, Priority.ALWAYS);
        HBox titleRow = new HBox(8);
        titleRow.setAlignment(Pos.CENTER_LEFT);
        Label titleLbl = new Label(mod.title);
        titleLbl.setStyle("-fx-text-fill:#ffffff;-fx-font-size:14px;-fx-font-weight:bold;");
        Label badge = new Label("  Modrinth  ");
        badge.setStyle("-fx-background-color:#10b98122;-fx-text-fill:#34d399;" +
            "-fx-background-radius:4;-fx-font-size:10px;-fx-padding:2 6;");
        titleRow.getChildren().addAll(titleLbl, badge);
        Label descLbl = new Label(mod.description.length() > 120
            ? mod.description.substring(0, 120) + "..." : mod.description);
        descLbl.setStyle("-fx-text-fill:#555577;-fx-font-size:12px;");
        descLbl.setWrapText(true);
        HBox stats = new HBox(16);
        stats.getChildren().addAll(statLabel("⬇ " + formatNum(mod.downloads)),
            statLabel("❤ " + formatNum(mod.follows)));
        info.getChildren().addAll(titleRow, descLbl, stats);
        VBox btnBox = buildInstallBtn("#10b981", box -> {
            new Thread(() -> {
                try {
                    List<ModrinthAPI.ModVersion> versions =
                        modrinth.getVersions(mod.id, mcVersion, loader);
                    if (versions.isEmpty()) return;
                    Path dir = PathManager.getGameDir().resolve("mods").resolve(mcVersion);
                    Files.createDirectories(dir);
                    modrinth.downloadMod(versions.get(0), dir, new ModrinthAPI.DownloadCallback() {
                        public void progress(int p) {}
                        public void done(Path f) { Platform.runLater(() -> markInstalled(box)); }
                    });
                } catch (Exception ex) {
                    Platform.runLater(() -> ((Label)box.getChildren().get(2)).setText("❌ Hata"));
                }
            }, "install-mr").start();
        });
        card.getChildren().addAll(iconBox, info, btnBox);
        return card;
    }

    // ── CurseForge Kart ─────────────────────────────────────────────
    private HBox buildCurseForgeCard(CurseForgeAPI.CFMod mod, String mcVersion, String loader) {
        HBox card = buildBaseCard("#f59e0b");
        StackPane iconBox = buildIconWithUrl(mod.name, "#f59e0b", mod.iconUrl);
        VBox info = new VBox(5);
        HBox.setHgrow(info, Priority.ALWAYS);
        HBox titleRow = new HBox(8);
        titleRow.setAlignment(Pos.CENTER_LEFT);
        Label titleLbl = new Label(mod.name);
        titleLbl.setStyle("-fx-text-fill:#ffffff;-fx-font-size:14px;-fx-font-weight:bold;");
        Label badge = new Label("  CurseForge  ");
        badge.setStyle("-fx-background-color:#f59e0b22;-fx-text-fill:#fbbf24;" +
            "-fx-background-radius:4;-fx-font-size:10px;-fx-padding:2 6;");
        titleRow.getChildren().addAll(titleLbl, badge);
        Label descLbl = new Label(mod.description.length() > 120
            ? mod.description.substring(0, 120) + "..." : mod.description);
        descLbl.setStyle("-fx-text-fill:#555577;-fx-font-size:12px;");
        descLbl.setWrapText(true);
        HBox stats = new HBox(16);
        stats.getChildren().addAll(statLabel("⬇ " + formatNum(mod.downloads)),
            statLabel("👍 " + formatNum(mod.likes)));
        info.getChildren().addAll(titleRow, descLbl, stats);
        VBox btnBox = buildInstallBtn("#f59e0b", box -> {
            new Thread(() -> {
                try {
                    List<CurseForgeAPI.CFFile> files =
                        curseforge.getFiles(mod.id, mcVersion, loader);
                    if (files.isEmpty()) return;
                    Path dir = PathManager.getGameDir().resolve("mods").resolve(mcVersion);
                    Files.createDirectories(dir);
                    curseforge.downloadMod(files.get(0), dir, new CurseForgeAPI.DownloadCallback() {
                        public void progress(int p) {}
                        public void done(Path f) { Platform.runLater(() -> markInstalled(box)); }
                    });
                } catch (Exception ex) {
                    Platform.runLater(() -> ((Label)box.getChildren().get(2)).setText("❌ Hata"));
                }
            }, "install-cf").start();
        });
        card.getChildren().addAll(iconBox, info, btnBox);
        return card;
    }

    // ── Yardımcılar ─────────────────────────────────────────────────
    private Button buildMainTab(String text, boolean active) {
        Button b = new Button(text);
        String on  = "-fx-background-color:#6c63ff22;-fx-text-fill:#a78bfa;" +
            "-fx-border-color:#6c63ff55;-fx-border-radius:10;-fx-background-radius:10;" +
            "-fx-padding:10 20;-fx-cursor:hand;-fx-font-size:13px;-fx-font-weight:bold;";
        String off = "-fx-background-color:#13131e;-fx-text-fill:#555577;" +
            "-fx-border-color:#1e1e30;-fx-border-radius:10;-fx-background-radius:10;" +
            "-fx-padding:10 20;-fx-cursor:hand;-fx-font-size:13px;";
        b.setStyle(active ? on : off);
        b.setUserData(active ? "active" : null);
        b.setOnMouseEntered(e -> { if (!"active".equals(b.getUserData()))
            b.setStyle("-fx-background-color:#1a1a2e;-fx-text-fill:#aaaacc;" +
                "-fx-border-color:#2a2a40;-fx-border-radius:10;-fx-background-radius:10;" +
                "-fx-padding:10 20;-fx-cursor:hand;-fx-font-size:13px;"); });
        b.setOnMouseExited(e -> { if (!"active".equals(b.getUserData())) b.setStyle(off); });
        return b;
    }

    private void setMainTab(Button active, Button inactive) {
        String on  = "-fx-background-color:#6c63ff22;-fx-text-fill:#a78bfa;" +
            "-fx-border-color:#6c63ff55;-fx-border-radius:10;-fx-background-radius:10;" +
            "-fx-padding:10 20;-fx-cursor:hand;-fx-font-size:13px;-fx-font-weight:bold;";
        String off = "-fx-background-color:#13131e;-fx-text-fill:#555577;" +
            "-fx-border-color:#1e1e30;-fx-border-radius:10;-fx-background-radius:10;" +
            "-fx-padding:10 20;-fx-cursor:hand;-fx-font-size:13px;";
        active.setStyle(on);   active.setUserData("active");
        inactive.setStyle(off); inactive.setUserData(null);
    }

    private Button buildSmallBtn(String text, String color) {
        Button b = new Button(text);
        b.setStyle("-fx-background-color:" + color + "22;-fx-text-fill:" + color + ";" +
            "-fx-border-color:" + color + "44;-fx-border-radius:8;-fx-background-radius:8;" +
            "-fx-padding:6 12;-fx-cursor:hand;-fx-font-size:11px;");
        return b;
    }

    private HBox buildBaseCard(String accent) {
        HBox card = new HBox(16);
        card.setPadding(new Insets(16, 20, 16, 20));
        card.setAlignment(Pos.CENTER_LEFT);
        String base  = "-fx-background-color:#13131e;-fx-background-radius:12;" +
            "-fx-border-color:#1e1e30;-fx-border-radius:12;-fx-border-width:1;";
        String hover = "-fx-background-color:#1a1a28;-fx-background-radius:12;" +
            "-fx-border-color:" + accent + "55;-fx-border-radius:12;-fx-border-width:1;";
        card.setStyle(base);
        card.setOnMouseEntered(e -> card.setStyle(hover));
        card.setOnMouseExited(e  -> card.setStyle(base));
        return card;
    }

    private StackPane buildIcon(String name, String color) {
        return buildIconWithUrl(name, color, null);
    }

    private StackPane buildIconWithUrl(String name, String color, String iconUrl) {
        StackPane box = new StackPane();
        box.setMinSize(48, 48); box.setMaxSize(48, 48);

        // Arka plan circle
        Circle bg = new Circle(24);
        bg.setFill(Color.web(color + "33"));
        bg.setStroke(Color.web(color + "66"));
        bg.setStrokeWidth(1.5);
        box.getChildren().add(bg);

        if (iconUrl != null && !iconUrl.isEmpty()) {
            new Thread(() -> {
                try {
                    okhttp3.OkHttpClient client = new okhttp3.OkHttpClient();
                    okhttp3.Request req = new okhttp3.Request.Builder().url(iconUrl).build();
                    byte[] bytes;
                    try (okhttp3.Response resp = client.newCall(req).execute()) {
                        if (!resp.isSuccessful() || resp.body() == null) return;
                        bytes = resp.body().bytes();
                    }
                    // WebP dahil tüm formatlar için ImageIO kullan
                    BufferedImage bi = ImageIO.read(new java.io.ByteArrayInputStream(bytes));
                    if (bi == null) return;
                    Image img = SwingFXUtils.toFXImage(bi, null);
                    javafx.application.Platform.runLater(() -> {
                        ImageView iv = new ImageView(img);
                        iv.setFitWidth(48); iv.setFitHeight(48);
                        iv.setPreserveRatio(false);
                        javafx.scene.shape.Circle clip = new javafx.scene.shape.Circle(24, 24, 24);
                        iv.setClip(clip);
                        box.getChildren().setAll(iv);
                    });
                } catch (Exception ignored) {}
            }, "icon-" + name).start();
        } else {
            Label lbl = new Label(name.isEmpty() ? "?" : name.substring(0,1).toUpperCase());
            lbl.setStyle("-fx-text-fill:"+color+";-fx-font-size:15px;-fx-font-weight:bold;");
            box.getChildren().add(lbl);
        }
        return box;
    }

    private VBox buildInstallBtn(String color, java.util.function.Consumer<VBox> action) {
        VBox box = new VBox(5);
        box.setAlignment(Pos.CENTER);
        Button btn = new Button("⬇  Kur");
        btn.setPrefWidth(90);
        btn.setStyle("-fx-background-color:" + color + "22;-fx-text-fill:" + color + ";" +
            "-fx-background-radius:8;-fx-padding:8 12;" +
            "-fx-border-color:" + color + "55;-fx-border-radius:8;-fx-cursor:hand;-fx-font-size:12px;");
        ProgressBar pb = new ProgressBar(0);
        pb.setPrefWidth(90); pb.setPrefHeight(4);
        pb.setStyle("-fx-accent:" + color + ";-fx-background-color:#252540;-fx-background-radius:2;");
        pb.setVisible(false);
        Label status = new Label("");
        status.setStyle("-fx-text-fill:#555577;-fx-font-size:10px;");
        status.setPrefWidth(90); status.setWrapText(true);
        btn.setOnAction(e -> { btn.setDisable(true); btn.setText("⏳"); pb.setVisible(true); action.accept(box); });
        box.getChildren().addAll(btn, pb, status);
        return box;
    }

    private void markInstalled(VBox box) {
        Button btn = (Button) box.getChildren().get(0);
        ProgressBar pb = (ProgressBar) box.getChildren().get(1);
        btn.setText("✅ Kuruldu");
        pb.setProgress(1.0);
    }

    private Button buildSourceTab(String text, String color, boolean active) {
        Button btn = new Button(text);
        btn.setStyle("-fx-background-color:" + (active ? color+"22" : "#1a1a2e") + ";" +
            "-fx-text-fill:" + (active ? color : "#666688") + ";" +
            "-fx-background-radius:8;-fx-padding:8 16;-fx-cursor:hand;-fx-font-size:13px;" +
            (active ? "-fx-border-color:"+color+"55;-fx-border-radius:8;" : ""));
        if (active) btn.setUserData("active");
        return btn;
    }

    private void setSourceActive(Button active, Button... others) {
        active.setUserData("active");
        for (Button b : others) b.setUserData(null);
    }

    private void applySourceStyle(Button btn, String color) {
        if (color != null) {
            btn.setStyle(
                "-fx-background-color:" + color + "22;" +
                "-fx-text-fill:" + color + ";" +
                "-fx-border-color:" + color + "88;" +
                "-fx-border-radius:8;-fx-background-radius:8;" +
                "-fx-padding:8 16;-fx-cursor:hand;-fx-font-size:13px;-fx-font-weight:bold;");
            btn.setUserData("active");
        } else {
            btn.setStyle(
                "-fx-background-color:#1a1a2e;-fx-text-fill:#666688;" +
                "-fx-border-color:transparent;" +
                "-fx-border-radius:8;-fx-background-radius:8;" +
                "-fx-padding:8 16;-fx-cursor:hand;-fx-font-size:13px;");
            btn.setUserData(null);
        }
    }

    private String getActiveSource(Button mr, Button cf, Button all) {
        if ("active".equals(cf.getUserData()))  return SRC_CURSEFORGE;
        if ("active".equals(all.getUserData())) return SRC_BOTH;
        return SRC_MODRINTH;
    }

    private Label statLabel(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-text-fill:#444466;-fx-font-size:11px;");
        return l;
    }

    private void styleCombo(ComboBox<String> cb, int width) {
        cb.setPrefWidth(width);
        cb.setStyle("-fx-background-color:#ffffff11;-fx-text-fill:#ffffff;" +
            "-fx-border-color:#3a3a60;-fx-border-radius:8;-fx-background-radius:8;" +
            "-fx-font-size:12px;-fx-padding:4;");
    }

    private String formatNum(long n) {
        if (n >= 1_000_000) return String.format("%.1fM", n/1_000_000.0);
        if (n >= 1_000)     return String.format("%.1fK", n/1_000.0);
        return String.valueOf(n);
    }
}
