package com.nokta.launcher.ui;

import com.google.gson.*;
import com.nokta.launcher.core.VersionManager;
import javafx.application.Platform;
import javafx.geometry.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;

public class VersionsScreen extends VBox {

    private VBox versionList;
    private Label statusLabel;
    private ComboBox<String> filterBox;

    public VersionsScreen() {
        setSpacing(0);
        setStyle("-fx-background-color:transparent;");
        setPadding(new Insets(32, 40, 32, 40));
        buildUI();
        loadVersions();
    }

    private void buildUI() {
        Label title = new Label("📦  Sürüm Yöneticisi");
        title.setStyle("-fx-text-fill:#ffffff;-fx-font-size:24px;-fx-font-weight:bold;");
        Label sub = new Label("Minecraft sürümlerini indir ve yönet");
        sub.setStyle("-fx-text-fill:#666688;-fx-font-size:14px;");
        VBox.setMargin(sub, new Insets(4, 0, 24, 0));

        // Filtre
        HBox filterRow = new HBox(12);
        filterRow.setAlignment(Pos.CENTER_LEFT);
        VBox.setMargin(filterRow, new Insets(0, 0, 20, 0));

        Label filterLbl = new Label("Filtre:");
        filterLbl.setStyle("-fx-text-fill:#666688;-fx-font-size:13px;");
        filterBox = new ComboBox<>();
        filterBox.getItems().addAll("Tümü", "Release", "Snapshot", "Beta", "Alpha");
        filterBox.setValue("Release");
        filterBox.setStyle("-fx-background-color:#ffffff11;-fx-text-fill:#ccccee;" +
            "-fx-border-color:#2a2a3e;-fx-border-radius:6;-fx-background-radius:6;");
        filterBox.setOnAction(e -> loadVersions());

        statusLabel = new Label("Yükleniyor...");
        statusLabel.setStyle("-fx-text-fill:#444466;-fx-font-size:12px;");
        HBox spacer = new HBox(); HBox.setHgrow(spacer, Priority.ALWAYS);

        filterRow.getChildren().addAll(filterLbl, filterBox, spacer, statusLabel);

        // Sürüm listesi
        versionList = new VBox(8);
        getChildren().addAll(title, sub, filterRow, versionList);
    }

