package com.nokta.launcher.ui;

import com.nokta.launcher.core.PerformanceManager;
import com.nokta.launcher.mods.ModrinthAPI;
import com.nokta.launcher.utils.PathManager;
import javafx.application.Platform;
import javafx.geometry.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.*;
import javafx.scene.shape.Circle;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.*;

public class PerformanceScreen extends VBox {

    private final ModrinthAPI api = new ModrinthAPI();
    private Label systemInfoLabel;
    private VBox installLogBox;

    public PerformanceScreen() {
        setSpacing(20);
        setPadding(new Insets(32, 40, 32, 40));
        setStyle("-fx-background-color:transparent;");
        buildUI();
    }

    private void buildUI() {
        Label title = new Label("⚡  Performans Merkezi");
        title.setStyle("-fx-text-fill:#ffffff;-fx-font-size:24px;-fx-font-weight:bold;");
        Label sub = new Label("Sisteminize özel FPS artırıcı mod paketlerini otomatik kurun.");
        sub.setStyle("-fx-text-fill:#666688;-fx-font-size:13px;");

        // Sistem bilgisi kartı
        VBox sysCard = buildSystemInfoCard();

        // Sürüm & Loader seçimi
        HBox filterRow = new HBox(12);
        filterRow.setAlignment(Pos.CENTER_LEFT);
        Label filterLbl = new Label("Sürüm:");
        filterLbl.setStyle("-fx-text-fill:#aaaacc;-fx-font-size:13px;");

        ComboBox<String> versionCb = new ComboBox<>();
        versionCb.getItems().addAll("1.21.4","1.21.1","1.20.1","1.19.4","1.18.2");
        versionCb.setValue("1.21.4");
        styleCombo(versionCb);

        Label loaderLbl = new Label("Loader:");
        loaderLbl.setStyle("-fx-text-fill:#aaaacc;-fx-font-size:13px;");

        ComboBox<String> loaderCb = new ComboBox<>();
        loaderCb.getItems().addAll("fabric","quilt");
        loaderCb.setValue("fabric");
        styleCombo(loaderCb);

        Label note = new Label("⚠ FPS modları sadece Fabric/Quilt destekler");
        note.setStyle("-fx-text-fill:#f59e0b44;-fx-font-size:11px;");

        filterRow.getChildren().addAll(filterLbl, versionCb, loaderLbl, loaderCb, note);

        // FPS paket kartları
        VBox packsBox = new VBox(12);
        for (PerformanceManager.FPSPack pack : PerformanceManager.FPS_PACKS) {
            packsBox.getChildren().add(buildPackCard(pack, versionCb, loaderCb));
        }

        // Kurulum logu
        installLogBox = new VBox(4);
        installLogBox.setPadding(new Insets(12));
        installLogBox.setStyle(
            "-fx-background-color:#00000044;-fx-background-radius:8;" +
            "-fx-border-color:#1a1a28;-fx-border-radius:8;-fx-border-width:1;");
        installLogBox.setVisible(false);

        getChildren().addAll(title, sub, sysCard, filterRow, packsBox, installLogBox);
    }

    private VBox buildSystemInfoCard() {
        VBox card = new VBox(10);
        card.setPadding(new Insets(20, 24, 20, 24));
        card.setStyle(
            "-fx-background-color:#00000044;-fx-background-radius:12;" +
            "-fx-border-color:#1e1e30;-fx-border-radius:12;-fx-border-width:1;");

        HBox row = new HBox(24);
        row.setAlignment(Pos.CENTER_LEFT);

        long ramMB  = PerformanceManager.getTotalSystemRAMMB();
        long ramGB  = ramMB / 1024;
        String os   = System.getProperty("os.name");
        String arch = System.getProperty("os.arch");
        int cores   = Runtime.getRuntime().availableProcessors();

        row.getChildren().addAll(
            sysInfoItem("💾 RAM",   ramGB + " GB",  ramGB >= 8 ? "#44dd88" : ramGB >= 4 ? "#f59e0b" : "#f04040"),
            sysInfoItem("💻 OS",    os,              "#6c63ff"),
            sysInfoItem("⚙ CPU",   cores + " çekirdek", "#3b82f6"),
            sysInfoItem("🏗 Arch",  arch,            "#10b981")
        );

        // Öneri
        String recPack = ramGB >= 8 ? "⚡ Ultra FPS Pack" :
                         ramGB >= 4 ? "🟢 Düşük Sistem Paketi" : "🟢 Düşük Sistem Paketi";
        Label rec = new Label("💡 Sisteminiz için öneri: " + recPack);
        rec.setStyle("-fx-text-fill:#6c63ff;-fx-font-size:12px;-fx-font-style:italic;");

        card.getChildren().addAll(row, rec);
        return card;
    }

