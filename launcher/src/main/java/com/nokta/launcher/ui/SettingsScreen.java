package com.nokta.launcher.ui;

import com.nokta.launcher.NoktaLauncher;
import com.nokta.launcher.core.ThemeManager;
import com.nokta.launcher.utils.PathManager;
import javafx.geometry.*;
import javafx.scene.control.*;
import javafx.scene.image.*;
import javafx.scene.layout.*;
import javafx.scene.paint.*;
import javafx.scene.shape.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import java.io.*;import java.nio.file.*;

public class SettingsScreen extends VBox {

    public SettingsScreen() {
        setSpacing(20);
        setPadding(new Insets(32, 40, 32, 40));
        setStyle("-fx-background-color:transparent;");
        buildUI();
    }

    private void buildUI() {
        Label title = new Label("⚙️  Ayarlar");
        title.setStyle("-fx-text-fill:#ffffff;-fx-font-size:24px;-fx-font-weight:bold;");
        Label sub = new Label("Launcher ve oyun ayarlarını buradan yapılandır.");
        sub.setStyle("-fx-text-fill:#666688;-fx-font-size:13px;");

        VBox themeCard   = buildCard("🎨  Arka Plan Resmi",        buildBackgroundSettings());
        VBox perfCard    = buildCard("⚡  Performans Ayarları",    buildPerformanceSettings());
        VBox javaCard    = buildCard("☕  Java Ayarları",           buildJavaSettings());
        VBox discordCard = buildCard("🎮  Discord Ayarları",        buildDiscordSettings());
        VBox aboutCard   = buildCard("ℹ️  Hakkında",                buildAboutSection());

        getChildren().addAll(title, sub, themeCard, perfCard, javaCard, discordCard, aboutCard);
    }


    private static final java.nio.file.Path BG_PREFS =
        PathManager.getGameDir().resolve("bg_prefs.json");

    private void applyBackground(String imgPath) {
        try {
            // Kaydet
            java.nio.file.Files.createDirectories(BG_PREFS.getParent());
            com.google.gson.JsonObject j = new com.google.gson.JsonObject();
            j.addProperty("path", imgPath);
            java.nio.file.Files.writeString(BG_PREFS, j.toString());
            // Uygula
            javafx.scene.image.Image img = new javafx.scene.image.Image(
                new java.io.File(imgPath).toURI().toString());
            if (MainWindow.bgView != null)
                MainWindow.bgView.setImage(img);
            System.out.println("[BG] Kaydedildi: " + imgPath);
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void resetToDefault(javafx.scene.image.ImageView preview) {
        try {
            java.nio.file.Files.deleteIfExists(BG_PREFS);
            // Resmi byte olarak oku, birden fazla yerde kullan
            java.io.InputStream s = getClass().getResourceAsStream("/assets/background.png");
            if (s == null) { System.out.println("[BG] HATA: /assets/background.png bulunamadı!"); return; }
            byte[] bytes = s.readAllBytes();
            javafx.scene.image.Image img = new javafx.scene.image.Image(new java.io.ByteArrayInputStream(bytes));
            javafx.scene.image.Image img2 = new javafx.scene.image.Image(new java.io.ByteArrayInputStream(bytes));
            if (MainWindow.bgView != null)
                MainWindow.bgView.setImage(img);
            if (preview != null)
                javafx.application.Platform.runLater(() -> preview.setImage(img2));
            System.out.println("[BG] Varsayılan yüklendi, hata: " + img.isError());
        } catch (Exception e) { e.printStackTrace(); }
    }

    private VBox buildBackgroundSettings() {
        VBox box = new VBox(16);

        Label lbl = new Label("Launcher arka plan resmini değiştir");
        lbl.setStyle("-fx-text-fill:#aaaacc;-fx-font-size:13px;");

        // Mevcut arka plan önizleme
        javafx.scene.image.ImageView preview = new javafx.scene.image.ImageView();
        preview.setFitWidth(320); preview.setFitHeight(180);
        preview.setPreserveRatio(false);
        preview.setStyle("-fx-effect:dropshadow(gaussian,#000000aa,12,0,0,4);");
        try {
            if (java.nio.file.Files.exists(BG_PREFS)) {
                com.google.gson.JsonObject j = com.google.gson.JsonParser.parseString(
                    java.nio.file.Files.readString(BG_PREFS)).getAsJsonObject();
                String p = j.get("path").getAsString();
                preview.setImage(new javafx.scene.image.Image(new java.io.File(p).toURI().toString()));
            } else {
                java.io.InputStream def = getClass().getResourceAsStream("/assets/background.png");
                if (def != null) preview.setImage(new javafx.scene.image.Image(def));
            }
        } catch (Exception ignored) {}

        // Dosyadan seç butonu
        Button chooseBtn = new Button("📁  Bilgisayardan Resim Seç");
        chooseBtn.setStyle("-fx-background-color:#6c63ff;-fx-text-fill:#fff;-fx-font-size:13px;" +
            "-fx-background-radius:8;-fx-padding:10 20;-fx-cursor:hand;");
        chooseBtn.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.setTitle("Arka Plan Resmi Seç");
            fc.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Resim Dosyaları", "*.png","*.jpg","*.jpeg","*.webp","*.gif"));
            java.io.File f2 = fc.showOpenDialog(getScene().getWindow());
            if (f2 != null) {
                applyBackground(f2.getAbsolutePath());
                preview.setImage(new javafx.scene.image.Image(f2.toURI().toString()));
            }
        });

        // Varsayılana sıfırla
        Button resetBtn = new Button("↺  Varsayılan Arka Plana Dön");
        resetBtn.setStyle("-fx-background-color:#ffffff11;-fx-text-fill:#aaaacc;-fx-font-size:12px;" +
            "-fx-background-radius:8;-fx-padding:8 16;-fx-cursor:hand;");
        resetBtn.setOnAction(e -> resetToDefault(preview));

        Label hint = new Label("💡  PNG, JPG, GIF formatları desteklenir. Önerilen: 1280×720 veya üstü.");
        hint.setStyle("-fx-text-fill:#444466;-fx-font-size:11px;");
        hint.setWrapText(true);

        javafx.scene.layout.HBox btnRow = new javafx.scene.layout.HBox(12, chooseBtn, resetBtn);
        btnRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        box.getChildren().addAll(lbl, preview, btnRow, hint);
        return box;
    }

