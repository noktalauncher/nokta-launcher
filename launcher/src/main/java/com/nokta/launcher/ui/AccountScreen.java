package com.nokta.launcher.ui;

import com.nokta.launcher.core.AuthManager;
import com.nokta.launcher.utils.PathManager;
import javafx.application.Platform;
import javafx.geometry.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.*;
import javafx.scene.shape.Circle;
import javafx.scene.effect.DropShadow;

public class AccountScreen extends VBox {

    private final AuthManager authManager;
    private VBox accountDisplay;

    public AccountScreen() {
        this.authManager = new AuthManager(PathManager.getGameDir());
        setSpacing(20);
        setPadding(new Insets(32, 40, 32, 40));
        setStyle("-fx-background-color:transparent;");
        buildUI();
    }

    private void buildUI() {
        Label title = new Label("👤  Hesap Yönetimi");
        title.setStyle("-fx-text-fill:#ffffff;-fx-font-size:24px;-fx-font-weight:bold;");
        Label sub = new Label("Microsoft veya offline hesabınla giriş yap.");
        sub.setStyle("-fx-text-fill:#666688;-fx-font-size:13px;");

        accountDisplay = new VBox(16);
        refreshAccountDisplay();

        // Giriş seçenekleri kartı
        VBox loginCard = buildLoginCard();

        getChildren().addAll(title, sub, accountDisplay, loginCard);
    }

    private void refreshAccountDisplay() {
        accountDisplay.getChildren().clear();
        java.util.List<AuthManager.Account> all = authManager.getAccounts();

        if (all.isEmpty()) {
            VBox empty = new VBox(12);
            empty.setPadding(new Insets(28));
            empty.setAlignment(Pos.CENTER);
            empty.setStyle("-fx-background-color:#00000055;-fx-background-radius:16;-fx-border-color:#1e1e30;-fx-border-radius:16;-fx-border-width:1;");
            Label icon = new Label("👤"); icon.setStyle("-fx-font-size:40px;");
            Label msg = new Label("Henüz giriş yapılmadı");
            msg.setStyle("-fx-text-fill:#444466;-fx-font-size:14px;");
            empty.getChildren().addAll(icon, msg);
            accountDisplay.getChildren().add(empty);
            return;
        }

        for (AuthManager.Account acc : new java.util.ArrayList<>(all)) {
            HBox card = new HBox(14);
            card.setPadding(new Insets(14, 20, 14, 20));
            card.setAlignment(Pos.CENTER_LEFT);
            boolean isActive = acc == authManager.getCurrentAccount();
            card.setStyle(
                "-fx-background-color:" + (isActive ? "#6c63ff22" : "#00000044") + ";" +
                "-fx-background-radius:14;" +
                "-fx-border-color:" + (isActive ? "#6c63ff88" : "#1e1e30") + ";" +
                "-fx-border-radius:14;-fx-border-width:1.5;"
            );

            // Avatar
            StackPane avatar = new StackPane();
            avatar.setMinSize(44,44); avatar.setMaxSize(44,44);
            javafx.scene.shape.Circle bg = new javafx.scene.shape.Circle(22);
            bg.setFill(new LinearGradient(0,0,1,1,true,CycleMethod.NO_CYCLE,
                new Stop(0, Color.web(acc.microsoft ? "#6c63ff" : "#3b82f6")),
                new Stop(1, Color.web(acc.microsoft ? "#3b82f6" : "#10b981"))));
            Label init = new Label(String.valueOf(acc.username.charAt(0)).toUpperCase());
            init.setStyle("-fx-text-fill:white;-fx-font-size:16px;-fx-font-weight:bold;");
            avatar.getChildren().addAll(bg, init);
            // Skin async
            new Thread(() -> {
                try {
                    javafx.scene.image.Image img = new javafx.scene.image.Image(
                        "https://minotar.net/helm/" + acc.username + "/44", 44, 44, false, true);
                    if (!img.isError()) {
                        javafx.scene.image.ImageView iv = new javafx.scene.image.ImageView(img);
                        iv.setFitWidth(44); iv.setFitHeight(44);
                        javafx.scene.shape.Rectangle clip = new javafx.scene.shape.Rectangle(44,44);
                        clip.setArcWidth(10); clip.setArcHeight(10);
                        iv.setClip(clip);
                        Platform.runLater(() -> avatar.getChildren().setAll(iv));
                    }
                } catch (Exception ignored) {}
            }, "skin").start();

            // Info
            VBox info = new VBox(3);
            HBox.setHgrow(info, Priority.ALWAYS);
            HBox nameRow = new HBox(8);
            nameRow.setAlignment(Pos.CENTER_LEFT);
            Label name = new Label(acc.username);
            name.setStyle("-fx-text-fill:#ffffff;-fx-font-size:14px;-fx-font-weight:bold;");
            Label badge = new Label(acc.microsoft ? " Microsoft " : " Offline ");
            badge.setStyle("-fx-background-color:" + (acc.microsoft ? "#6c63ff33" : "#3b82f633") + ";" +
                "-fx-text-fill:" + (acc.microsoft ? "#a78bfa" : "#60a5fa") + ";" +
                "-fx-background-radius:5;-fx-font-size:10px;-fx-padding:2 6 2 6;");
            if (isActive) {
                Label active = new Label("● Aktif");
                active.setStyle("-fx-text-fill:#44dd88;-fx-font-size:10px;");
                nameRow.getChildren().addAll(name, badge, active);
            } else {
                nameRow.getChildren().addAll(name, badge);
            }
            info.getChildren().add(nameRow);

            // Butonlar
            HBox btns = new HBox(8);
            btns.setAlignment(Pos.CENTER_RIGHT);
            if (!isActive) {
                Button switchBtn = new Button("Seç");
                switchBtn.setStyle("-fx-background-color:#6c63ff33;-fx-text-fill:#a78bfa;" +
                    "-fx-background-radius:8;-fx-padding:6 14 6 14;-fx-cursor:hand;" +
                    "-fx-border-color:#6c63ff55;-fx-border-radius:8;");
                switchBtn.setOnAction(e -> {
                    authManager.switchAccount(acc);
                    refreshAccountDisplay();
                    // Launcher config güncelle
                    com.nokta.launcher.core.NokTaConfig cfg = com.nokta.launcher.core.NokTaConfig.load();
                    cfg.username = acc.username;
                    cfg.save();
                });
                btns.getChildren().add(switchBtn);
            }
            Button delBtn = new Button("Sil");
            delBtn.setStyle("-fx-background-color:#ff333322;-fx-text-fill:#f04040;" +
                "-fx-background-radius:8;-fx-padding:6 14 6 14;-fx-cursor:hand;" +
                "-fx-border-color:#f0404033;-fx-border-radius:8;");
            delBtn.setOnAction(e -> {
                authManager.removeAccount(acc);
                refreshAccountDisplay();
            });
            btns.getChildren().add(delBtn);

            card.getChildren().addAll(avatar, info, btns);
            accountDisplay.getChildren().add(card);
        }
    }