    private VBox sysInfoItem(String label, String value, String color) {
        VBox box = new VBox(4);
        box.setAlignment(Pos.CENTER_LEFT);
        Label l = new Label(label);
        l.setStyle("-fx-text-fill:#444466;-fx-font-size:11px;");
        Label v = new Label(value);
        v.setStyle("-fx-text-fill:"+color+";-fx-font-size:14px;-fx-font-weight:bold;");
        box.getChildren().addAll(l, v);
        return box;
    }

    private VBox buildPackCard(PerformanceManager.FPSPack pack,
                                ComboBox<String> versionCb,
                                ComboBox<String> loaderCb) {
        VBox card = new VBox(14);
        card.setPadding(new Insets(20, 24, 20, 24));
        card.setStyle(
            "-fx-background-color:#00000044;-fx-background-radius:14;" +
            "-fx-border-color:" + pack.color + "33;" +
            "-fx-border-radius:14;-fx-border-width:1;");

        // Başlık satırı
        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);

        Circle dot = new Circle(6);
        dot.setFill(Color.web(pack.color));

        VBox titleBox = new VBox(3);
        HBox.setHgrow(titleBox, Priority.ALWAYS);

        HBox titleRow = new HBox(8);
        titleRow.setAlignment(Pos.CENTER_LEFT);
        Label nameLabel = new Label(pack.name);
        nameLabel.setStyle("-fx-text-fill:#ffffff;-fx-font-size:15px;-fx-font-weight:bold;");

        if (pack.recommended) {
            Label badge = new Label("  ÖNERİLEN  ");
            badge.setStyle(
                "-fx-background-color:" + pack.color + "33;" +
                "-fx-text-fill:" + pack.color + ";" +
                "-fx-background-radius:6;-fx-font-size:10px;-fx-padding:2 8;");
            titleRow.getChildren().addAll(nameLabel, badge);
        } else {
            titleRow.getChildren().add(nameLabel);
        }

        Label descLabel = new Label(pack.description);
        descLabel.setStyle("-fx-text-fill:#555577;-fx-font-size:12px;");
        titleBox.getChildren().addAll(titleRow, descLabel);

        Label ramLabel = new Label("RAM: " + pack.recommendedRAM/1024 + "GB+");
        ramLabel.setStyle("-fx-text-fill:" + pack.color + ";-fx-font-size:12px;");

        header.getChildren().addAll(dot, titleBox, ramLabel);

        // Mod listesi
        FlowPane modTags = new FlowPane(6, 6);
        for (PerformanceManager.ModEntry mod : pack.mods) {
            Label tag = new Label(mod.displayName);
            Tooltip.install(tag, new Tooltip(mod.description));
            tag.setStyle(
                "-fx-background-color:#ffffff11;-fx-text-fill:#888899;" +
                "-fx-background-radius:6;-fx-padding:4 10;-fx-font-size:11px;");
            modTags.getChildren().add(tag);
        }

        // Kurulum butonu & progress
        HBox btnRow = new HBox(12);
        btnRow.setAlignment(Pos.CENTER_RIGHT);

        ProgressBar packProgress = new ProgressBar(0);
        packProgress.setPrefWidth(200);
        packProgress.setPrefHeight(6);
        packProgress.setStyle("-fx-accent:"+pack.color+";-fx-background-color:#ffffff11;-fx-background-radius:3;");
        packProgress.setVisible(false);
        HBox.setHgrow(packProgress, Priority.ALWAYS);

        Label packStatus = new Label("");
        packStatus.setStyle("-fx-text-fill:#555577;-fx-font-size:11px;");

