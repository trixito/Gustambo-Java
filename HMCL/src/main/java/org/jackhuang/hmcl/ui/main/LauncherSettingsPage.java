// SPDX-FileCopyrightText: Copyright (C) 2021 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.main;

import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import org.jackhuang.hmcl.game.HMCLGameRepository;
import org.jackhuang.hmcl.game.HMCLGameInstance;
import org.jackhuang.hmcl.setting.GameSettings;
import org.jackhuang.hmcl.setting.GameDirectoryManager;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.animation.TransitionPane;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.ui.decorator.DecoratorAnimatedPage;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jackhuang.hmcl.ui.game.GameSettingsPage;


public class LauncherSettingsPage extends DecoratorAnimatedPage implements DecoratorPage, PageAware {
    private final ReadOnlyObjectWrapper<State> state = new ReadOnlyObjectWrapper<>(State.fromTitle("Configuración"));
    /// Manages tab selection and content transitions independently of the sidebar.
    private final TabController tabController;
    /// The tab for editing game settings (memoria, Java, etc.).
    private final TabItem<GameSettingsPage<GameSettings.Preset>> gameTab;
    /// The tab whose content manages installed Java runtimes.
    private final TabItem<JavaManagementPage> javaManagementTab;
    /// The tab displaying launcher information and licenses.
    private final TabItem<AboutPage> aboutTab;
    private final TransitionPane transitionPane = new TransitionPane();

    public LauncherSettingsPage() {
        gameTab = new TabItem<>(() -> new GameSettingsPage<>(GameSettings.Preset.class, null));
        javaManagementTab = new TabItem<>(JavaManagementPage::new);
        aboutTab = new TabItem<>(AboutPage::new);
        tabController = new TabController(transitionPane, gameTab, javaManagementTab, aboutTab);

        tabController.selectTab(gameTab);
        addEventHandler(Navigator.NavigationEvent.NAVIGATED, event -> gameTab.getNode().loadInstance(HMCLGameInstance.Optional.empty(GameDirectoryManager.getSelectedRepository())));

        AdvancedListBox sideBar = new AdvancedListBox()
                .addNavigationDrawerTab(tabController, gameTab, "Juego (RAM y más)", SVG.STADIA_CONTROLLER, SVG.STADIA_CONTROLLER_FILL)
                .addNavigationDrawerTab(tabController, javaManagementTab, "Java", SVG.LOCAL_CAFE, SVG.LOCAL_CAFE_FILL)
                .addNavigationDrawerTab(tabController, aboutTab, "Acerca de", SVG.INFO, SVG.INFO_FILL);
        FXUtils.setLimitWidth(sideBar, 200);
        setLeft(sideBar);

        setCenter(transitionPane);
    }

    @Override
    public void onPageShown() {
        tabController.onPageShown();
    }

    @Override
    public void onPageHidden() {
        tabController.onPageHidden();
    }

    public void showGameSettings(HMCLGameRepository repository) {
        gameTab.getNode().loadInstance(HMCLGameInstance.Optional.empty(repository));
        tabController.selectTab(gameTab, false);
    }

    public void showFeedback() {
        tabController.selectTab(aboutTab, false);
    }

    @Override
    public ReadOnlyObjectProperty<State> stateProperty() {
        return state.getReadOnlyProperty();
    }
}
