package io.xpipe.app.core.window;

import io.xpipe.app.comp.Comp;
import io.xpipe.app.core.AppCache;
import io.xpipe.app.core.AppProperties;
import io.xpipe.app.core.AppTheme;
import io.xpipe.app.core.mode.OperationMode;
import io.xpipe.app.issue.ErrorEvent;
import io.xpipe.app.issue.TrackEvent;
import io.xpipe.app.prefs.AppPrefs;
import io.xpipe.app.prefs.CloseBehaviourAlert;
import io.xpipe.app.resources.AppImages;
import io.xpipe.app.util.ThreadHelper;
import io.xpipe.core.process.OsType;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.stage.Screen;
import javafx.stage.Stage;

import lombok.Builder;
import lombok.Getter;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import javax.imageio.ImageIO;

public class AppMainWindow {

    private static AppMainWindow INSTANCE;

    @Getter
    private final Stage stage;

    private final BooleanProperty windowActive = new SimpleBooleanProperty(false);
    private Thread thread;
    private volatile Instant lastUpdate;

    public AppMainWindow(Stage stage) {
        this.stage = stage;
    }

    public static AppMainWindow init(Stage stage) {
        INSTANCE = new AppMainWindow(stage);
        var scene = new Scene(new Region(), -1, -1, false);
        scene.setFill(Color.TRANSPARENT);
        ModifiedStage.prepareStage(stage);
        stage.setScene(scene);
        stage.opacityProperty().bind(AppPrefs.get().windowOpacity());
        AppWindowHelper.addIcons(stage);
        AppWindowHelper.setupStylesheets(stage.getScene());
        return INSTANCE;
    }

    public static AppMainWindow getInstance() {
        return INSTANCE;
    }

    private synchronized void onChange() {
        lastUpdate = Instant.now();
        if (thread == null) {
            thread = ThreadHelper.unstarted(() -> {
                while (true) {
                    var toStop = lastUpdate.plus(Duration.of(1, ChronoUnit.SECONDS));
                    if (Instant.now().isBefore(toStop)) {
                        var toSleep = Duration.between(Instant.now(), toStop);
                        if (!toSleep.isNegative()) {
                            var ms = toSleep.toMillis();
                            ThreadHelper.sleep(ms);
                        }
                    } else {
                        break;
                    }
                }

                synchronized (AppMainWindow.this) {
                    logChange();
                    thread = null;
                }
            });
            thread.start();
        }
    }

    private void logChange() {
        TrackEvent.withDebug("Window resize")
                .tag("x", stage.getX())
                .tag("y", stage.getY())
                .tag("width", stage.getWidth())
                .tag("height", stage.getHeight())
                .tag("maximized", stage.isMaximized())
                .build()
                .handle();
    }

    private void initializeWindow(WindowState state) {
        applyState(state);

        TrackEvent.withDebug("Window initialized")
                .tag("x", stage.getX())
                .tag("y", stage.getY())
                .tag("width", stage.getWidth())
                .tag("height", stage.getHeight())
                .tag("maximized", stage.isMaximized())
                .build()
                .handle();
    }

    private void setupListeners() {
        AppWindowBounds.fixInvalidStagePosition(stage);
        stage.xProperty().addListener((c, o, n) -> {
            if (windowActive.get()) {
                onChange();
            }
        });
        stage.yProperty().addListener((c, o, n) -> {
            if (windowActive.get()) {
                onChange();
            }
        });
        stage.widthProperty().addListener((c, o, n) -> {
            if (windowActive.get()) {
                onChange();
            }
        });
        stage.heightProperty().addListener((c, o, n) -> {
            if (windowActive.get()) {
                onChange();
            }
        });
        stage.maximizedProperty().addListener((c, o, n) -> {
            if (windowActive.get()) {
                onChange();
            }
        });

        stage.setOnHiding(e -> {
            saveState();
        });

        stage.setOnHidden(e -> {
            windowActive.set(false);
        });

        stage.setOnShown(event -> {
            stage.requestFocus();
        });

        stage.setOnCloseRequest(e -> {
            if (!CloseBehaviourAlert.showIfNeeded()) {
                e.consume();
                return;
            }

            // Close other windows
            Stage.getWindows().stream().filter(w -> !w.equals(stage)).toList().forEach(w -> w.fireEvent(e));
            stage.close();
            OperationMode.onWindowClose();
            e.consume();
        });

        stage.addEventHandler(KeyEvent.KEY_PRESSED, event -> {
            if (new KeyCodeCombination(KeyCode.Q, KeyCombination.SHORTCUT_DOWN).match(event)) {
                stage.close();
                OperationMode.onWindowClose();
                event.consume();
            }
        });

        TrackEvent.debug("Window listeners added");
    }

