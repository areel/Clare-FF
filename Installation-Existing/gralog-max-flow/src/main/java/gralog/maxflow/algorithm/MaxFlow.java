// This file is an extension of GrALoG, Copyright (c) 2016-2018 LaS group, TU Berlin.
// This file was created by Clare Reel in 2026

import gralog.maxflow.functions.MaxFlowFunctions;
import gralog.gralogfx.panels.MaxFlowLegendPanel;

import gralog.algorithm.*;
import gralog.progresshandler.ProgressHandler; // Note: onProgress(c) required to redraw graph.
import gralog.structure.*;

import java.security.AlgorithmParameters;
import java.util.*;

import gralog.gralogfx.piping.Piping;
import gralog.gralogfx.piping.Piping.MessageToConsoleFlag;

import gralog.rendering.GralogGraphicsContext;

import gralog.rendering.GralogColor;
import gralog.gralogfx.panels.PluginControlPanel;
import javafx.application.Platform;

@AlgorithmDescription(name = "Maximum Flow", text = "Finds the maximum flow between 2 selected vertices using the Ford–Fulkerson algorithm.", url = "https://en.wikipedia.org/wiki/Ford–Fulkerson_algorithm")

public class MaxFlow extends Algorithm {

    private volatile boolean playing = false;
    private volatile boolean stopped = false;
    private volatile boolean skipToEnd = false;
    private volatile boolean frontStepped = false;
    private volatile boolean backStepped = false;
    private volatile boolean isResidualView = true;
    private final Object pauseLock = new Object();
    double maxFlow;
    int iteration;
    boolean completedNormally;
    String pathString = "";

    Map<Edge, Edge> reverse;
    List<Vertex> unlabelled = new ArrayList<>();

    // State history for back-stepping
    private static class AlgorithmState {
        HashMap<Edge, Double> edgeWeights;
        double maxFlow;
        ArrayList<Vertex> lastPath;
        int iteration;
        String stepHistoryText;
        HashMap<Vertex, Edge> prevParentEdge;

        AlgorithmState(Structure c, double maxFlow, ArrayList<Vertex> path, int iteration, String historyText, HashMap<Vertex, Edge> parentEdge) {
            this.maxFlow = maxFlow;
            this.iteration = iteration;
            this.stepHistoryText = historyText;
            this.lastPath = path != null ? new ArrayList<>(path) : null;
            this.prevParentEdge =  parentEdge;

            // Snapshot all edge weights
            this.edgeWeights = new HashMap<Edge, Double>();
            for (Edge e : (Set<Edge>) c.getEdges()) {
                this.edgeWeights.put(e, e.weight);
            }
        }
    }

    private ArrayList<AlgorithmState> stateHistory = new ArrayList<>();
    private int currentStateIndex = -1; // Index in stateHistory we're currently at

    private HashMap<Edge, Double> originalWeights = null;
    private Map<Edge, Edge> reverseEdgeMap = null;

    private StringBuilder stepHistory = new StringBuilder();

