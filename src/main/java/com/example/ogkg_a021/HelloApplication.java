package com.example.ogkg_a021;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Stack;

public class HelloApplication extends Application {
    private List<Point> points = new ArrayList<>();
    private Canvas canvas = new Canvas();
    private boolean manualMode = false;
    private double zoomFactor = 1.0;
    private List<Long> buildTimes = new ArrayList<>();
    private List<Integer> pointCounts = new ArrayList<>();

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        BorderPane root = new BorderPane();
        HBox controls = new HBox(10);
        controls.setPadding(new Insets(10));

        Label label = new Label("Number of points:");
        TextField textField = new TextField();
        textField.setPrefWidth(100);

        Button generateButton = new Button("Generate Points");
        Button manualButton = new Button("Manual Mode");
        Button clearButton = new Button("Clear Canvas");
        Button zoomInButton = new Button("Zoom In");
        Button zoomOutButton = new Button("Zoom Out");
        Button showGraphButton = new Button("Show Graph");

        generateButton.setOnAction(e -> {
            int count;
            try {
                count = Integer.parseInt(textField.getText());
            } catch (NumberFormatException ex) {
                count = 100; // Default value if input is invalid
            }
            generateRandomPoints(count);
            long startTime = System.nanoTime();
            draw();
            long endTime = System.nanoTime();
            long duration = (endTime - startTime) / 1000000; // time in milliseconds
            buildTimes.add(duration);
            pointCounts.add(count);
            System.out.println("Build time: " + duration + " ms");
        });

        manualButton.setOnAction(e -> {
            manualMode = !manualMode;
            if (manualMode) {
                manualButton.setText("Exit Manual Mode");
            } else {
                manualButton.setText("Manual Mode");
            }
        });

        clearButton.setOnAction(e -> {
            points.clear();
            buildTimes.clear();
            pointCounts.clear();
            draw();
        });

        zoomInButton.setOnAction(e -> {
            zoomFactor *= 1.2;
            draw();
        });

        zoomOutButton.setOnAction(e -> {
            zoomFactor /= 1.2;
            draw();
        });

        showGraphButton.setOnAction(e -> {
            showBuildTimeGraph();
        });

        controls.getChildren().addAll(label, textField, generateButton, manualButton, clearButton, zoomInButton, zoomOutButton, showGraphButton);
        root.setTop(controls);
        root.setCenter(canvas);

        Scene scene = new Scene(root, 1024, 768);

        canvas.setOnMouseClicked(this::handleMouseClick);

        primaryStage.setTitle("Convex Hull and Inscribed Circle");
        primaryStage.setScene(scene);
        primaryStage.setMaximized(true);
        primaryStage.show();