    private void loadVersions() {
        versionList.getChildren().clear();
        statusLabel.setText("Yükleniyor...");

        new Thread(() -> {
            try {
                URL url = new URL("https://launchermeta.mojang.com/mc/game/version_manifest_v2.json");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(10000);
                JsonObject manifest = JsonParser.parseString(
                    new String(conn.getInputStream().readAllBytes())).getAsJsonObject();

                String filter = filterBox.getValue();
                List<JsonObject> versions = new ArrayList<>();
                for (JsonElement el : manifest.getAsJsonArray("versions")) {
                    JsonObject v = el.getAsJsonObject();
                    String type = v.get("type").getAsString();
                    if (filter.equals("Tümü") ||
                        filter.equalsIgnoreCase(type) ||
                        (filter.equals("Release") && type.equals("release")) ||
                        (filter.equals("Snapshot") && type.equals("snapshot")) ||
                        (filter.equals("Beta") && type.equals("old_beta")) ||
                        (filter.equals("Alpha") && type.equals("old_alpha")))
                        versions.add(v);
                }

                Platform.runLater(() -> {
                    statusLabel.setText(versions.size() + " sürüm");
                    for (JsonObject v : versions) {
                        versionList.getChildren().add(buildVersionCard(v));
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> statusLabel.setText("❌ Yüklenemedi: " + e.getMessage()));
            }
        }).start();
    }

    private HBox buildVersionCard(JsonObject version) {
        String id      = version.get("id").getAsString();
        String type    = version.get("type").getAsString();
        String relDate = version.get("releaseTime").getAsString().substring(0, 10);

        // İndirilmiş mi?
        Path versionDir = Paths.get(
            System.getProperty("user.home"), ".nokta-launcher", "versions", id);
        boolean downloaded = Files.exists(versionDir.resolve(id + ".jar"));

        HBox card = new HBox(12);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setPadding(new Insets(12, 16, 12, 16));
        card.setStyle("-fx-background-color:#00000055;-fx-background-radius:10;" +
            "-fx-border-color:#1e1e30;-fx-border-radius:10;-fx-border-width:1;");

        // Tip ikonu
        String typeIcon = switch (type) {
            case "release"  -> "🟢";
            case "snapshot" -> "🟡";
            case "old_beta" -> "🔵";
            default         -> "⚪";
        };
        Label iconLbl = new Label(typeIcon);
        iconLbl.setStyle("-fx-font-size:16px;");

        // Versiyon adı
        VBox nameBox = new VBox(2);
        HBox.setHgrow(nameBox, Priority.ALWAYS);
        Label nameLbl = new Label("Minecraft " + id);
        nameLbl.setStyle("-fx-text-fill:#ddddff;-fx-font-size:14px;-fx-font-weight:bold;");
        Label typeLbl = new Label(getTypeName(type) + "  •  " + relDate);
        typeLbl.setStyle("-fx-text-fill:#444466;-fx-font-size:12px;");
        nameBox.getChildren().addAll(nameLbl, typeLbl);

        // Durum
        Label statusLbl = new Label(downloaded ? "✅ İndirildi" : "");
        statusLbl.setStyle("-fx-text-fill:#44aa66;-fx-font-size:12px;");

        // Butonlar
        HBox btns = new HBox(8);
        btns.setAlignment(Pos.CENTER);

        if (downloaded) {
            Button playBtn = new Button("▶ Oyna");
            playBtn.setStyle(
                "-fx-background-color:#6c63ff;-fx-text-fill:white;" +
                "-fx-font-size:12px;-fx-padding:6 16;-fx-background-radius:6;-fx-cursor:hand;");
            playBtn.setOnAction(e -> {
                // PlayScreen'e geç ve bu versiyonu seç
                System.out.println("▶ Oynatılıyor: " + id);
            });

            Button deleteBtn = new Button("🗑");
            deleteBtn.setStyle(
                "-fx-background-color:#ff333322;-fx-text-fill:#ff6666;" +
                "-fx-font-size:12px;-fx-padding:6 10;-fx-background-radius:6;-fx-cursor:hand;");
            deleteBtn.setOnAction(e -> deleteVersion(id, card));

            btns.getChildren().addAll(playBtn, deleteBtn);
        } else {
            Button dlBtn = new Button("⬇ İndir");
            dlBtn.setStyle(
                "-fx-background-color:#00ff4422;-fx-text-fill:#44cc66;" +
                "-fx-font-size:12px;-fx-padding:6 16;-fx-background-radius:6;-fx-cursor:hand;");

            ProgressBar pb = new ProgressBar(0);
            pb.setPrefWidth(80);
            pb.setVisible(false);
            pb.setStyle("-fx-accent:#6c63ff;");

            dlBtn.setOnAction(e -> {
                dlBtn.setDisable(true);
                dlBtn.setText("İndiriliyor...");
                pb.setVisible(true);
                downloadVersion(id, pb, statusLbl, dlBtn, btns);
            });
            btns.getChildren().addAll(pb, dlBtn);
        }

        card.getChildren().addAll(iconLbl, nameBox, statusLbl, btns);
        return card;
    }

    private void downloadVersion(String id, ProgressBar pb, Label statusLbl,
                                  Button dlBtn, HBox btns) {
        new Thread(() -> {
            try {
                java.nio.file.Path gameDir = java.nio.file.Paths.get(
                    System.getProperty("user.home"), ".nokta-launcher");
                VersionManager vm = new VersionManager(gameDir);
                vm.downloadVersion(id, (msg, pct) -> Platform.runLater(() -> { pb.setProgress(pct/100.0); statusLbl.setText(pct+"%"); }));
                Platform.runLater(() -> {
                    statusLbl.setText("✅ İndirildi");
                    statusLbl.setStyle("-fx-text-fill:#44aa66;-fx-font-size:12px;");
                    dlBtn.setText("▶ Oyna");
                    dlBtn.setStyle(
                        "-fx-background-color:#6c63ff;-fx-text-fill:white;" +
                        "-fx-font-size:12px;-fx-padding:6 16;-fx-background-radius:6;-fx-cursor:hand;");
                    dlBtn.setDisable(false);
                    pb.setVisible(false);
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    statusLbl.setText("❌ Hata");
                    dlBtn.setDisable(false);
                    dlBtn.setText("⬇ Tekrar Dene");
                });
            }
        }).start();
    }

    private void deleteVersion(String id, HBox card) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Sürümü Sil");
        alert.setContentText("Minecraft " + id + " silinsin mi?");
        alert.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.OK) {
                try {
                    Path vDir = Paths.get(System.getProperty("user.home"),
                        ".nokta-launcher", "versions", id);
                    deleteDir(vDir.toFile());
                    loadVersions();
                } catch (Exception e) { System.out.println("⚠ Silinemedi: " + e.getMessage()); }
            }
        });
    }

    private void deleteDir(java.io.File dir) {
        if (dir.isDirectory())
            for (java.io.File f : dir.listFiles()) deleteDir(f);
        dir.delete();
    }

    private String getTypeName(String type) {
        return switch (type) {
            case "release"   -> "Release";
            case "snapshot"  -> "Snapshot";
            case "old_beta"  -> "Beta";
            case "old_alpha" -> "Alpha";
            default          -> type;
        };
    }
}