        Button installBtn = new Button("⬇  Paketi Kur  (" + pack.mods.size() + " mod)");
        installBtn.setStyle(
            "-fx-background-color:" + pack.color + "22;" +
            "-fx-text-fill:" + pack.color + ";" +
            "-fx-background-radius:10;-fx-padding:10 20;" +
            "-fx-border-color:" + pack.color + "55;-fx-border-radius:10;" +
            "-fx-cursor:hand;-fx-font-size:13px;-fx-font-weight:bold;");

        installBtn.setOnAction(e -> installPack(
            pack, versionCb.getValue(), loaderCb.getValue(),
            installBtn, packProgress, packStatus
        ));

        btnRow.getChildren().addAll(packStatus, packProgress, installBtn);
        card.getChildren().addAll(header, modTags, btnRow);
        return card;
    }

    private void installPack(PerformanceManager.FPSPack pack,
                              String mcVersion, String loader,
                              Button btn, ProgressBar pb, Label status) {
        btn.setDisable(true);
        pb.setVisible(true);
        installLogBox.setVisible(true);
        status.setText("⏳ Kuruluyor...");

        Path modsDir = PathManager.getGameDir().resolve("mods").resolve(mcVersion);

        new Thread(() -> {
            AtomicInteger installed = new AtomicInteger(0);
            int total = pack.mods.size();

            for (PerformanceManager.ModEntry mod : pack.mods) {
                addLog("🔍 Aranıyor: " + mod.displayName);
                try {
                    List<ModrinthAPI.Mod> results = api.search(mod.slug, mcVersion, loader, 3);
                    if (results.isEmpty()) {
                        addLog("⚠ Bulunamadı: " + mod.displayName + " (atlandı)");
                        continue;
                    }

                    ModrinthAPI.Mod found = results.get(0);
                    List<ModrinthAPI.ModVersion> versions = api.getVersions(
                        found.id, mcVersion, loader);

                    if (versions.isEmpty()) {
                        addLog("⚠ Uyumlu versiyon yok: " + mod.displayName);
                        continue;
                    }

                    ModrinthAPI.ModVersion ver = versions.get(0);
                    addLog("⬇ İndiriliyor: " + mod.displayName + " " + ver.versionNumber);

                    api.downloadMod(ver, modsDir, new ModrinthAPI.DownloadCallback() {
                        @Override public void progress(int pct) {}
                        @Override public void done(Path file) {
                            int n = installed.incrementAndGet();
                            double progress = n / (double) total;
                            Platform.runLater(() -> {
                                pb.setProgress(progress);
                                status.setText(n + "/" + total + " kuruldu");
                            });
                            addLog("✅ Kuruldu: " + mod.displayName);
                        }
                    });
                } catch (Exception ex) {
                    addLog("❌ Hata (" + mod.displayName + "): " + ex.getMessage());
                }
            }

            Platform.runLater(() -> {
                pb.setProgress(1.0);
                status.setStyle("-fx-text-fill:#44dd88;-fx-font-size:11px;");
                status.setText("✅ " + installed.get() + "/" + total + " mod kuruldu!");
                btn.setText("✅ Kuruldu");
                btn.setDisable(false);
                addLog("🎉 " + pack.name + " kurulum tamamlandı!");
                addLog("📁 Mods: " + modsDir.toAbsolutePath());
            });
        }, "pack-installer").start();
    }

    private void addLog(String msg) {
        Platform.runLater(() -> {
            Label log = new Label(msg);
            log.setStyle("-fx-text-fill:#555577;-fx-font-size:11px;-fx-font-family:monospace;");
            installLogBox.getChildren().add(0, log);
            if (installLogBox.getChildren().size() > 25)
                installLogBox.getChildren().remove(installLogBox.getChildren().size()-1);
        });
    }

    private void styleCombo(ComboBox<String> cb) {
        cb.setStyle(
            "-fx-background-color:#ffffff11;-fx-text-fill:#ffffff;" +
            "-fx-border-color:#3a3a60;-fx-border-radius:8;" +
            "-fx-background-radius:8;-fx-font-size:12px;-fx-padding:4;");
        cb.setPrefWidth(120);
    }
}
