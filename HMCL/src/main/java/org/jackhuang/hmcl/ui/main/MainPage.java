// SPDX-FileCopyrightText: Copyright (C) 2021 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.main;

import com.jfoenix.controls.JFXButton;
import javafx.beans.property.*;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;
import org.jackhuang.hmcl.game.HMCLGameInstance;
import org.jackhuang.hmcl.game.HMCLGameRepository;
import org.jackhuang.hmcl.game.HMCLGameRepositorySnapshot;
import org.jackhuang.hmcl.gustambo.GustamboAccount;
import org.jackhuang.hmcl.gustambo.GustamboModpack;
import org.jackhuang.hmcl.setting.GameDirectoryManager;
import org.jackhuang.hmcl.theme.Themes;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jackhuang.hmcl.ui.instances.Instances;
import org.jackhuang.hmcl.upgrade.RemoteVersion;
import org.jackhuang.hmcl.util.javafx.BindingMapping;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnmodifiableView;

/// Pantalla principal: fondo azul oscuro, logo grande de la instancia y botón JUGAR.
public final class MainPage extends StackPane implements DecoratorPage {
    private static final String ACCENT = "#2f8cff";
    private static final String ACCENT_TEXT = "#4aa8ff";
    private static final String ACCENT_HOVER = "#4a98ff";

    private final VBox homeView = new VBox();
    private final VBox instanceView = new VBox();
    /// `false` = pantalla de inicio (logo del launcher); `true` = página de la instancia Trixsafio.
    private final BooleanProperty instanceShown = new SimpleBooleanProperty(this, "instanceShown", false);

    public BooleanProperty instanceShownProperty() {
        return instanceShown;
    }

    public void showHome() {
        instanceShown.set(false);
    }

    public void showInstance() {
        instanceShown.set(true);
    }

    private final ReadOnlyObjectWrapper<State> state = new ReadOnlyObjectWrapper<>();

    private final ObjectProperty<@Nullable HMCLGameInstance> currentGame = new SimpleObjectProperty<>(this, "currentGame");
    // Se conservan porque Controllers los enlaza; el actualizador de HMCL ya no se muestra.
    private final BooleanProperty showUpdate = new SimpleBooleanProperty(this, "showUpdate");
    private final BooleanProperty showUpdateDialog = new SimpleBooleanProperty(this, "showUpdateDialog");
    private final ObjectProperty<RemoteVersion> latestVersion = new SimpleObjectProperty<>(this, "latestVersion");

    private final ObservableList<HMCLGameInstance> mutableInstances = FXCollections.observableArrayList();
    private final @UnmodifiableView ObservableList<HMCLGameInstance> instances =
            FXCollections.unmodifiableObservableList(mutableInstances);

    private final ObservableValue<HMCLGameRepositorySnapshot> selectedRepositorySnapshot =
            BindingMapping.of(GameDirectoryManager.selectedRepositoryProperty())
                    .flatMap(HMCLGameRepository::snapshotProperty);

