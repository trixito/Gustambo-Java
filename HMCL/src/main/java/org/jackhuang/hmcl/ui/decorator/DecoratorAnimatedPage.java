// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.decorator;

import javafx.scene.Node;
import javafx.scene.control.Control;
import javafx.scene.control.Skin;
import javafx.scene.control.SkinBase;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.ui.FXUtils;

public class DecoratorAnimatedPage extends Control {

    protected final VBox left = new VBox();
    protected final StackPane center = new StackPane();

    {
        getStyleClass().add("gray-background");
    }

    protected void setLeft(Node... children) {
        left.getChildren().setAll(children);
    }

    protected void setCenter(Node... children) {
        center.getChildren().setAll(children);
    }

    /// Ancho de la barra lateral; las subclases pueden cambiarlo.
    protected double getLeftWidth() {
        return 200;
    }

    public VBox getLeft() {
        return left;
    }

    public StackPane getCenter() {
        return center;
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new DecoratorAnimatedPageSkin<>(this);
    }

    public static class DecoratorAnimatedPageSkin<T extends DecoratorAnimatedPage> extends SkinBase<T> {

        protected DecoratorAnimatedPageSkin(T control) {
            super(control);

            BorderPane pane = new BorderPane();
            pane.setLeft(control.left);
            FXUtils.setLimitWidth(control.left, control.getLeftWidth());
            pane.setCenter(control.center);
            getChildren().setAll(pane);
        }

        protected void setLeft(Node... children) {
            getSkinnable().setLeft(children);
        }

        protected void setCenter(Node... children) {
            getSkinnable().setCenter(children);
        }

    }

}