        private VBox buildLoginCard() {
        VBox card = new VBox(16);
        card.setPadding(new Insets(28, 32, 28, 32));
        card.setStyle(
            "-fx-background-color:#00000055;" +
            "-fx-background-radius:16;" +
            "-fx-border-color:#1e1e30;" +
            "-fx-border-radius:16;" +
            "-fx-border-width:1;"
        );

        Label cardTitle = new Label("Giriş Yap");
        cardTitle.setStyle("-fx-text-fill:#aaaacc;-fx-font-size:14px;-fx-font-weight:bold;");

        // Tab butonları
        HBox tabs = new HBox(8);
        Button msTab      = new Button("🔷  Microsoft Hesabı");
        Button offlineTab = new Button("👤  Offline");
        styleTab(msTab, true);
        styleTab(offlineTab, false);

        tabs.getChildren().addAll(msTab, offlineTab);

        // İçerik alanı (Microsoft veya Offline form)
        StackPane tabContent = new StackPane();

        VBox msContent      = buildMicrosoftTab();
        VBox offlineContent = buildOfflineTab();
        offlineContent.setVisible(false);
        offlineContent.setManaged(false);

        tabContent.getChildren().addAll(msContent, offlineContent);

        msTab.setOnAction(e -> {
            styleTab(msTab, true);
            styleTab(offlineTab, false);
            msContent.setVisible(true); msContent.setManaged(true);
            offlineContent.setVisible(false); offlineContent.setManaged(false);
        });
        offlineTab.setOnAction(e -> {
            styleTab(offlineTab, true);
            styleTab(msTab, false);
            offlineContent.setVisible(true); offlineContent.setManaged(true);
            msContent.setVisible(false); msContent.setManaged(false);
        });

        card.getChildren().addAll(cardTitle, tabs, tabContent);
        return card;
    }

