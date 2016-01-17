package flexo.gui;

import flexo.model.Connection;
import flexo.model.Setup;
import flexo.model.TypicalNode;
import flexo.model.persistence.SetupExporter;
import flexo.model.persistence.SetupLoader;
import flexo.model.persistence.SetupSaver;
import flexo.model.setupbuilder.SetupBuilder;
import flexo.model.setupbuilder.ThreeDimensionalSetupBuilder;
import flexo.model.setupbuilder.TwoDimensionalSetupBuilder;
import flexo.visualization.SelectionObserver;
import flexo.visualization.Visualization;
import flexo.visualization.VisualizedConnection;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.concurrent.Task;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.layout.Pane;
import javafx.scene.transform.Rotate;
import javafx.scene.transform.Translate;
import javafx.stage.FileChooser;

import java.io.File;
import java.util.Optional;

public class ApplicationController implements SelectionObserver {

    @FXML
    private MenuItem saveMenuItem;

    @FXML
    private MenuItem saveAsMenuItem;

    @FXML
    private MenuItem exportMenuItem;

    @FXML
    private SplitPane splitPane;

    @FXML
    private TitledPane listViewTitledPane;

    @FXML
    private TitledPane propertiesTitledPane;

    @FXML
    private ListView listView;

    @FXML
    private PropertiesController propertiesController;

    @FXML
    private Pane pane;

    double lastDividerPosition;

    private Setup setup;
    private volatile Group root;
    private Visualization visualization;

    private final int X = 0;
    private final int Y = 200;
    private final int Z = -2000;

    double lastX, lastY;
    Translate cameraTranslate = new Translate(X, Y, Z);
    Rotate cameraRotateX = new Rotate(0, 0, Y, 0, Rotate.X_AXIS);
    Rotate cameraRotateY = new Rotate(0, X, 0, 0, Rotate.Y_AXIS);

    String filePath;

    @FXML
    void initialize() {
        listViewTitledPane.expandedProperty().addListener(saveOrRestoreDividerPosition(propertiesTitledPane));
        propertiesTitledPane.expandedProperty().addListener(saveOrRestoreDividerPosition(listViewTitledPane));

        listView.getSelectionModel().selectedIndexProperty().addListener((observable, oldValue, newValue) -> {
            int index = newValue.intValue();
            if (index != -1) {
                VisualizedConnection visualizedConnection = visualization.getVisualizedConnections().get(index);
                if (visualizedConnection != visualization.getSelectedElement()) {
                    visualization.selectElement(visualizedConnection, visualizedConnection.getMaterial());
                    propertiesController.setSelectedConnection(visualizedConnection.getConnection());
                } else {
                    listView.scrollTo(index);
                }
            }
        });

        listView.setCellFactory(param -> new ListCell<Connection>() {
            @Override
            protected void updateItem(Connection item, boolean empty) {
                super.updateItem(item, empty);
                if (item != null) {
                    setText(item.getTypicalNode1().getId() + " - " + item.getTypicalNode2().getId());
                }
            }
        });

        root = new Group();
        root.setRotationAxis(Rotate.X_AXIS);
        root.setRotate(180);

        SubScene subScene = new SubScene(root, 0, 0, true, SceneAntialiasing.BALANCED);
        subScene.heightProperty().bind(pane.heightProperty());
        subScene.widthProperty().bind(pane.widthProperty());
        pane.getChildren().add(subScene);

        PerspectiveCamera camera = new PerspectiveCamera(true);
        camera.setFarClip(Double.MAX_VALUE);
        camera.setNearClip(0.1);
        camera.getTransforms().addAll(cameraRotateY, cameraRotateX, cameraTranslate); // [TODO] Decide which rotation method is better

        subScene.setCamera(camera);
        subScene.setRoot(root);

        pane.setOnMousePressed(event -> {
            if (event.isMiddleButtonDown()) {
                cameraTranslate.setX(X);
                cameraTranslate.setY(Y);
                cameraTranslate.setZ(Z);
                cameraRotateX.setAngle(0);
                cameraRotateY.setAngle(0);
//                selectedSphere = null; // [TODO] Decide how deselection should work
//                propertiesController.setVisible(false);
            } else {
                lastX = event.getSceneX();
                lastY = event.getSceneY();
            }
        });

        pane.setOnMouseDragged(event -> {
            double sceneX = event.getSceneX();
            double sceneY = event.getSceneY();
            double deltaX = lastX - sceneX;
            double deltaY = lastY - sceneY;

            double multiplier = cameraTranslate.getZ() / Z;

            if (event.isPrimaryButtonDown()) {
                cameraTranslate.setX(cameraTranslate.getX() + deltaX * multiplier);
                cameraTranslate.setY(cameraTranslate.getY() + deltaY * multiplier);
            }

            if (event.isSecondaryButtonDown()) {
                cameraRotateX.setAngle(cameraRotateX.getAngle() + deltaY * multiplier);
                cameraRotateY.setAngle(cameraRotateY.getAngle() + deltaX * multiplier);
            }

            lastX = sceneX;
            lastY = sceneY;
        });

        pane.setOnScroll(event -> {
            double multiplier = 2 * cameraTranslate.getZ() / Z;
            cameraTranslate.setZ(cameraTranslate.getZ() + event.getDeltaY() * multiplier);
        });
    }

    private ChangeListener<Boolean> saveOrRestoreDividerPosition(TitledPane anotherTitledPane) {
        return (observable, oldValue, newValue) -> {
            if (newValue) {
                splitPane.setDividerPositions(lastDividerPosition);
            } else if (anotherTitledPane.isExpanded()) {
                lastDividerPosition = splitPane.getDividerPositions()[0];
            }
        };
    }