    private void applyState(WindowState state) {
        if (state != null) {
            stage.setX(state.windowX);
            stage.setY(state.windowY);
            stage.setWidth(state.windowWidth);
            stage.setHeight(state.windowHeight);
            stage.setMaximized(state.maximized);

            TrackEvent.debug("Window loaded saved bounds");
        } else if (!AppProperties.get().isShowcase()) {
            stage.setWidth(1280);
            stage.setHeight(720);
        } else {
            stage.setX(312);
            stage.setY(149);
            stage.setWidth(1296);
            stage.setHeight(759);
        }
    }

    private void saveState() {
        if (!AppPrefs.get().saveWindowLocation().get()) {
            return;
        }

        if (AppProperties.get().isShowcase()) {
            return;
        }

        var newState = WindowState.builder()
                .maximized(stage.isMaximized())
                .windowX((int) stage.getX())
                .windowY((int) stage.getY())
                .windowWidth((int) stage.getWidth())
                .windowHeight((int) stage.getHeight())
                .build();
        AppCache.update("windowState", newState);
    }

    private WindowState loadState() {
        if (!AppPrefs.get().saveWindowLocation().get()) {
            return null;
        }

        if (AppProperties.get().isShowcase()) {
            return null;
        }

        WindowState state = AppCache.getNonNull("windowState", WindowState.class, () -> null);
        if (state == null) {
            return null;
        }

        boolean inBounds = false;
        for (Screen screen : Screen.getScreens()) {
            Rectangle2D visualBounds = screen.getVisualBounds();
            // Check whether the bounds intersect where the intersection is larger than 20 pixels!
            if (state.windowWidth > 40
                    && state.windowHeight > 40
                    && visualBounds.intersects(new Rectangle2D(
                            state.windowX + 20, state.windowY + 20, state.windowWidth - 40, state.windowHeight - 40))) {
                inBounds = true;
                break;
            }
        }
        return inBounds ? state : null;
    }

    public void initialize() {
        stage.setMinWidth(550);
        stage.setMinHeight(400);

        var state = loadState();
        initializeWindow(state);
        setupListeners();
        windowActive.set(true);
        TrackEvent.debug("Window set to active");
    }

    public void show() {
        stage.show();
        if (OsType.getLocal() == OsType.WINDOWS) {
            NativeWinWindowControl.MAIN_WINDOW = new NativeWinWindowControl(stage);
        }
    }

    private void setupContent(Comp<?> content) {
        var contentR = content.createRegion();
        stage.getScene().setRoot(contentR);
        AppTheme.initThemeHandlers(stage);
        TrackEvent.debug("Set content scene");

        contentR.prefWidthProperty().bind(stage.getScene().widthProperty());
        contentR.prefHeightProperty().bind(stage.getScene().heightProperty());

        if (OsType.getLocal().equals(OsType.LINUX) || OsType.getLocal().equals(OsType.MACOS)) {
            stage.getScene().addEventHandler(KeyEvent.KEY_PRESSED, event -> {
                if (new KeyCodeCombination(KeyCode.W, KeyCombination.SHORTCUT_DOWN).match(event)) {
                    OperationMode.onWindowClose();
                    event.consume();
                }
            });
        }

        stage.getScene().addEventHandler(KeyEvent.KEY_PRESSED, event -> {
            if (AppProperties.get().isDeveloperMode() && event.getCode().equals(KeyCode.F6)) {
                var newR = content.createRegion();
                stage.getScene().setRoot(newR);
                AppTheme.initThemeHandlers(stage);
                newR.requestFocus();

                TrackEvent.debug("Rebuilt content");
                event.consume();
            }

            if (AppProperties.get().isShowcase() && event.getCode().equals(KeyCode.F12)) {
                var image = stage.getScene().snapshot(null);
                var awt = AppImages.toAwtImage(image);
                var file = Path.of(System.getProperty("user.home"), "Desktop", "xpipe-screenshot.png");
                try {
                    ImageIO.write(awt, "png", file.toFile());
                } catch (IOException e) {
                    ErrorEvent.fromThrowable(e).handle();
                }
                TrackEvent.debug("Screenshot taken");
                event.consume();
            }
        });
        TrackEvent.debug("Set content reload listener");
    }

    public void setContent(Comp<?> content) {
        setupContent(content);
    }

    @Builder
    @Jacksonized
    @Value
    private static class WindowState {
        boolean maximized;
        int windowX;
        int windowY;
        int windowWidth;
        int windowHeight;
    }
}