    @SuppressWarnings("unchecked")
    public Object run(Structure c, AlgorithmParameters p, Set<Object> selection,
            ProgressHandler onprogress) throws Exception {

        // ability for user to switch between views of the graph.
        MaxFlowLegendPanel.setViewChangeCallback(() -> {
            try {
                Platform.runLater(() -> {
                    try {
                        boolean isResidualView = MaxFlowLegendPanel.isResidualGraphView();
                        MaxFlowFunctions.updateEdgeDisplay(c, reverseEdgeMap, originalWeights, isResidualView);
                        onprogress.onProgress(c);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        MaxFlowFunctions.recolour(c);
        // progress.setProgress(0); // clear progress bar, 1/3 filled bar [COME BACK
        // HERE]

        // ensuring algorithm can perform on generated graph
        // while giving clear instructions of how to rectify issues
        if (c.getVertices().size() == 0)
            return "The Ford-Fulkerson Algorithm requires a non-empty structure.";
        if (c.getEdges().size() == 0)
            return "The Ford-Fulkerson Algorithm requires edges.";

        for (Edge e : (Set<Edge>) c.getEdges()) {
            if (e.weight < 0d)
                return "The Ford-Fulkerson Algorithm requires non-negative edge weights";
            if (!e.isDirected)
                return ("The Ford-Fulkerson Algorithm requires that all edges are directed");
        }

        ArrayList<Vertex> sources = MaxFlowFunctions.findSources(c);
        ArrayList<Vertex> sinks = MaxFlowFunctions.findSinks(c);
        if (sources == null)
            return ("Please label all sources with 'S'. Ensure you have no incoming edges to your sources, and no outgoing edges from your sinks.");
        if (sinks == null)
            return ("Please, label all sinks with 'T'. Ensure you have no incoming edges to your sources, and no outgoing edges from your sinks.");

        // algorithm runs in an infinite while loop; necessary to allow multiple
        // visualisations of the same graph without requiring the user to redraw/reselect algorithm,
        // especially when using "skip" button.
        while (true) {

            unlabelled.clear();
            List<Vertex> allVerticesForLabelling = new ArrayList<>(c.getVertices());
            List<String> allLabels = new ArrayList<>();
            for (Vertex v : allVerticesForLabelling) {
                    unlabelled.add(v);
                    allLabels.add(v.label);
                } // keep unlabelled vertices in mind so new label can be erased when original
                  // graph recovered

                if (!backStepped) { // if backstepped=true do NOT initialise graph
                    synchronized (pauseLock) {
                        playing = false;
                        skipToEnd = false;
                        frontStepped = false;
                    }

                    stepHistory = new StringBuilder(); // cleared history for beginning of each iteration

                    // user's graph saved so as to allow it to be recovered when aborting algorithm
                    originalWeights = new HashMap<Edge, Double>();
                    for (Edge e : (Set<Edge>) c.getEdges()) {
                        originalWeights.put(e, e.weight);
                        e.label = String.format("%.2f", e.weight); // display labels on edges to aid in clarity of
                                                                   // algorithm
                    }

                    // residual graph generated immediately after algorithm selected
                    reverse = MaxFlowFunctions.residualGraph(c); // Assign to field, not local variable
                    reverseEdgeMap = reverse;

                    for (Edge forwardEdge : originalWeights.keySet()) {
                        if (reverse.containsKey(forwardEdge)) {
                            Edge reverseEdge = reverse.get(forwardEdge);
                            reverseEdge.label = String.format("%.2f", reverseEdge.weight);
                            reverseEdge.color = new GralogColor(0x669966); // mint
                            // lighter colour for residual edges, visually distinct
                        } // graph with backflow edges visible is the default
                    }

                    MaxFlowFunctions.giveVertexLabels(c, sources, sinks);
                    maxFlow = 0;
                    iteration = 0;
                    stateHistory = new ArrayList<>(); // Reset state history for this run
                    currentStateIndex = -1;

                    // Save initial state (iteration 0)
                    AlgorithmState initialState = new AlgorithmState(c, maxFlow, null, iteration, stepHistory.toString(), null);
                    stateHistory.add(initialState);
                    currentStateIndex = 0;

                    // show initial state of algorithm, and communicate with the user how to progress
                    updateControlPanel(iteration, 0, maxFlow, "Initial residual graph - press Play to begin", "");
                    updateGraphDisplay(c, onprogress); // DEBUGGED: Issue with views persisting between loops
                    onprogress.onProgress(c); // redraw graph to show updates
                    waitForStep(); // indefinite wait until user interacts with the algorithm
                } // end of initialisation

                if (stopped) { // stopping algorithm before it begins in earnest (or double clicking stop)
                               // restores the original graph
                    List<Edge> edges = new ArrayList<>(c.getEdges());
                    for (Edge e : edges)
                        e.label = ""; // get rid of added edge labels, as it was before
                    restoreGraph(c, onprogress, unlabelled, allLabels); // DEBUGGED: label issues
                    onprogress.onProgress(c); // restore original graph visually
                    MaxFlowLegendPanel.setViewChangeCallback(null); // ensure null when algorithm ends
                    return "Original graph restored, select desired algorithm from dropdown menu above.";
                }

                completedNormally = false; // algorithm beginning in earnest, so not completed
                while (!stopped) {

                    if (backStepped) {
                        if (currentStateIndex <= 0) {
                            // already at initial state
                            updateControlPanel(iteration, 0, maxFlow, "Cannot step back - at initial state", "");
                            backStepped = false;
                            onprogress.onProgress(c);
                            waitForStep();
                        } else { // DEBUGGED: BACKSTEP->STOP ISSUE

                            backStepped = false;
                            MaxFlowFunctions.recolour(c);
                            currentStateIndex--;
                            AlgorithmState prevState = stateHistory.get(currentStateIndex);

                            // Restore graph state from snapshot
                            for (Map.Entry<gralog.structure.Edge, Double> entry : prevState.edgeWeights.entrySet()) {
                                entry.getKey().weight = entry.getValue();
                                entry.getKey().label = String.valueOf(entry.getValue());
                            }

                            // restoring algorithm variables
                            maxFlow = prevState.maxFlow;
                            ArrayList<Vertex> path = prevState.lastPath;
                            iteration = prevState.iteration;

                            // back in step history, truncated to this point
                            stepHistory = new StringBuilder(prevState.stepHistoryText);

                            // highlight the path from this iteration
                            if (prevState.lastPath != null) {
                                MaxFlowFunctions.hilightPath(prevState.lastPath, prevState.prevParentEdge);
                                pathString = MaxFlowFunctions.buildPathString(prevState.lastPath);
                            } else {
                                pathString = "";
                            }

                            // Update display
                            if ((currentStateIndex == 0) || (prevState.lastPath == null)) {
                                updateControlPanel(iteration, 0, maxFlow,
                                        "Showing pass " + iteration + " - No path to display", "");
                            } else {
                                updateControlPanel(iteration, 0, maxFlow,
                                        "Showing path " + pathString, "");
                            }
                            updateGraphDisplay(c, onprogress);
                            onprogress.onProgress(c);

                            playing = false; // Pause with path visible
                            waitForStep();
                        }
                        if (stopped) {
                        restoreGraph(c, onprogress, unlabelled, allLabels); // DEBUGGED: label issues
                        updateControlPanel(0, 0, 0, "Graph restored - press Play to restart algorithm", "");
                        onprogress.onProgress(c);
                        stopped = false;
                        break; // hang in beginning-state limbo
                        }

                        MaxFlowFunctions.recolour(c); // clear highlighting after user continues
                        updateGraphDisplay(c, onprogress);
                        onprogress.onProgress(c);
                        continue;
                    }

                    if (frontStepped) {
                        HashMap<Vertex, Vertex> parentVertex = new HashMap<Vertex, Vertex>();
                        HashMap<Vertex, Edge> parentEdge = new HashMap<Vertex, Edge>();
                        Vertex sink = MaxFlowFunctions.bfs(c, sources, sinks, reverse, parentEdge, parentVertex);

                        if (sink == null) {
                            completedNormally = true;
                            frontStepped = false;
                            break;
                        }

                        ArrayList<Vertex> path = MaxFlowFunctions.buildPath(c, sink, parentVertex, parentEdge);
                        if (path == null || path.isEmpty()) {
                            completedNormally = true;
                            frontStepped = false;
                            break;
                        }
                        double pathFlow = MaxFlowFunctions.calculatePathFlow(path, parentEdge);
                        if (pathFlow <= 0) {
                            completedNormally = true;
                            frontStepped = false;
                            break;
                        }

                        iteration++;
                        maxFlow += pathFlow;
                        updateControlPanel(iteration, pathFlow, maxFlow,
                                "Found path " + MaxFlowFunctions.buildPathString(path) + String.format(" (Path flow: %.2f)", pathFlow), "");
                        MaxFlowFunctions.updateFlowAlongPath(path, pathFlow, parentEdge, reverse, isResidualView);
                        updateGraphDisplay(c, onprogress);
                        onprogress.onProgress(c); // redraw with highlighted path

                        // Save state AFTER applying flow so it can restore to this point
                        // and remove any future states (if user went back then forward)
                        while (stateHistory.size() > currentStateIndex + 1) {
                            stateHistory.remove(stateHistory.size() - 1);
                        }
                        // Aadding current state
                        AlgorithmState newState = new AlgorithmState(c, maxFlow, path, iteration,
                                stepHistory.toString(), parentEdge);
                        stateHistory.add(newState);
                        currentStateIndex = stateHistory.size() - 1;

                        frontStepped = false;
                        playing = false;
                        waitForStep(); // PAUSE HERE path stays green

                        MaxFlowFunctions.recolour(c);
                        updateGraphDisplay(c, onprogress);
                        onprogress.onProgress(c);

                        if (stopped) {
                        restoreGraph(c, onprogress, unlabelled, allLabels); // DEBUGGED: label issues
                        updateControlPanel(0, 0, 0, "Graph restored - press Play to restart algorithm", "");
                        onprogress.onProgress(c);
                        stopped = false;
                        break; // hang in beginning-state limbo
                        }
                        continue; // wait for next button press
                    }

                    if (skipToEnd) {
                        while (true) { // instantaneous algorithm completion
                            HashMap<Vertex, Vertex> pv = new HashMap<Vertex, Vertex>();
                            HashMap<Vertex, Edge> pe = new HashMap<Vertex, Edge>();
                            Vertex sink = MaxFlowFunctions.bfs(c, sources, sinks, reverse, pe, pv);
                            if (sink == null) {
                                break;
                            }
                            ArrayList<Vertex> path = MaxFlowFunctions.buildPath(c, sink, pv, pe);
                            if (path == null || path.isEmpty())
                                break;
                            double pathFlow = MaxFlowFunctions.calculatePathFlow(path, pe);
                            if (pathFlow <= 0)
                                break;

                            iteration++;

                            // logging steps to history v quick so the user can still read the steps taken
                            // to reach conclusion
                            stepHistory.append(String.format("Pass %d: Found path: %s (Path flow: %.2f)\n", iteration,
                                    MaxFlowFunctions.buildPathString(path), pathFlow));
                            MaxFlowFunctions.updateFlowAlongPath(path, pathFlow, pe, reverse, isResidualView);
                            maxFlow += pathFlow;

                            while (stateHistory.size() > currentStateIndex + 1) {
                                stateHistory.remove(stateHistory.size() - 1);
                            }
                            AlgorithmState newState = new AlgorithmState(c, maxFlow, path, iteration,
                                    stepHistory.toString(), pe);
                            stateHistory.add(newState);
                            currentStateIndex = stateHistory.size() - 1;
                        }
                        updateControlPanel(iteration, 0, maxFlow, "Skipped to completion", "");
                        MaxFlowFunctions.recolour(c);
                        updateGraphDisplay(c, onprogress);
                        onprogress.onProgress(c); // only redraw graph at the end, don't overwhelm user with all
                                                  // those flashing nodes
                        completedNormally = true;
                        break; // leave the main loop, algorithm done but still hanging in end-state limbo
                    }

                    // with no skip, algorithm takes its time for the user to understand the process
                    HashMap<Vertex, Vertex> parentVertex = new HashMap<Vertex, Vertex>();
                    HashMap<Vertex, Edge> parentEdge = new HashMap<Vertex, Edge>();
                    Vertex sink = MaxFlowFunctions.bfs(c, sources, sinks, reverse, parentEdge, parentVertex);

                    if (sink == null) {// no path left? done
                        completedNormally = true;
                        break;
                    }

                    ArrayList<Vertex> path = MaxFlowFunctions.buildPath(c, sink, parentVertex, parentEdge);
                    if (path == null || path.isEmpty()) {
                        completedNormally = true;
                        break;
                    }
                    double pathFlow = MaxFlowFunctions.calculatePathFlow(path, parentEdge);
                    if (pathFlow <= 0) {
                        completedNormally = true;
                        break;
                    }

                    iteration++;
                    maxFlow += pathFlow;
                    updateControlPanel(iteration, pathFlow, maxFlow,
                            "Found path " + MaxFlowFunctions.buildPathString(path) + String.format(" (Path flow: %.2f)", pathFlow), "");
                    MaxFlowFunctions.updateFlowAlongPath(path, pathFlow, parentEdge, reverse, isResidualView);
                    updateGraphDisplay(c, onprogress);
                    onprogress.onProgress(c); // redraw with highlighted path

                    // Save state AFTER applying flow so it can restore to this point
                    // and remove any future states (if user went back then forward)
                    while (stateHistory.size() > currentStateIndex + 1) {
                        stateHistory.remove(stateHistory.size() - 1);
                    }
                    // Aadding current state
                    AlgorithmState newState = new AlgorithmState(c, maxFlow, path, iteration, stepHistory.toString(), parentEdge);
                    stateHistory.add(newState);
                    currentStateIndex = stateHistory.size() - 1;

                    waitForStep(); // PAUSE HERE path stays green

                    MaxFlowFunctions.recolour(c);
                    updateGraphDisplay(c, onprogress);
                    onprogress.onProgress(c);

                    if (stopped) {
                        restoreGraph(c, onprogress, unlabelled, allLabels); // DEBUGGED: label issues
                        updateControlPanel(0, 0, 0, "Graph restored - press Play to restart algorithm", "");
                        onprogress.onProgress(c);
                        stopped = false;
                        break; // hang in beginning-state limbo
                    }
                }

                if (completedNormally) { // if completed normally, show the partition and wait indefinitely
                    String minCutEdges = MaxFlowFunctions.minCut(c, sources);
                    updateGraphDisplay(c, onprogress);
                    onprogress.onProgress(c);

                    iteration++;

                    // log as new state so as to backstep accurately
                    while (stateHistory.size() > currentStateIndex + 1) {
                        stateHistory.remove(stateHistory.size() - 1);
                    }
                    AlgorithmState newState = new AlgorithmState(c, maxFlow, null, iteration, stepHistory.toString(), null);
                    stateHistory.add(newState);
                    currentStateIndex = stateHistory.size() - 1;

                    updateControlPanel(iteration, 0, maxFlow,
                            "No more augmenting paths - Complete! Max flow = " + String.format("%.2f", maxFlow)
                                    + " - Min-cut shown (Salmon/Blue) - Press any console button to restore & restart",
                            minCutEdges);

                    synchronized (pauseLock) { // wait indefinitely for user to press any button
                        pauseLock.wait();
                    }
                    if (backStepped) {
                        completedNormally = false;
                        continue;
                    }

                    // any button press leads to restoring graph and restarting
                    restoreGraph(c, onprogress, unlabelled, allLabels); //DEBUGGED: Label issues
                    updateControlPanel(0, 0, 0, "Graph restored - press Play to restart algorithm", "");
                    onprogress.onProgress(c);
                    continue; // beginning loop again :D
                }
            }
        }

    // BUTTONS FOR INTERACTIVITY

    public void play() {
        synchronized (pauseLock) {
            playing = true;
            stopped = false;
            frontStepped = false;
            skipToEnd = false;
            backStepped = false;
            pauseLock.notifyAll();
        }
        Platform.runLater(PluginControlPanel::notifyPlayRequested);
    }

    public void pause() {
        synchronized (pauseLock) {
            playing = false;
            stopped = false;
            frontStepped = false;
            skipToEnd = false;
            backStepped = false;
            // do NOT notifyAll... let the current wait() keep blocking
        }
        Platform.runLater(PluginControlPanel::notifySpontaneousPauseRequested);
    }

    public void stop() {
        synchronized (pauseLock) {
            stopped = true;
            playing = false;
            frontStepped = false;
            skipToEnd = false;
            backStepped = false;
            pauseLock.notifyAll(); // unblock waitForStep so it can see stopped==true
        }
    }

    public void skip() {
        synchronized (pauseLock) {
            skipToEnd = true;
            playing = false;
            stopped = false;
            frontStepped = false;
            backStepped = false;
            pauseLock.notifyAll();
        }
    }

    public void frontStep() {
        synchronized (pauseLock) {
            frontStepped = true;
            playing = false;
            stopped = false;
            skipToEnd = false;
            backStepped = false;
            pauseLock.notifyAll();
        }
    }

    public void backStep() {
        synchronized (pauseLock) {
            backStepped = true;
            playing = false;
            stopped = false;
            frontStepped = false;
            skipToEnd = false;
            pauseLock.notifyAll();

        }
    }

    private void waitForStep() throws InterruptedException {
        synchronized (pauseLock) {
            while (true) {
                if (stopped || skipToEnd || frontStepped || backStepped)
                    return;

                if (playing) {
                    pauseLock.wait(2500); // Auto-advance: wait 2 seconds
                    if (stopped || skipToEnd || frontStepped || backStepped)
                        return;
                    if (!playing)
                        continue;
                    return; // 2 seconds, continue
                } else {
                    pauseLock.wait();
                }
            }
        }
    }

    private void updateGraphDisplay(Structure c, ProgressHandler onprogress) {
        try {
            boolean isResidualView = MaxFlowLegendPanel.isResidualGraphView();
            MaxFlowFunctions.updateEdgeDisplay(c, reverseEdgeMap, originalWeights, isResidualView);
            onprogress.onProgress(c);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // function must remain local so it can have access to status of play and update
    // buttons
    private void updateControlPanel(int iteration, double pathFlow, double totalFlow, String message, String minCut) {
        // add each update and their corresponding passes to the history (accumulates
        // across the run)

        if (message.contains("Found"))
            stepHistory.append(String.format("Pass %d: %s\n", iteration, message ));
        if (message.contains("No more"))
            stepHistory
                    .append(String.format("Pass %d: %s\n" + "Edges across partition: %s", iteration, message, minCut));
        // if (message.contains("Min-cut"))
        // stepHistory
        // .append(String.format("Pass %d: %s\n" + "Edges across partition: %s",
        // iteration, message, minCut));

        final boolean isPlaying = this.playing;
        final String historySnapshot = stepHistory.toString();

        Platform.runLater(() -> {
            List<String[]> vars = new ArrayList<String[]>();
            vars.add(new String[] { "Showing Pass", String.valueOf(iteration) });
            vars.add(new String[] { "Status", message });
            vars.add(new String[] { "Total Flow", String.format("%.2f", totalFlow) });
            
            // vars.add(new String[] { "Path Flow", String.format("%.2f", pathFlow) });
            // vars.add(new String[] { "Edges Across Partition", minCut });
            vars.add(new String[] { "Step History", "\n" + historySnapshot });
            PluginControlPanel.notifyPlannedPauseRequested(vars);

            // show the correct button; || while auto-advancing, |> while paused
            if (isPlaying)
                PluginControlPanel.notifyPlayRequested();
        });
    }

    private void restoreGraph(Structure c, ProgressHandler onprogress, List<Vertex> unlabelled, List<String> allLabels ) {
        if (originalWeights == null)
            return;

        // remove reverse edges
        if (reverseEdgeMap != null) {
            for (Map.Entry<Edge, Edge> entry : reverseEdgeMap.entrySet()) {
                if (originalWeights.containsKey(entry.getKey())) {
                    c.removeEdge(entry.getValue());
                }
            }
        }

        // restore original weights
        for (Map.Entry<Edge, Double> entry : originalWeights.entrySet()) {
            entry.getKey().weight = entry.getValue();
        }

        int o = 0;
        if (unlabelled.size() != 0) {
            for (Vertex v : unlabelled) {
                v.label = allLabels.get(o);
                o++;
            }
        }

        originalWeights = null;
        reverseEdgeMap = null;
        MaxFlowFunctions.recolour(c);

        try {
            onprogress.onProgress(c);
        } catch (Exception ignored) {
        } // redraw
    }
}