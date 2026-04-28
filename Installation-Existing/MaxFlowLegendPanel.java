// This file is an extension of GrALoG, Copyright (c) 2016-2018 LaS group, TU Berlin.
// This file was created by Clare Reel in 2026

package gralog.gralogfx.panels;

import gralog.rendering.GralogColor;
import javafx.scene.canvas.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.*;

public class MaxFlowLegendPanel extends ScrollPane {

    private VBox scrollPane;
    private static final int ITEM_HEIGHT = 30;
    private static final int CANVAS_WIDTH = 60;
    private static final int VERTEX_RADIUS = 10;

    // true = residual graph view, false = net flow view
    private static boolean showResidualGraph = true;
    private static Runnable onViewChanged = null;

    public static void setViewChangeCallback(Runnable callback) {
        onViewChanged = callback;
    }

    public static boolean isResidualGraphView() {
        return showResidualGraph;
    }

    public MaxFlowLegendPanel() {

        scrollPane = new VBox(3); // 3 spacing
        scrollPane.setStyle("-fx-background-color:transparent;");

        scrollPane.getChildren().add(new Label("")); // spacing
        // Title
        Label title = new Label("    Ford-Fulkerson Algorithm");
        title.setFont(Font.font("System", FontWeight.BOLD, 14));
        scrollPane.getChildren().add(title);

        // view toggle button
        Button toggleButton = new Button("Switch to Net Flow View");
        toggleButton.setStyle("-fx-font-size: 11px; -fx-padding: 5;");
        toggleButton.setPrefWidth(200);
        toggleButton.setOnAction(event -> {
            showResidualGraph = !showResidualGraph;
            if (showResidualGraph) {
                toggleButton.setText("Switch to Net Flow View");
            } else {
                toggleButton.setText("Switch to Residual Graph View");
            }
            updateLegendContent();

            if (onViewChanged != null) {
                onViewChanged.run();
            }
        });

        HBox buttonBox = new HBox(toggleButton);
        buttonBox.setStyle("-fx-padding: 5 0 5 0;");
        scrollPane.getChildren().add(buttonBox);

        

        updateLegendContent();

        this.setContent(scrollPane);
        this.setFitToWidth(true);
        this.setStyle("-fx-background-color: white;");
    }

    private void updateLegendContent() {
        // remove all children except title, button, and spacing ( orrr keep first 3
        // items)
        while (scrollPane.getChildren().size() > 3) {
            scrollPane.getChildren().remove(3);
        }

        if (showResidualGraph) {
            Label resGraph = new Label("     Residual View of Graph");
            resGraph.setFont(Font.font("System", FontWeight.BOLD, 11));
            scrollPane.getChildren().add(resGraph);
        } else {
            Label netGraph = new Label("     Net Flow View of Graph");
            netGraph.setFont(Font.font("System", FontWeight.BOLD, 11));
            scrollPane.getChildren().add(netGraph);
        }

        scrollPane.getChildren().add(new Label(""));

        // vertex legend items r the same for both views
        addVertex(new GralogColor(0xffb66e), "Vertex in current augmenting path");
        addVertex(new GralogColor(0x5167a2), "Source side of min-cut partition");
        addVertex(new GralogColor(0x740000), "Sink side of min-cut partition");
        addVertex(GralogColor.WHITE, "Vertex not in current augmenting path");

        //scrollPane.getChildren().add(new Label(""));

        if (showResidualGraph) {
            addEdge(GralogColor.BLACK, false, false, false, "Forward edge (remaining capacity)");
            addEdge(new GralogColor(0xe16f00), false, false, false, "Edge in current augmenting path");
            addEdge(new GralogColor(0x81b8c1), true, false, true, "Reverse edge (flow sent forward)");
            addEdge(GralogColor.NAVY, false, true, false, "Min-cut edge (bottleneck)");
        } else {
            addEdge(GralogColor.BLACK, false, false, false, "Edge (showing flow/capacity)");
            addEdge(new GralogColor(0xe16f00), false, false, false, "Edge in current augmenting path");
            addEdge(GralogColor.NAVY, false, true, false, "Min-cut edge (bottleneck)");

            //scrollPane.getChildren().add(new Label(""));
            Label formatLabel = new Label("Edge Label Format:");
            formatLabel.setFont(Font.font("System", FontWeight.BOLD, 12));
            scrollPane.getChildren().add(formatLabel);
            addText("X/Y", "X = current flow, Y = capacity");
        }
    }