    private VBox buildMicrosoftTab() {
        VBox box = new VBox(16);
        Label info = new Label(
            "Microsoft hesabınla giriş yapmak için butona tıkla.\n" +
            "Tarayıcıda giriş yaptıktan sonra otomatik olarak tamamlanır."
        );
        info.setStyle("-fx-text-fill:#666688;-fx-font-size:12px;");
        info.setWrapText(true);

        Button openBrowserBtn = new Button("🌐  Microsoft ile Giriş Yap");
        openBrowserBtn.setMaxWidth(Double.MAX_VALUE);
        openBrowserBtn.setStyle(
            "-fx-background-color:linear-gradient(to right,#0078d4,#106ebe);" +
            "-fx-text-fill:white;-fx-font-size:13px;-fx-font-weight:bold;" +
            "-fx-background-radius:10;-fx-padding:12 24 12 24;-fx-cursor:hand;"
        );

        ProgressBar msProgress = new ProgressBar(0);
        msProgress.setMaxWidth(Double.MAX_VALUE);
        msProgress.setStyle("-fx-accent:#6c63ff;-fx-background-color:#ffffff11;-fx-background-radius:4;");
        msProgress.setVisible(false);

        Label msStatus = new Label("");
        msStatus.setStyle("-fx-text-fill:#aaaacc;-fx-font-size:12px;");
        msStatus.setWrapText(true);

        openBrowserBtn.setOnAction(e -> {
            msProgress.setVisible(true);
            msProgress.setProgress(-1);
            openBrowserBtn.setDisable(true);
            msStatus.setStyle("-fx-text-fill:#f59e0b;-fx-font-size:12px;");
            msStatus.setText("⏳ Tarayıcıda giriş yapın...");
            AuthManager auth = new AuthManager(PathManager.getGameDir());
            auth.startMicrosoftAuthFlow(
                (msg, pct) -> Platform.runLater(() -> {
                    if (pct > 0) msProgress.setProgress(pct / 100.0);
                    msStatus.setText("⏳ " + msg);
                }),
                acc -> Platform.runLater(() -> {
                    msStatus.setStyle("-fx-text-fill:#44dd88;-fx-font-size:12px;");
                    msStatus.setText("✅ Hoş geldin, " + acc.username + "!");
                    msProgress.setProgress(1);
                    openBrowserBtn.setDisable(false);
                    refreshAccountDisplay();
                }),
                err -> Platform.runLater(() -> {
                    msStatus.setStyle("-fx-text-fill:#f04040;-fx-font-size:12px;");
                    msStatus.setText("❌ " + err);
                    msProgress.setVisible(false);
                    openBrowserBtn.setDisable(false);
                })
            );
        });

        box.getChildren().addAll(info, openBrowserBtn, msProgress, msStatus);
        return box;
    }

        private VBox buildOfflineTab() {
        VBox box = new VBox(16);

        Label info = new Label("İnternet bağlantısı olmadan oynayabilirsin.\nBu mod sadece offline (cracked) sunucularda çalışır.");
        info.setStyle("-fx-text-fill:#666688;-fx-font-size:12px;");
        info.setWrapText(true);

        Label userLabel = new Label("Oyuncu Adı");
        userLabel.setStyle("-fx-text-fill:#aaaacc;-fx-font-size:12px;-fx-font-weight:bold;");

        TextField userField = new TextField();
        userField.setPromptText("Oyuncu adını gir...");
        userField.setStyle(
            "-fx-background-color:#ffffff11;" +
            "-fx-text-fill:#ffffff;" +
            "-fx-border-color:#3a3a60;" +
            "-fx-border-radius:8;" +
            "-fx-background-radius:8;" +
            "-fx-font-size:13px;" +
            "-fx-padding:10 14 10 14;"
        );

        Label statusLabel = new Label("");
        statusLabel.setStyle("-fx-text-fill:#aaaacc;-fx-font-size:12px;");

        Button loginBtn = new Button("▶  Offline Giriş Yap");
        loginBtn.setMaxWidth(Double.MAX_VALUE);
        loginBtn.setStyle(
            "-fx-background-color:linear-gradient(to right,#3b82f6,#10b981);" +
            "-fx-text-fill:white;" +
            "-fx-font-size:14px;" +
            "-fx-font-weight:bold;" +
            "-fx-background-radius:10;" +
            "-fx-padding:12 24 12 24;" +
            "-fx-cursor:hand;"
        );

        loginBtn.setOnAction(e -> {
            String name = userField.getText().trim();
            if (name.isEmpty()) {
                statusLabel.setStyle("-fx-text-fill:#f04040;-fx-font-size:12px;");
                statusLabel.setText("❌ Lütfen bir oyuncu adı girin!");
                return;
            }
            if (name.length() < 3 || name.length() > 16) {
                statusLabel.setStyle("-fx-text-fill:#f04040;-fx-font-size:12px;");
                statusLabel.setText("❌ Oyuncu adı 3-16 karakter olmalı!");
                return;
            }
            AuthManager auth = new AuthManager(PathManager.getGameDir());
            AuthManager.Account acc = auth.loginOffline(name);
            statusLabel.setStyle("-fx-text-fill:#44dd88;-fx-font-size:12px;");
            statusLabel.setText("✅ Giriş başarılı! Hoş geldin, " + acc.username);
            refreshAccountDisplay();
        });

        box.getChildren().addAll(info, userLabel, userField, statusLabel, loginBtn);
        return box;
    }

    private void styleTab(Button btn, boolean active) {
        if (active) {
            btn.setStyle(
                "-fx-background-color:#6c63ff33;-fx-text-fill:#a78bfa;" +
                "-fx-background-radius:8;-fx-padding:8 16 8 16;-fx-cursor:hand;" +
                "-fx-border-color:#6c63ff55;-fx-border-radius:8;-fx-font-size:13px;"
            );
        } else {
            btn.setStyle(
                "-fx-background-color:#ffffff0a;-fx-text-fill:#666688;" +
                "-fx-background-radius:8;-fx-padding:8 16 8 16;-fx-cursor:hand;" +
                "-fx-font-size:13px;"
            );
        }
    }
}
