package io.xpipe.app.browser;

import io.xpipe.app.browser.fs.OpenFileSystemModel;
import io.xpipe.app.fxcomps.Comp;
import io.xpipe.app.fxcomps.CompStructure;
import io.xpipe.app.fxcomps.impl.TextFieldComp;
import io.xpipe.app.fxcomps.impl.TooltipAugment;
import io.xpipe.app.util.InputHelper;

import javafx.beans.property.Property;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.HBox;

import atlantafx.base.theme.Styles;
import org.kordamp.ikonli.javafx.FontIcon;

public class BrowserFilterComp extends Comp<BrowserFilterComp.Structure> {

    private final OpenFileSystemModel model;
    private final Property<String> filterString;

    public BrowserFilterComp(OpenFileSystemModel model, Property<String> filterString) {
        this.model = model;
        this.filterString = filterString;
    }

    @Override
    public Structure createBase() {
        var expanded = new SimpleBooleanProperty();
        var text = new TextFieldComp(filterString, false).createStructure().get();
        var button = new Button();
        button.minWidthProperty().bind(button.heightProperty());
        button.setFocusTraversable(true);
        InputHelper.onExactKeyCode(text, KeyCode.ESCAPE, true, keyEvent -> {
            text.clear();
            button.fire();
            keyEvent.consume();
        });
        new TooltipAugment<>("app.search", new KeyCodeCombination(KeyCode.F, KeyCombination.SHORTCUT_DOWN))
                .augment(button);
        text.focusedProperty().addListener((observable, oldValue, newValue) -> {
            if (!newValue && filterString.getValue() == null) {
                if (button.isFocused()) {
                    return;
                }

                expanded.set(false);
            }
        });
        filterString.addListener((observable, oldValue, newValue) -> {
            if (newValue == null && !text.isFocused()) {
                expanded.set(false);
            }
        });
        text.setMinWidth(0);
        Styles.toggleStyleClass(text, Styles.LEFT_PILL);

        filterString.subscribe(val -> {
            if (val == null) {
                text.getStyleClass().remove(Styles.SUCCESS);
            } else {
                text.getStyleClass().add(Styles.SUCCESS);
            }
        });

        var fi = new FontIcon("mdi2m-magnify");
        button.setGraphic(fi);
        button.setOnAction(event -> {
            if (model.getCurrentDirectory() == null) {
                return;
            }

            if (expanded.get()) {
                if (filterString.getValue() == null) {
                    expanded.set(false);
                }
                event.consume();
            } else {
                expanded.set(true);
                text.requestFocus();
                event.consume();
            }
        });

        var box = new HBox(text, button);
        box.getStyleClass().add("browser-filter");
        box.setAlignment(Pos.CENTER);

        text.setPrefWidth(0);
        text.setFocusTraversable(false);
        button.getStyleClass().add(Styles.FLAT);
        button.disableProperty().bind(model.getInOverview());
        expanded.addListener((observable, oldValue, val) -> {
            if (val) {
                text.setPrefWidth(250);
                text.setFocusTraversable(true);
                button.getStyleClass().add(Styles.RIGHT_PILL);
                button.getStyleClass().remove(Styles.FLAT);
            } else {
                text.setPrefWidth(0);
                text.setFocusTraversable(false);
                button.getStyleClass().remove(Styles.RIGHT_PILL);
                button.getStyleClass().add(Styles.FLAT);
            }
        });
        button.minHeightProperty().bind(text.heightProperty());
        button.minWidthProperty().bind(text.heightProperty());
        button.maxHeightProperty().bind(text.heightProperty());
        button.maxWidthProperty().bind(text.heightProperty());
        return new Structure(box, text, button);
    }

    public record Structure(HBox box, TextField textField, Button toggleButton) implements CompStructure<HBox> {

        @Override
        public HBox get() {
            return box;
        }
    }
}
