// SPDX-FileCopyrightText: Copyright (C) 2021 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.main;

import javafx.beans.property.ReadOnlyObjectProperty;
import org.jackhuang.hmcl.gustambo.GustamboAccount;
import org.jackhuang.hmcl.setting.Accounts;
import org.jackhuang.hmcl.setting.GameDirectoryManager;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.animation.AnimationUtils;
import org.jackhuang.hmcl.ui.construct.AdvancedListBox;
import org.jackhuang.hmcl.ui.construct.AdvancedListItem;
import org.jackhuang.hmcl.ui.decorator.DecoratorAnimatedPage;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jackhuang.hmcl.upgrade.UpdateChecker;

import java.util.Locale;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class RootPage extends DecoratorAnimatedPage implements DecoratorPage {
    private MainPage mainPage = null;

    public RootPage() {
        getStyleClass().remove("gray-background");
        getLeft().getStyleClass().add("gray-background");
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

    /// Builds sidebar navigation and registers optional page preparation on its entries.
    private static class Skin extends DecoratorAnimatedPageSkin<RootPage> {

        /// Creates navigation entries, their actions, and one-shot hover preparation registrations.
        protected Skin(RootPage control) {
            super(control);

            // first item in left sidebar: GustamboClient solo usa cuenta offline (nick del jugador)
            AdvancedListItem accountListItem = new AdvancedListItem();
            accountListItem.setLeftIcon(SVG.PERSON);
            accountListItem.setSubtitle("Toca para cambiar tu nombre");
            accountListItem.setOnAction(e -> GustamboAccount.askNickname());
            FXUtils.onChangeAndOperate(Accounts.selectedAccountProperty(),
                    account -> accountListItem.setTitle(account == null ? "Elegir nombre" : account.getProfileName()));

            // settings item in left sidebar
            AdvancedListItem launcherSettingsItem = new AdvancedListItem();
            launcherSettingsItem.setLeftIcon(SVG.SETTINGS);
            launcherSettingsItem.setTitle(i18n("settings"));
            launcherSettingsItem.setOnAction(e -> {
                Controllers.getSettingsPage().showGameSettings(GameDirectoryManager.getSelectedRepository());
                Controllers.navigate(Controllers.getSettingsPage());
            });

            if (AnimationUtils.isAnimationEnabled()) {
                launcherSettingsItem.setPrepareAction(Controllers::prepareSettingsPage);
            }

            // the left sidebar: GustamboClient only keeps accounts and settings
            AdvancedListBox sideBar = new AdvancedListBox()
                    .startCategory(i18n("account").toUpperCase(Locale.ROOT))
                    .add(accountListItem)
                    .startCategory(i18n("settings.launcher.general").toUpperCase(Locale.ROOT))
                    .add(launcherSettingsItem);

            // the root page, with the sidebar in left, navigator in center.
            setLeft(sideBar);
            setCenter(getSkinnable().getMainPage());
        }
    }

}
