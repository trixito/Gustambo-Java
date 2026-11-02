// SPDX-FileCopyrightText: Copyright (C) 2021 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.main;

import com.jfoenix.controls.JFXPopup;
import javafx.beans.property.ReadOnlyObjectProperty;
import org.jackhuang.hmcl.game.ModpackHelper;
import org.jackhuang.hmcl.setting.Accounts;
import org.jackhuang.hmcl.setting.GameDirectoryManager;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.animation.AnimationUtils;
import org.jackhuang.hmcl.ui.account.AccountAdvancedListItem;
import org.jackhuang.hmcl.ui.account.AccountListPopupMenu;
import org.jackhuang.hmcl.ui.construct.AdvancedListBox;
import org.jackhuang.hmcl.ui.construct.AdvancedListItem;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane;
import org.jackhuang.hmcl.ui.decorator.DecoratorAnimatedPage;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jackhuang.hmcl.ui.download.ModpackInstallWizardProvider;
import org.jackhuang.hmcl.ui.instances.Instances;
import org.jackhuang.hmcl.ui.nbt.NBTEditorPage;
import org.jackhuang.hmcl.ui.nbt.NBTFileType;
import org.jackhuang.hmcl.upgrade.UpdateChecker;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.io.FileUtils;

import java.nio.file.Path;
import java.util.Locale;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

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
            FXUtils.applyDragListener(mainPage,
                    file -> ModpackHelper.isFileModpackByExtension(file) || NBTFileType.isNBTFileByExtension(file) || "json".equalsIgnoreCase(FileUtils.getExtension(file)),
                    modpacks -> {
                        Path file = modpacks.get(0);
                        if (ModpackHelper.isFileModpackByExtension(file)) {
                            Controllers.getDecorator().startWizard(
                                    new ModpackInstallWizardProvider(GameDirectoryManager.getSelectedRepository(), file),
                                    i18n("install.modpack"));
                        } else if (NBTFileType.isNBTFileByExtension(file)) {
                            try {
                                Controllers.navigate(new NBTEditorPage(file));
                            } catch (Throwable e) {
                                LOG.warning("Fail to open nbt file", e);
                                Controllers.dialog(i18n("nbt.open.failed") + "\n\n" + StringUtils.getStackTrace(e),
                                        i18n("message.error"), MessageDialogPane.MessageType.ERROR);
                            }
                        } else if ("json".equalsIgnoreCase(FileUtils.getExtension(file))) {
                            Instances.installFromJson(GameDirectoryManager.getSelectedRepository(), file);
                        }
                    });

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

            // first item in left sidebar
            AccountAdvancedListItem accountListItem = new AccountAdvancedListItem();
            accountListItem.setOnAction(e -> Controllers.navigate(Controllers.getAccountListPage()));
            FXUtils.onSecondaryButtonClicked(accountListItem, () -> AccountListPopupMenu.show(accountListItem, JFXPopup.PopupVPosition.TOP, JFXPopup.PopupHPosition.LEFT, accountListItem.getWidth(), 0));
            accountListItem.accountProperty().bind(Accounts.selectedAccountProperty());

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