    private VBox buildThemeSettings_UNUSED() {
        VBox box = new VBox(20);

        // Tema seçici grid
        Label themeLbl = new Label("Arka Plan Teması");
        themeLbl.setStyle("-fx-text-fill:#ffffff;-fx-font-size:13px;");
        Label themeDesc = new Label("Launcher'ın renk temasını seç");
        themeDesc.setStyle("-fx-text-fill:#444466;-fx-font-size:11px;");

        javafx.scene.layout.GridPane grid = new javafx.scene.layout.GridPane();
        grid.setHgap(12); grid.setVgap(12);

        ThemeManager.Theme[] themes = ThemeManager.Theme.values();
        ThemeManager tm = ThemeManager.get();

        for (int i = 0; i < themes.length; i++) {
            ThemeManager.Theme t = themes[i];
            VBox card = buildThemeCard(t, tm.getTheme() == t);
            card.setOnMouseClicked(e -> {
                tm.setTheme(t);
                // Tüm kartları güncelle
                refreshThemeCards(grid, tm);
                // Arka planı güncelle
                setStyle("-fx-background-color:transparent;");
            });
            grid.add(card, i % 3, i / 3);
        }

        box.getChildren().addAll(themeLbl, themeDesc, grid);
        return box;
    }

    private VBox buildThemeCard(ThemeManager.Theme t, boolean selected) {
        VBox card = new VBox(8);
        card.setPadding(new Insets(12));
        card.setPrefWidth(160);
        card.setStyle(
            "-fx-background-color:" + t.cardColor + ";" +
            "-fx-background-radius:10;" +
            "-fx-border-color:" + (selected ? t.accentColor : t.borderColor) + ";" +
            "-fx-border-radius:10;" +
            "-fx-border-width:" + (selected ? "2" : "1") + ";" +
            "-fx-cursor:hand;");

        // Renk önizleme
        HBox preview = new HBox(4);
        preview.setAlignment(Pos.CENTER_LEFT);
        for (String color : new String[]{t.bgColor, t.cardColor, t.accentColor}) {
            Circle c = new Circle(8);
            c.setFill(Color.web(color));
            preview.getChildren().add(c);
        }

        Label name = new Label(t.displayName);
        name.setStyle("-fx-text-fill:" + (selected ? t.accentColor : "#888899") + ";" +
            "-fx-font-size:11px;-fx-font-weight:" + (selected ? "bold" : "normal") + ";");
        name.setWrapText(true);

        Label check = new Label(selected ? "✓ Aktif" : "");
        check.setStyle("-fx-text-fill:" + t.accentColor + ";-fx-font-size:10px;");

        card.getChildren().addAll(preview, name, check);

        card.setOnMouseEntered(e -> card.setStyle(
            "-fx-background-color:" + t.cardColor + ";" +
            "-fx-background-radius:10;" +
            "-fx-border-color:" + t.accentColor + ";" +
            "-fx-border-radius:10;-fx-border-width:2;-fx-cursor:hand;"));
        card.setOnMouseExited(e -> card.setStyle(
            "-fx-background-color:" + t.cardColor + ";" +
            "-fx-background-radius:10;" +
            "-fx-border-color:" + (ThemeManager.get().getTheme() == t ? t.accentColor : t.borderColor) + ";" +
            "-fx-border-radius:10;" +
            "-fx-border-width:" + (ThemeManager.get().getTheme() == t ? "2" : "1") + ";-fx-cursor:hand;"));

        return card;
    }