    {
        // ---- barra superior: título a la izquierda; usuario y ajustes a la derecha
        HBox titleNode = new HBox(4);
        titleNode.setAlignment(Pos.CENTER_LEFT);
        titleNode.setMaxWidth(Double.MAX_VALUE);

        Label titleLabel = new Label();
        titleLabel.textProperty().bind(Controllers.launcherTitleProperty());
        titleLabel.getStyleClass().add("jfx-decorator-title");
        titleLabel.textFillProperty().bind(Themes.titleFillProperty());

        Region titleSpacer = new Region();
        HBox.setHgrow(titleSpacer, Priority.ALWAYS);

        JFXButton userButton = new JFXButton();
        userButton.setFocusTraversable(false);
        userButton.setGraphic(SVG.PERSON.createIcon(Themes.titleFillProperty()));
        userButton.getStyleClass().add("jfx-decorator-button");
        userButton.setOnAction(e -> GustamboAccount.askNickname());
        FXUtils.installFastTooltip(userButton, "Cambiar nombre");

        JFXButton settingsButton = new JFXButton();
        settingsButton.setFocusTraversable(false);
        settingsButton.setGraphic(SVG.SETTINGS.createIcon(Themes.titleFillProperty()));
        settingsButton.getStyleClass().add("jfx-decorator-button");
        settingsButton.setOnAction(e -> {
            Controllers.getSettingsPage().showGameSettings(GameDirectoryManager.getSelectedRepository());
            Controllers.navigate(Controllers.getSettingsPage());
        });
        FXUtils.installFastTooltip(settingsButton, "Configuración");

        titleNode.getChildren().setAll(titleLabel, titleSpacer, userButton, settingsButton);
        state.setValue(new State(null, titleNode, false, false, true));

        FXUtils.onChangeAndOperate(selectedRepositorySnapshot,
                ignored -> mutableInstances.setAll(GameDirectoryManager.getSelectedRepository().getDisplayInstances().toList()));

        // ---- fondo: azul muy oscuro con un resplandor suave y franja azul abajo
        setStyle("-fx-background-color: radial-gradient(center 50% 45%, radius 60%, #0b2a63 0%, #050f26 50%, #02060f 100%);");

        Pane stripeLayer = new Pane();
        stripeLayer.setMouseTransparent(true);
        Polygon stripe = new Polygon();
        stripe.setFill(Color.web(ACCENT));
        stripeLayer.getChildren().add(stripe);
        stripeLayer.layoutBoundsProperty().addListener((obs, old, b) -> {
            double w = b.getWidth(), h = b.getHeight();
            stripe.getPoints().setAll(0.0, h, 0.0, h - 4, w, h - 12, w, h);
        });

        // ---- pantalla de inicio: logo de la rata + "Gustambo Launcher"
        ImageView homeLogo = new ImageView(FXUtils.newBuiltinImage(RootPage.LAUNCHER_LOGO, 480, 480, true, true));
        homeLogo.setFitWidth(240);
        homeLogo.setFitHeight(240);
        homeLogo.setPreserveRatio(true);
        Rectangle homeClip = new Rectangle(240, 240);
        homeClip.setArcWidth(72);
        homeClip.setArcHeight(72);
        homeLogo.setClip(homeClip);

        Label homeName = new Label("GUSTAMBO");
        homeName.setStyle("-fx-text-fill: #eef4ff; -fx-font-size: 64px; -fx-font-weight: bold;");
        Label homeSub = new Label("LAUNCHER");
        homeSub.setStyle("-fx-text-fill: " + ACCENT_TEXT + "; -fx-font-size: 54px;");
        VBox homeNames = new VBox(0, homeName, homeSub);
        homeNames.setAlignment(Pos.CENTER_LEFT);

        HBox homeBrand = new HBox(32, homeLogo, homeNames);
        homeBrand.setAlignment(Pos.CENTER);
        homeView.getChildren().setAll(homeBrand);
        homeView.setAlignment(Pos.CENTER);
        homeView.setPickOnBounds(false);

        // ---- pantalla de la instancia: logo T + nombre
        ImageView logo = new ImageView(FXUtils.newBuiltinImage(RootPage.INSTANCE_LOGO, 440, 440, true, true));
        logo.setFitWidth(220);
        logo.setFitHeight(220);
        logo.setPreserveRatio(true);

        Label name = new Label(GustamboModpack.DISPLAY_NAME.toUpperCase());
        name.setStyle("-fx-text-fill: #eef4ff; -fx-font-size: 60px; -fx-font-weight: bold;");
        Label event = new Label("EVENTO");
        event.setStyle("-fx-text-fill: " + ACCENT_TEXT + "; -fx-font-size: 52px;");
        VBox names = new VBox(0, name, event);
        names.setAlignment(Pos.CENTER_LEFT);

        HBox brand = new HBox(28, logo, names);
        brand.setAlignment(Pos.CENTER);

        // ---- estado + botón
        Label status = new Label();
        status.setStyle("-fx-text-fill: #9db4d8; -fx-font-size: 16px;");
        status.textProperty().bind(GustamboModpack.statusProperty());

        Button play = new Button();
        play.setDefaultButton(true);
        play.setPrefSize(560, 60);
        play.setMinWidth(260);
        play.setMaxWidth(640);
        play.setCursor(Cursor.HAND);
        styleButton(play, false);
        play.setOnMouseEntered(e -> styleButton(play, true));
        play.setOnMouseExited(e -> styleButton(play, false));

        Runnable refreshPlay = () -> {
            if (GustamboModpack.updateAvailableProperty().get()) {
                play.setText("A C T U A L I Z A R");
                FXUtils.setOnActionWithCooldown(play, GustamboModpack::promptUpdate);
            } else if (getCurrentGame() == null) {
                play.setText("I N S T A L A R");
                FXUtils.setOnActionWithCooldown(play, GustamboModpack::installNow);
            } else {
                play.setText("J U G A R");
                FXUtils.setOnActionWithCooldown(play, this::launch);
            }
        };
        currentGameProperty().addListener((obs, old, game) -> refreshPlay.run());
        GustamboModpack.updateAvailableProperty().addListener((obs, old, v) -> refreshPlay.run());
        refreshPlay.run();

        VBox action = new VBox(14, status, play);
        action.setAlignment(Pos.CENTER);

        instanceView.getChildren().setAll(brand, action);
        instanceView.setSpacing(26);
        instanceView.setAlignment(Pos.CENTER);
        instanceView.setPadding(new Insets(0, 24, 24, 24));
        instanceView.setPickOnBounds(false);

        homeView.visibleProperty().bind(instanceShown.not());
        instanceView.visibleProperty().bind(instanceShown);

        getChildren().addAll(stripeLayer, homeView, instanceView);
    }

