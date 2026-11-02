// SPDX-FileCopyrightText: Copyright (C) 2021 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.main;

import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.canvas.Canvas;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import org.jackhuang.hmcl.gustambo.GustamboAccount;
import org.jackhuang.hmcl.gustambo.GustamboModpack;
import org.jackhuang.hmcl.setting.Accounts;
import org.jackhuang.hmcl.game.TexturesLoader;
import org.jackhuang.hmcl.setting.GameDirectoryManager;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.decorator.DecoratorAnimatedPage;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jackhuang.hmcl.upgrade.UpdateChecker;

/// Raíz del launcher: barra lateral estrecha de iconos + pantalla principal.
public class RootPage extends DecoratorAnimatedPage implements DecoratorPage {
    static final String LAUNCHER_LOGO = "/assets/img/launcher.png";
    static final String INSTANCE_LOGO = "/assets/img/trixsafio.png";

    private static final double SIDEBAR_WIDTH = 96;
    private static final String ACCENT = "#2f8cff";

    private MainPage mainPage = null;

    public RootPage() {
        getStyleClass().remove("gray-background");
        getLeft().setStyle("-fx-background-color: #040916;");
    }

    @Override
    protected double getLeftWidth() {
        return SIDEBAR_WIDTH;
    }

    @Override
    public ReadOnlyObjectProperty<State> stateProperty() {
        return getMainPage().stateProperty();
    }

    @Override
    protected Skin createDefaultSkin() {
        return new Skin(this);
    }

    public MainPage getMainPage() {
        if (mainPage == null) {
            MainPage mainPage = new MainPage();
            FXUtils.onChangeAndOperate(GameDirectoryManager.selectedInstanceProperty(), mainPage::setCurrentGame);
            mainPage.latestVersionProperty().bind(UpdateChecker.latestVersionProperty());
            this.mainPage = mainPage;
        }
        return mainPage;
    }

    private static class Skin extends DecoratorAnimatedPageSkin<RootPage> {

        protected Skin(RootPage control) {
            super(control);

            // logo del launcher (arriba)
            MainPage main = control.getMainPage();
            StackPane launcherButton = launcherLogoButton(); // vuelve a la pantalla de inicio
            FXUtils.onClicked(launcherButton, main::showHome);
            FXUtils.installFastTooltip(launcherButton, "Gustambo Launcher");

            // instancia Trixsafio (seleccionada)
            StackPane instanceButton = imageButton(INSTANCE_LOGO, true);
            // abre la página de la instancia: ahí está INSTALAR / JUGAR / ACTUALIZAR
            FXUtils.onClicked(instanceButton, main::showInstance);
            FXUtils.onChangeAndOperate(main.instanceShownProperty(), shown -> {
                setSelected(launcherButton, !shown);
                setSelected(instanceButton, shown);
            });
            FXUtils.installFastTooltip(instanceButton, GustamboModpack.DISPLAY_NAME);

            Region separator = new Region();
            separator.setMinHeight(2);
            separator.setMaxHeight(2);
            separator.setMaxWidth(44);
            separator.setStyle("-fx-background-color: #14233f;");

            Region spacer = new Region();
            VBox.setVgrow(spacer, Priority.ALWAYS);

            // jugador (nick offline) con su skin: toca para cambiar el nombre
            Canvas face = new Canvas(64, 64);
            face.setMouseTransparent(true);
            StackPane avatar = new StackPane(face);
            avatar.setPrefSize(64, 64);
            avatar.setMinSize(64, 64);
            avatar.setMaxSize(64, 64);
            avatar.setCursor(Cursor.HAND);
            avatar.setStyle("-fx-background-color: #0d2a5c; -fx-background-radius: 12;");
            FXUtils.onClicked(avatar, GustamboAccount::askNickname);
            FXUtils.installFastTooltip(avatar, "Toca para cambiar tu nombre");
            FXUtils.onChangeAndOperate(Accounts.selectedAccountProperty(), account -> {
                if (account == null) {
                    TexturesLoader.unbindAvatar(face);
                    TexturesLoader.drawAvatar(face, TexturesLoader.getDefaultSkinImage());
                } else {
                    TexturesLoader.bindAvatar(face, account);
                }
            });

            VBox sideBar = new VBox(14, launcherButton, separator, instanceButton, spacer, avatar);
            sideBar.setAlignment(Pos.TOP_CENTER);
            sideBar.setPadding(new Insets(16, 0, 16, 0));
            VBox.setVgrow(sideBar, Priority.ALWAYS);

            setLeft(sideBar);
            setCenter(getSkinnable().getMainPage());
        }

        /// Botón cuadrado de 64px con una imagen de `/assets/img` y esquinas redondeadas.
        private static void setSelected(StackPane button, boolean selected) {
            button.setStyle(selected
                    ? "-fx-border-color: " + ACCENT + "; -fx-border-width: 2; -fx-border-radius: 12;"
                    : "");
        }

        /// Botón del launcher: cuadrado azul con la rata blanca (sin fondo propio en el PNG).
        private static StackPane launcherLogoButton() {
            ImageView rat = new ImageView(FXUtils.newBuiltinImage(LAUNCHER_LOGO, 112, 112, true, true));
            rat.setFitWidth(44);
            rat.setFitHeight(44);
            rat.setPreserveRatio(true);

            StackPane button = new StackPane(rat);
            button.setPrefSize(64, 64);
            button.setMinSize(64, 64);
            button.setMaxSize(64, 64);
            // El fondo va por Java (no por CSS) para que setSelected solo cambie el borde.
            button.setBackground(new Background(new BackgroundFill(Color.web(ACCENT), new CornerRadii(12), Insets.EMPTY)));
            button.setCursor(Cursor.HAND);
            return button;
        }

        private static StackPane imageButton(String resource, boolean clickable) {
            ImageView view = new ImageView(FXUtils.newBuiltinImage(resource, 128, 128, true, true));
            view.setFitWidth(64);
            view.setFitHeight(64);
            view.setPreserveRatio(true);
            Rectangle clip = new Rectangle(64, 64);
            clip.setArcWidth(20);
            clip.setArcHeight(20);
            view.setClip(clip);

            StackPane button = new StackPane(view);
            button.setPrefSize(64, 64);
            button.setMinSize(64, 64);
            button.setMaxSize(64, 64);
            if (clickable) button.setCursor(Cursor.HAND);
            return button;
        }
    }
}