    private void refreshThemeCards(javafx.scene.layout.GridPane grid, ThemeManager tm) {
        ThemeManager.Theme[] themes = ThemeManager.Theme.values();
        grid.getChildren().clear();
        for (int i = 0; i < themes.length; i++) {
            ThemeManager.Theme t = themes[i];
            VBox card = buildThemeCard(t, tm.getTheme() == t);
            card.setOnMouseClicked(e -> {
                tm.setTheme(t);
                refreshThemeCards(grid, tm);
                setStyle("-fx-background-color:transparent;");
            });
            grid.add(card, i % 3, i / 3);
        }
    }

    private VBox buildCard(String title, VBox content) {
        ThemeManager tm = ThemeManager.get();
        VBox card = new VBox(16);
        card.setPadding(new Insets(24, 28, 24, 28));
        card.setStyle(
            "-fx-background-color:" + tm.card() + ";" +
            "-fx-background-radius:16;" +
            "-fx-border-color:" + tm.border() + ";" +
            "-fx-border-radius:16;-fx-border-width:1;");
        Label t = new Label(title);
        t.setStyle("-fx-text-fill:#aaaacc;-fx-font-size:14px;-fx-font-weight:bold;");
        Separator sep = new Separator();
        sep.setStyle("-fx-background-color:" + tm.border() + ";");
        card.getChildren().addAll(t, sep, content);
        return card;
    }

    private VBox buildPerformanceSettings() {
        VBox box = new VBox(14);
        box.getChildren().add(settingRow("Varsayılan RAM", "Oyun başlatılırken kullanılacak bellek",
            new ComboBox<String>() {{
                getItems().addAll("1 GB","2 GB","4 GB","6 GB","8 GB","12 GB","16 GB");
                setValue("4 GB");
                setStyle("-fx-background-color:#ffffff11;-fx-text-fill:#fff;" +
                    "-fx-border-color:#3a3a60;-fx-border-radius:8;-fx-background-radius:8;");
                setPrefWidth(140);
            }}));
        box.getChildren().add(toggleRow("Aikar's JVM Flags",
            "Otomatik performans optimizasyonu (önerilir)", true));
        box.getChildren().add(settingRow("Render Modu", "Pencere modu tercihi",
            new ComboBox<String>() {{
                getItems().addAll("Pencereli","Tam Ekran","Kenarsız");
                setValue("Pencereli");
                setStyle("-fx-background-color:#ffffff11;-fx-text-fill:#fff;" +
                    "-fx-border-color:#3a3a60;-fx-border-radius:8;-fx-background-radius:8;");
                setPrefWidth(140);
            }}));
        return box;
    }

    private VBox buildJavaSettings() {
        VBox box = new VBox(14);
        HBox javaRow = new HBox(10);
        javaRow.setAlignment(Pos.CENTER_LEFT);
        VBox javaInfo = new VBox(3);
        HBox.setHgrow(javaInfo, Priority.ALWAYS);
        Label jt = new Label("Java Yolu");
        jt.setStyle("-fx-text-fill:#ffffff;-fx-font-size:13px;");
        Label js = new Label("Varsayılan sistem Java'sı kullanılır");
        js.setStyle("-fx-text-fill:#444466;-fx-font-size:11px;");
        javaInfo.getChildren().addAll(jt, js);
        TextField javaField = new TextField("java");
        javaField.setStyle("-fx-background-color:#ffffff11;-fx-text-fill:#ffffff;" +
            "-fx-border-color:#3a3a60;-fx-border-radius:8;-fx-background-radius:8;" +
            "-fx-padding:8 12;-fx-font-size:12px;-fx-font-family:monospace;");
        javaField.setPrefWidth(200);
        javaRow.getChildren().addAll(javaInfo, javaField);

        VBox jvmBox = new VBox(6);
        Label jvmT = new Label("Ek JVM Argümanları");
        jvmT.setStyle("-fx-text-fill:#ffffff;-fx-font-size:13px;");
        TextArea jvmArea = new TextArea("-Dfml.ignoreInvalidMinecraftCertificates=true");
        jvmArea.setPrefHeight(60);
        jvmArea.setStyle("-fx-background-color:#ffffff11;-fx-text-fill:#aaaacc;" +
            "-fx-border-color:#3a3a60;-fx-border-radius:8;-fx-background-radius:8;" +
            "-fx-font-size:11px;-fx-font-family:monospace;");
        jvmBox.getChildren().addAll(jvmT, jvmArea);
        box.getChildren().addAll(javaRow, jvmBox);
        return box;
    }