    private static void styleButton(Button button, boolean hover) {
        button.setStyle("-fx-background-color: " + (hover ? ACCENT_HOVER : ACCENT) + ";"
                + "-fx-text-fill: #02060f; -fx-font-size: 18px; -fx-font-weight: bold;"
                + "-fx-background-radius: 16; -fx-cursor: hand;");
    }

    private void launch() {
        if (GustamboModpack.updateAvailableProperty().get()) { // actualización obligatoria
            GustamboModpack.promptUpdate();
            return;
        }
        HMCLGameRepository repository = GameDirectoryManager.getSelectedRepository();
        Instances.launch(repository.getSelectedInstance());
    }

    @Override
    public ReadOnlyObjectWrapper<State> stateProperty() {
        return state;
    }

    public @Nullable HMCLGameInstance getCurrentGame() {
        return currentGame.get();
    }

    public ObjectProperty<@Nullable HMCLGameInstance> currentGameProperty() {
        return currentGame;
    }

    public void setCurrentGame(@Nullable HMCLGameInstance currentGame) {
        this.currentGame.set(currentGame);
    }

    public @UnmodifiableView ObservableList<HMCLGameInstance> getInstances() {
        return instances;
    }

    public boolean isShowUpdate() {
        return showUpdate.get();
    }

    public BooleanProperty showUpdateProperty() {
        return showUpdate;
    }

    public void setShowUpdate(boolean showUpdate) {
        this.showUpdate.set(showUpdate);
    }

    public boolean isShowUpdateDialog() {
        return showUpdateDialog.get();
    }

    public BooleanProperty showUpdateDialogProperty() {
        return showUpdateDialog;
    }

    public void setShowUpdateDialog(boolean showUpdateDialog) {
        this.showUpdateDialog.set(showUpdateDialog);
    }

    public RemoteVersion getLatestVersion() {
        return latestVersion.get();
    }

    public ObjectProperty<RemoteVersion> latestVersionProperty() {
        return latestVersion;
    }

    public void setLatestVersion(RemoteVersion latestVersion) {
        this.latestVersion.set(latestVersion);
    }
}
