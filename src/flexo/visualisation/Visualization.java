package flexo.visualisation;

import flexo.gui.SpherePropertiesController;
import flexo.model.Scene;
import flexo.modelconverter.ModelConverter;
import flexo.scenebuilder.SceneBuilder;
import flexo.scenebuilder.TwoDimensionBuilder;
import javafx.scene.Group;
import javafx.scene.ParallelCamera;
import javafx.scene.PointLight;
import javafx.scene.SubScene;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Sphere;
import javafx.scene.transform.Rotate;
import javafx.scene.transform.Transform;
import javafx.scene.transform.Translate;

import java.util.LinkedList;
import java.util.List;

/**
 * Created by kcpr on 12.06.15.
 */
public class Visualization {

    double anchorX, anchorY, lastX, lastY;

    ParallelCamera camera = new ParallelCamera();//PerspectiveCamera(true);
    int radius = 20;
    int visualisationMultiplicant = 10;

    Sphere selectedSphere;

    public Visualization(Pane pane, SubScene subScene, SpherePropertiesController spherePropertiesController) {

        SceneBuilder builder = new TwoDimensionBuilder();
        builder.setNodesNumber(10);
        Scene scene = builder.build();

//        camera.setTranslateZ(-1000);
        camera.setFarClip(Double.MAX_VALUE);
        camera.setNearClip(Double.MIN_VALUE);

        camera.getTransforms().add(new Translate());

        subScene.setCamera(camera);


        List<javafx.scene.Node> visualisedObjects = createVisualisedObjects(scene, radius, visualisationMultiplicant, spherePropertiesController);
        final Group root = new Group(visualisedObjects);

        subScene.setRoot(root);

//        pane.getChildren().setAll(root);


//        final Scene scene = new Scene(root, 500, 500, true);

        pane.setOnMousePressed(event -> {
            anchorX = event.getSceneX();
            anchorY = event.getSceneY();

            lastX = camera.getTranslateX();
            lastY = camera.getTranslateY();
//          anchorAngle = parent.getRotate();
        });
//
        pane.setOnMouseDragged(event -> {

            Transform transform = camera.getTransforms().get(0);

            if (event.isPrimaryButtonDown()) {
                transform = transform.createConcatenation(new Translate(anchorX - event.getSceneX(), anchorY - event.getSceneY()));
            }

            if (event.isSecondaryButtonDown()) {
                transform = transform.createConcatenation(new Rotate((anchorX - event.getSceneX()), 450, 0, 0, Rotate.Y_AXIS));
                transform = transform.createConcatenation(new Rotate((anchorY - event.getSceneY()), 0, 150, 0, Rotate.X_AXIS));
            }

            camera.getTransforms().set(0, transform);

            anchorX = event.getSceneX();
            anchorY = event.getSceneY();

        });

        pane.setOnScroll(event -> {
            Transform transform = camera.getTransforms().get(0);
            transform = transform.createConcatenation(new Translate(0, 0, event.getDeltaY()));
            camera.getTransforms().set(0, transform);
//                group.getCamera().setTranslateZ(red.getCamera().getTranslateZ() + event.getDeltaY());
//                    root.setTranslateZ(root.getTranslateZ() + event.getDeltaY());
        });

        PointLight pointLight = new PointLight(Color.ANTIQUEWHITE);
        pointLight.setTranslateX(15);
        pointLight.setTranslateY(-10);
        pointLight.setTranslateZ(-100);

//        group.getChildren().add(pointLight);

    }

    private List<javafx.scene.Node> createVisualisedObjects(Scene scene, int radius, int multiplicant, SpherePropertiesController spherePropertiesController) {
        List<flexo.model.Node> list = ModelConverter.convert(scene);

        final PhongMaterial blackMaterial = new PhongMaterial(Color.BLACK);
        blackMaterial.setSpecularColor(Color.WHITE);

        final PhongMaterial redMaterial = new PhongMaterial(Color.RED);
        redMaterial.setSpecularColor(Color.WHITE);

        List<javafx.scene.Node> visibleObjects = new LinkedList<>();
        for (flexo.model.Node node : list) {
            Sphere sphere = new Sphere(radius);
            sphere.setMaterial(blackMaterial);
            sphere.setTranslateX(node.getX() * multiplicant);
            sphere.setTranslateY(node.getY() * multiplicant);
            sphere.setTranslateZ(node.getZ() * multiplicant);

            sphere.setOnMouseClicked(event -> {
                if (selectedSphere != null) {
                    selectedSphere.setMaterial(blackMaterial);
                }
                selectedSphere = sphere;
                sphere.setMaterial(redMaterial); // [TODO] Find out why 'sphere.setEffect()' doesn't seem to work

                spherePropertiesController.setSelectedNode(node);

                spherePropertiesController.setId(node.getId());
                spherePropertiesController.setParameter(node.getParameter());
                spherePropertiesController.setX(node.getX());
                spherePropertiesController.setY(node.getY());
                spherePropertiesController.setZ(node.getZ());
                spherePropertiesController.setVisible(true);
            });

            visibleObjects.add(sphere);
        }
        final PhongMaterial greyMaterial = new PhongMaterial();
        greyMaterial.setDiffuseColor(Color.GREY);
        greyMaterial.setSpecularColor(Color.WHITE);

        Sphere sphere = new Sphere(radius);
        sphere.setMaterial(greyMaterial);
        sphere.setTranslateX(scene.getCentralNode().getX() * multiplicant);
        sphere.setTranslateY(scene.getCentralNode().getY() * multiplicant);
        sphere.setTranslateZ(scene.getCentralNode().getZ() * multiplicant);

        visibleObjects.add(sphere);
        return visibleObjects;
    }
}