    private VBox buildDiscordSettings() {
        VBox box = new VBox(14);
        box.getChildren().add(toggleRow("Discord Rich Presence",
            "Discord profilinde oyun durumunu göster", true));
        box.getChildren().add(toggleRow("Mod Listesi Göster",
            "Hangi modları oynadığını Discord'da göster", false));
        HBox statusRow = new HBox(10);
        statusRow.setAlignment(Pos.CENTER_LEFT);
        boolean connected = NoktaLauncher.discord.isConnected();
        Label dot = new Label("●");
        dot.setStyle("-fx-text-fill:" + (connected ? "#44dd88" : "#f59e0b") + ";-fx-font-size:14px;");
        Label statusTxt = new Label(connected ? "Discord'a bağlı" : "Discord bulunamadı");
        statusTxt.setStyle("-fx-text-fill:#666688;-fx-font-size:12px;");
        statusRow.getChildren().addAll(dot, statusTxt);
        box.getChildren().add(statusRow);
        return box;
    }

    private VBox buildAboutSection() {
        VBox box = new VBox(10);
        infoRow(box, "Launcher",        "Nokta Launcher");
        infoRow(box, "Sürüm",           "v" + "1.0.0");
        infoRow(box, "Java",            System.getProperty("java.version"));
        infoRow(box, "İşletim Sistemi", System.getProperty("os.name"));
        infoRow(box, "Oyun Dizini",     PathManager.getGameDir().toAbsolutePath().toString());
        Separator sep = new Separator();
        sep.setStyle("-fx-background-color:transparent;");
        Button openBtn = new Button("📁  Oyun Klasörünü Aç");
        openBtn.setStyle("-fx-background-color:#ffffff11;-fx-text-fill:#aaaacc;" +
            "-fx-background-radius:8;-fx-padding:8 16;-fx-cursor:hand;" +
            "-fx-border-color:#3a3a60;-fx-border-radius:8;");
        openBtn.setOnAction(e -> {
            try { new ProcessBuilder("xdg-open",
                PathManager.getGameDir().toAbsolutePath().toString()).start();
            } catch (Exception ex) { ex.printStackTrace(); }
        });
        box.getChildren().addAll(sep, openBtn);
        return box;
    }

    private HBox settingRow(String title, String desc, Control control) {
        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);
        VBox info = new VBox(3);
        HBox.setHgrow(info, Priority.ALWAYS);
        Label t = new Label(title);
        t.setStyle("-fx-text-fill:#ffffff;-fx-font-size:13px;");
        Label d = new Label(desc);
        d.setStyle("-fx-text-fill:#444466;-fx-font-size:11px;");
        info.getChildren().addAll(t, d);
        row.getChildren().addAll(info, control);
        return row;
    }

    private HBox toggleRow(String title, String desc, boolean on) {
        ToggleButton toggle = new ToggleButton(on ? "Açık" : "Kapalı");
        toggle.setSelected(on);
        String onStyle  = "-fx-background-color:" + ThemeManager.get().accent() +
            ";-fx-text-fill:white;-fx-background-radius:20;-fx-padding:4 16;-fx-cursor:hand;-fx-font-size:11px;";
        String offStyle = "-fx-background-color:#ffffff11;-fx-text-fill:#666688;" +
            "-fx-background-radius:20;-fx-padding:4 16;-fx-cursor:hand;-fx-font-size:11px;";
        toggle.setStyle(on ? onStyle : offStyle);
        toggle.selectedProperty().addListener((obs, o, n) -> {
            toggle.setText(n ? "Açık" : "Kapalı");
            toggle.setStyle(n ? onStyle : offStyle);
        });
        return settingRow(title, desc, toggle);
    }

    private void infoRow(VBox parent, String key, String value) {
        HBox row = new HBox(10);
        Label k = new Label(key + ":");
        k.setStyle("-fx-text-fill:#444466;-fx-font-size:12px;");
        k.setPrefWidth(130);
        Label v = new Label(value);
        v.setStyle("-fx-text-fill:#aaaacc;-fx-font-size:12px;-fx-font-family:monospace;");
        v.setWrapText(true);
        row.getChildren().addAll(k, v);
        parent.getChildren().add(row);
    }
}
