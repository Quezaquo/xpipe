package io.xpipe.app.comp.store;

import io.xpipe.app.fxcomps.Comp;
import io.xpipe.app.fxcomps.CompStructure;
import io.xpipe.app.fxcomps.impl.IconButtonComp;
import io.xpipe.app.fxcomps.impl.PrettyImageHelper;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;

import java.util.ArrayList;
import java.util.function.Consumer;

public class StoreQuickAccessButtonComp extends Comp<CompStructure<Button>> {

    private final StoreSection section;
    private final Consumer<StoreEntryWrapper> action;

    public StoreQuickAccessButtonComp(StoreSection section, Consumer<StoreEntryWrapper> action) {
        this.section = section;
        this.action = action;
    }

    private void showMenu(Node anchor) {
        var cm = createMenu();
        if (cm == null) {
            return;
        }

        cm.show(anchor, Side.RIGHT, 0, 0);
    }

    private ContextMenu createMenu() {
        if (section.getShownChildren().isEmpty()) {
            return null;
        }

        var cm = new ContextMenu();
        cm.setAutoHide(true);
        cm.getStyleClass().add("condensed");
        Menu menu = (Menu) recurse(cm, section);
        cm.getItems().addAll(menu.getItems());
        return cm;
    }

    private MenuItem recurse(ContextMenu contextMenu, StoreSection section) {
        var c = section.getShownChildren();
        var w = section.getWrapper();
        var graphic =
                w.getEntry().getProvider().getDisplayIconFileName(w.getEntry().getStore());
        if (c.isEmpty()) {
            var item = new MenuItem(
                    w.getName().getValue(),
                    PrettyImageHelper.ofFixedSizeSquare(graphic, 16).createRegion());
            item.setOnAction(event -> {
                action.accept(w);
                contextMenu.hide();
                event.consume();
            });
            return item;
        }

        var items = new ArrayList<MenuItem>();
        for (StoreSection sub : c) {
            if (!sub.getWrapper().getValidity().getValue().isUsable()) {
                continue;
            }

            items.add(recurse(contextMenu, sub));
        }
        var m = new Menu(
                w.getName().getValue(),
                PrettyImageHelper.ofFixedSizeSquare(graphic, 16).createRegion());
        m.getItems().setAll(items);
        m.setOnAction(event -> {
            if (event.getTarget() == m) {
                action.accept(w);
                contextMenu.hide();
                event.consume();
            }
        });
        return m;
    }

    @Override
    public CompStructure<Button> createBase() {
        var button = new IconButtonComp("mdi2c-chevron-double-right");
        button.apply(struc -> {
            struc.get().setOnAction(event -> {
                showMenu(struc.get());
            });
        });
        return button.createStructure();
    }
}