        canvas.widthProperty().bind(scene.widthProperty());
        canvas.heightProperty().bind(scene.heightProperty());
    }

    private void handleMouseClick(MouseEvent event) {
        if (manualMode) {
            points.add(new Point(event.getX(), event.getY()));
            draw();
        }
    }

    private void generateRandomPoints(int count) {
        Random random = new Random();
        points.clear();
        for (int i = 0; i < count; i++) {
            points.add(new Point(random.nextDouble() * canvas.getWidth(), random.nextDouble() * canvas.getHeight()));
        }
    }

    private void draw() {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());

        gc.save();
        gc.scale(zoomFactor, zoomFactor);

        gc.setFill(Color.BLACK);
        for (Point point : points) {
            gc.fillOval(point.x - 2, point.y - 2, 4, 4);
        }

        if (points.size() < 3) {
            gc.restore();
            return;
        }

        List<Point> hull = computeConvexHull(points);
        gc.setStroke(Color.RED);
        gc.setLineWidth(2 / zoomFactor);
        for (int i = 0; i < hull.size(); i++) {
            Point p1 = hull.get(i);
            Point p2 = hull.get((i + 1) % hull.size());
            gc.strokeLine(p1.x, p1.y, p2.x, p2.y);
        }

        Circle inscribedCircle = findLargestInscribedCircle(hull);
        if (inscribedCircle != null) {
            gc.setStroke(Color.BLUE);
            gc.setLineWidth(2 / zoomFactor);
            gc.strokeOval(inscribedCircle.center.x - inscribedCircle.radius, inscribedCircle.center.y - inscribedCircle.radius, inscribedCircle.radius * 2, inscribedCircle.radius * 2);
            System.out.println("Inscribed Circle: Center = (" + inscribedCircle.center.x + ", " + inscribedCircle.center.y + "), Radius = " + inscribedCircle.radius);
        }

        gc.restore();

        // Виведення координат точок у консоль
        System.out.println("Points:");
        for (Point point : points) {
            System.out.println("(" + point.x + ", " + point.y + ")");
        }
    }

    private List<Point> computeConvexHull(List<Point> points) {
        if (points.size() < 3) return new ArrayList<>(points);

        points.sort(Point::compareTo);
        Stack<Point> lowerHull = new Stack<>();
        for (Point p : points) {
            while (lowerHull.size() >= 2 && cross(lowerHull.get(lowerHull.size() - 2), lowerHull.peek(), p) <= 0) {
                lowerHull.pop();
            }
            lowerHull.push(p);
        }

        Stack<Point> upperHull = new Stack<>();
        for (int i = points.size() - 1; i >= 0; i--) {
            Point p = points.get(i);
            while (upperHull.size() >= 2 && cross(upperHull.get(upperHull.size() - 2), upperHull.peek(), p) <= 0) {
                upperHull.pop();
            }
            upperHull.push(p);
        }

        lowerHull.pop();
        upperHull.pop();
        lowerHull.addAll(upperHull);

        return new ArrayList<>(lowerHull);
    }

    private double cross(Point o, Point a, Point b) {
        return (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x);
    }

    private Circle findLargestInscribedCircle(List<Point> hull) {
        double maxRadius = 0;
        Point bestCenter = null;

        for (int i = 0; i < hull.size(); i++) {
            Point p1 = hull.get(i);
            Point p2 = hull.get((i + 1) % hull.size());
            for (int j = i + 1; j < hull.size(); j++) {
                Point p3 = hull.get(j);
                Point center = findCircleCenter(p1, p2, p3);
                if (center != null && isInsideHull(center, hull)) {
                    double radius = computeMinimumDistance(center, hull);
                    if (radius > maxRadius) {
                        maxRadius = radius;
                        bestCenter = center;
                    }
                }
            }
        }

        if (bestCenter == null) {
            return null;
        }

        return new Circle(bestCenter, maxRadius);
    }

    private Point findCircleCenter(Point p1, Point p2, Point p3) {
        double ax = p1.x;
        double ay = p1.y;
        double bx = p2.x;
        double by = p2.y;
        double cx = p3.x;
        double cy = p3.y;

        double d = 2 * (ax * (by - cy) + bx * (cy - ay) + cx * (ay - by));
        if (d == 0) {
            return null;
        }

        double ux = ((ax * ax + ay * ay) * (by - cy) + (bx * bx + by * by) * (cy - ay) + (cx * cx + cy * cy) * (ay - by)) / d;
        double uy = ((ax * ax + ay * ay) * (cx - bx) + (bx * bx + by * by) * (ax - cx) + (cx * cx + cy * cy) * (bx - ax)) / d;

        return new Point(ux, uy);
    }

    private boolean isInsideHull(Point p, List<Point> hull) {
        for (int i = 0; i < hull.size(); i++) {
            Point p1 = hull.get(i);
            Point p2 = hull.get((i + 1) % hull.size());
            if (cross(p1, p2, p) < 0) {
                return false;
            }
        }
        return true;
    }

    private double computeMinimumDistance(Point p, List<Point> hull) {
        double minDistance = Double.MAX_VALUE;
        for (int i = 0; i < hull.size(); i++) {
            Point v1 = hull.get(i);
            Point v2 = hull.get((i + 1) % hull.size());
            double distance = pointToSegmentDistance(p, v1, v2);
            if (distance < minDistance) {
                minDistance = distance;
            }
        }
        return minDistance;
    }

    private double pointToSegmentDistance(Point p, Point v1, Point v2) {
        double dx = v2.x - v1.x;
        double dy = v2.y - v1.y;
        double d = Math.sqrt(dx * dx + dy * dy);
        dx /= d;
        dy /= d;
        double projection = (p.x - v1.x) * dx + (p.y - v1.y) * dy;
        if (projection < 0) {
            return Math.sqrt((p.x - v1.x) * (p.x - v1.x) + (p.y - v1.y) * (p.y - v1.y));
        } else if (projection > d) {
            return Math.sqrt((p.x - v2.x) * (p.x - v2.x) + (p.y - v2.y) * (p.y - v2.y));
        } else {
            double nearestX = v1.x + projection * dx;
            double nearestY = v1.y + projection * dy;
            return Math.sqrt((p.x - nearestX) * (p.x - nearestX) + (p.y - nearestY) * (p.y - nearestY));
        }
    }

    private void showBuildTimeGraph() {
        Stage graphStage = new Stage();
        graphStage.setTitle("Build Time Graph");

        NumberAxis xAxis = new NumberAxis();
        xAxis.setLabel("Number of Points");

        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Build Time (ms)");

        LineChart<Number, Number> lineChart = new LineChart<>(xAxis, yAxis);
        lineChart.setTitle("Build Time vs Number of Points");

        XYChart.Series<Number, Number> series = new XYChart.Series<>();
        series.setName("Build Time");

        for (int i = 0; i < pointCounts.size(); i++) {
            series.getData().add(new XYChart.Data<>(pointCounts.get(i), buildTimes.get(i)));
        }

        lineChart.getData().add(series);

        VBox vbox = new VBox(lineChart);
        Scene scene = new Scene(vbox, 800, 600);
        graphStage.setScene(scene);
        graphStage.show();
    }

    static class Point implements Comparable<Point> {
        double x, y;

        Point(double x, double y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public int compareTo(Point other) {
            if (this.x != other.x) return Double.compare(this.x, other.x);
            return Double.compare(this.y, other.y);
        }
    }

    static class Circle {
        Point center;
        double radius;

        Circle(Point center, double radius) {
            this.center = center;
            this.radius = radius;
        }
    }
}