    private void addVertex(GralogColor gralogColor, String description) {
        VBox item = new VBox(1);

        Canvas canvas = new Canvas(CANVAS_WIDTH, ITEM_HEIGHT);
        GraphicsContext gc = canvas.getGraphicsContext2D();

        Color colour = convertGralogColour(gralogColor);

        gc.setFill(colour);
        gc.setStroke(Color.BLACK);
        gc.setLineWidth(1.5);
        double centerY = ITEM_HEIGHT / 2.0;
        gc.fillOval(CANVAS_WIDTH / 2.0 - VERTEX_RADIUS, centerY - VERTEX_RADIUS,
                VERTEX_RADIUS * 2, VERTEX_RADIUS * 2);
        gc.strokeOval(CANVAS_WIDTH / 2.0 - VERTEX_RADIUS, centerY - VERTEX_RADIUS,
                VERTEX_RADIUS * 2, VERTEX_RADIUS * 2);

        Label label = new Label(description);
        label.setFont(Font.font("System", 11));

        javafx.scene.layout.HBox hbox = new javafx.scene.layout.HBox(10);
        hbox.getChildren().addAll(canvas, label);
        hbox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        scrollPane.getChildren().add(hbox);
    }

    private void addEdge(GralogColor gralogColor, boolean dashed, boolean dotted, boolean backwardsArrow,
            String description) {
        Canvas canvas = new Canvas(CANVAS_WIDTH, ITEM_HEIGHT);
        GraphicsContext gc = canvas.getGraphicsContext2D();

        Color colour = convertGralogColour(gralogColor);

        gc.setStroke(colour);
        gc.setLineWidth(2);

        double y = ITEM_HEIGHT / 2.0;
        double startX = 5;
        double endX = CANVAS_WIDTH - 5;

        if (dashed) {
            gc.setLineDashes(5, 5);
        } else if (dotted) {
            gc.setLineDashes(3, 3);
        } else {
            gc.setLineDashes(null);
        }

        gc.strokeLine(startX, y, endX, y);

        double arrowSize = 6;
        gc.setLineDashes(null); // solid for arrow

        if (backwardsArrow) {
            // Arrow pointing LEFT (at start of line)
            gc.strokeLine(startX, y, startX + arrowSize, y - arrowSize / 2);
            gc.strokeLine(startX, y, startX + arrowSize, y + arrowSize / 2);
        } else {
            // Arrow pointing RIGHT (at end of line)
            gc.strokeLine(endX, y, endX - arrowSize, y - arrowSize / 2);
            gc.strokeLine(endX, y, endX - arrowSize, y + arrowSize / 2);
        }

        Label label = new Label(description);
        label.setFont(Font.font("System", 11));

        javafx.scene.layout.HBox hbox = new javafx.scene.layout.HBox(10);
        hbox.getChildren().addAll(canvas, label);
        hbox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        scrollPane.getChildren().add(hbox);
    }

    private void addText(String symbol, String description) {
        Label symbolLabel = new Label(symbol);
        symbolLabel.setFont(Font.font("Monospaced", FontWeight.BOLD, 12));
        symbolLabel.setMinWidth(CANVAS_WIDTH);
        symbolLabel.setStyle("-fx-padding: 0 10 0 10;");

        Label descLabel = new Label(description);
        descLabel.setFont(Font.font("System", 11));

        javafx.scene.layout.HBox hbox = new javafx.scene.layout.HBox(10);
        hbox.getChildren().addAll(symbolLabel, descLabel);
        hbox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        scrollPane.getChildren().add(hbox);
    }

    // needed to convert GralogColor to JavaFX Color using r, g, b fields, ensure
    // that colours are consistent between graph and legend
    private Color convertGralogColour(GralogColor gralogColor) {
        return Color.rgb(
                gralogColor.r & 0xFF,
                gralogColor.g & 0xFF,
                gralogColor.b & 0xFF);
    }
}