    @FXML
    private void newTwoDimensionalSetup() {
        newSetup(new TwoDimensionalSetupBuilder(), 3, 10, "two-dimensional setup", "Number of nodes:");
    }

    @FXML
    private void newThreeDimensionalSetup() {
        newSetup(new ThreeDimensionalSetupBuilder(), 2, 10, "three-dimensional setup", "Number of nodes in base:");
    }

    private void newSetup(SetupBuilder setupBuilder, int minimalValue, int defaultValue, String setupTypeName, String contentText) {
        TextInputDialog textInputDialog = new TextInputDialog(Integer.toString(defaultValue)); // [TODO] Think about moving this part to separate method such as 'showIntegerInputDialog'
        textInputDialog.setTitle("New " + setupTypeName);
        textInputDialog.setHeaderText("Create new " + setupTypeName);
        textInputDialog.setContentText(contentText);
        textInputDialog.setGraphic(null);

        textInputDialog.getEditor().setTextFormatter(new TextFormatter<String>(change -> {
            if (change.isAdded() && !change.getText().matches("\\d+")) {
                return null;
            }
            return change;
        }));

        Node button = textInputDialog.getDialogPane().lookupButton(ButtonType.OK);
        textInputDialog.getEditor().textProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue.length() == 0 || newValue.length() > 9 || Integer.parseInt(newValue) < minimalValue) {
                button.setDisable(true);
            } else {
                button.setDisable(false);
            }
        });

        Optional<String> integerInputDialogResult = textInputDialog.showAndWait();
        if (integerInputDialogResult.isPresent()) {
            Task task = runAsTask(() -> {
                setup = setupBuilder.build(Integer.parseInt(integerInputDialogResult.get()));
                visualization = new Visualization(setup, this);
            }, "Creating new setup", "creating new setup");

            EventHandler onSucceeded = task.getOnSucceeded();
            task.setOnSucceeded(event -> {
                onSucceeded.handle(event);
                showVisualizedSetup();
                filePath = null;
                enableMenuItems();
            });
        }
    }

    @FXML
    private void loadSetup() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("XML files", "*.xml", "*.XML"), new FileChooser.ExtensionFilter("All files", "*"));
        File file = fileChooser.showOpenDialog(null);
        if (file != null) {
            Task task = runAsTask(() -> {
                setup = SetupLoader.loadFromXMLFile(file);
                visualization = new Visualization(setup, this);
            }, "Loading setup", "loading setup");

            EventHandler onSucceeded = task.getOnSucceeded();
            task.setOnSucceeded(event -> {
                onSucceeded.handle(event);
                showVisualizedSetup();
                filePath = file.getPath();
                enableMenuItems();
            });
        }
    }

    private void enableMenuItems() {
        saveMenuItem.setDisable(false);
        saveAsMenuItem.setDisable(false);
        exportMenuItem.setDisable(false);
    }

    private void showVisualizedSetup() {
        root.getChildren().clear();
        listView.getItems().setAll(setup.getConnections());
        root.getChildren().addAll(visualization.getVisualisedElements());
        propertiesController.setVisualization(visualization);
    }

    @FXML
    private void saveSetup() {
        if (filePath != null) {
            runAsTask(() -> SetupSaver.saveToXMLFile(setup, new File(filePath)), "Saving setup", "saving setup");
        } else {
            saveSetupAs();
        }
    }

    @FXML
    private void saveSetupAs() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setInitialFileName("Setup.xml");
        fileChooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("XML files", "*.xml", "*.XML"), new FileChooser.ExtensionFilter("All files", "*"));
        File file = fileChooser.showSaveDialog(null);
        if (file != null) {
            runAsTask(() -> {
                SetupSaver.saveToXMLFile(setup, file);
                filePath = file.getPath();
            }, "Saving setup", "saving setup");
        }
    }

    @FXML
    private void exportSetup() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setInitialFileName("Setup.obj");
        fileChooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("OBJ files", "*.obj", "*.OBJ"), new FileChooser.ExtensionFilter("All files", "*"));
        File file = fileChooser.showSaveDialog(null);
        if (file != null) {
            runAsTask(() -> SetupExporter.exportToOBJFile(setup, file), "Exporting setup", "exporting setup");
        }
    }

    @FXML
    private void quit() {
        Platform.exit();
    }

    static Task runAsTask(RunnableWithException runnableWithException, String title, String contentText) {
        Alert alert = new Alert(Alert.AlertType.NONE, title, ButtonType.CANCEL);
        alert.setTitle(title);

        Task task = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                runnableWithException.run();
                return null;
            }
        };

        task.setOnSucceeded(event -> {
            alert.close();
        });

        task.setOnFailed(event -> {
            alert.close();
            Alert errorAlert = new Alert(Alert.AlertType.NONE, "Error occurred while " + contentText, ButtonType.OK);
            errorAlert.setTitle("Error occurred");
            errorAlert.show();
        });

        ((Button) alert.getDialogPane().lookupButton(ButtonType.CANCEL)).setOnAction(event -> {
            task.cancel();
        });

        new Thread(task).start();
        alert.show();

        return task;
    }

    @Override
    public void selectedTypicalNode(TypicalNode typicalNode) {
        propertiesController.setSelectedNode(typicalNode);
        listView.getSelectionModel().clearSelection();
    }

    @Override
    public void selectedConnection(Connection connection) {
        propertiesController.setSelectedConnection(connection);
        listView.getSelectionModel().select(connection);
    }

    interface RunnableWithException {
        void run() throws Exception;
    }

}
