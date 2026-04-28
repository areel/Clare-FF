// This file is an extension of GrALoG, Copyright (c) 2016-2018 LaS group, TU Berlin.
// This file was created by Clare Reel in 2026

package gralog.maxflow.functions;

import gralog.algorithm.*;
import gralog.structure.*;
import java.util.*;

import gralog.rendering.GralogGraphicsContext;
import gralog.rendering.GralogColor;

public class MaxFlowFunctions {

    // ensure source vertices fit criteria
    public static ArrayList<Vertex> findSources(Structure c) {
        boolean foundSources = false;
        ArrayList<Vertex> sources = new ArrayList<Vertex>();

        for (Object obj : c.getVertices()) {
            Vertex v = (Vertex) obj;
            boolean hasS = (v.label.contains("S") || v.label.contains("s"));
            boolean hasNoIncoming = v.getIncomingNeighbours().isEmpty();
            boolean hasOutgoing = !v.getOutgoingNeighbours().isEmpty();

            if (hasS && hasNoIncoming && hasOutgoing) {
                sources.add(v);
                foundSources = true;
            }
        }
        if (foundSources == true)
            return sources;
        else
            return null;
    }

    // ensure sink vertices fit criteria
    public static ArrayList<Vertex> findSinks(Structure c) {
        boolean foundSinks = false;
        ArrayList<Vertex> sinks = new ArrayList<Vertex>();

        for (Object obj : c.getVertices()) {
            Vertex v = (Vertex) obj;
            boolean hasT = (v.label.contains("T") || v.label.contains("t"));
            boolean hasNoOutgoing = v.getOutgoingNeighbours().isEmpty();
            boolean hasIncoming = !v.getIncomingNeighbours().isEmpty();
            if (hasT && hasNoOutgoing && hasIncoming) {
                sinks.add(v);
                foundSinks = true;
            }
        }
        if (foundSinks == true)
            return sinks;
        else
            return null;
    }

    // residual graph built by adding reversed edges
    public static Map<Edge, Edge> residualGraph(Structure c) {
        Map<Edge, Edge> reverse = new HashMap<>();
        List<Edge> original = new ArrayList<>(c.getEdges());

        for (Edge e : original) {
            double capacity = e.weight;
            e.weight = capacity;
            gralog.structure.Edge rev = c.addEdge(e.getTarget(), e.getSource());
            rev.weight = 0.0;
            rev.type = GralogGraphicsContext.LineType.DASHED; // dashed for visual distinction

            reverse.put(e, rev);
            reverse.put(rev, e);
        }

        return reverse;
    }

    // labelling vertices in an intuitive way that allows for the user to understand
    // which vertices are
    // incolved in each path
    public static void giveVertexLabels(Structure c, ArrayList<Vertex> sources, ArrayList<Vertex> sinks) {
        List<Vertex> vertices = new ArrayList<>(c.getVertices());
        List<Vertex> blankList = new ArrayList<>();

        for (Vertex v : vertices) {
            if (v.label.equals(""))
                blankList.add(v);
        }

        int i = 0;
        for (Vertex s : sources) {
            for (Vertex d : vertices)
                while (d.label.equals("S" + i))
                    i++;
            s.label = ("S" + i);
        }
        i = 0;
        for (Vertex t : sinks) {
            for (Vertex d : vertices)
                while (d.label.equals("T" + i))
                    i++;
            t.label = ("T" + i);
        }
        i = 0;
        for (Vertex b : blankList) {
            for (Vertex d : vertices)
                while (d.label.equals("V" + i))
                    i++;
            b.label = ("V" + i);
        }
    }

    // breadth first search, find sinks reachable from sources
    public static Vertex bfs(Structure c, ArrayList<Vertex> S,
            ArrayList<Vertex> T, Map<Edge, Edge> reverse,
            HashMap<Vertex, Edge> parentEdge, HashMap<Vertex, Vertex> parentVertex) {

        LinkedList<Vertex> queue = new LinkedList<>();
        HashSet<Vertex> visited = new HashSet<>();

        for (Vertex s : S) {
            queue.add(s);
            visited.add(s);
            parentVertex.put(s, null);
            parentEdge.put(s, null);
        }

        while (queue.isEmpty() == false) {
            Vertex u = queue.poll();
            for (Edge e : u.getOutgoingEdges()) {
                Vertex v = e.getTarget();
                if (e.weight > 0 && (visited.contains(v) == false)) {
                    visited.add(v);
                    queue.add(v);
                    parentVertex.put(v, u);
                    parentEdge.put(v, e);

                    if (T.contains(v)) {
                        return v; // return the sink vertex
                    }
                }
            }
        }
        return null; // no paths left o7
    }

    // build path from sink back to source, colour vertices to highlight path for
    // user
    public static ArrayList<Vertex> buildPath(Structure c, Vertex sink, HashMap<Vertex, Vertex> parentVertex, HashMap<Vertex, Edge> parentEdge) {
        ArrayList<Vertex> path = new ArrayList<Vertex>();
        for (Vertex v = sink; v != null; v = parentVertex.get(v)) {
            path.add(0, v);
        }
        hilightPath(path, parentEdge);
        return path;
    }

    public static void hilightPath(ArrayList<Vertex> path, HashMap<Vertex, Edge> parentEdge) { //DEBUGED: highlight the right edges
 
        for (gralog.structure.Vertex v : path) {
            v.fillColor = new GralogColor(0xffb66e); // clementine
            //v.fillColor = new GralogColor(0x5167a2); // blu
            Edge e = parentEdge.get(v);
            if (e != null){
                e.color = new GralogColor(0xe16f00); // tangerine
                e.thickness = 0.03; // slightly thicker line, as this increases font size,
            }//break; // making the path in question more prominent
        } 
        /*
        for (int i = 1; i < path.size(); i++) {
            Edge e = parentEdge.get(path.get(i));
            if (e != null){
                e.color = new GralogColor(0xe16f00); // tangerine
                e.thickness = 0.03; // slightly thicker line, as this increases font size,
                break; // making the path in question more prominent
            }
        }
        for (int i = 0; i < path.size() - 1; i++) {
            Vertex from = path.get(i);
            Vertex to = path.get(i + 1);

            // find edge from "from" to "to"
            for (Edge e : (Set<Edge>) c.getEdges()) {
                if (e.getSource() == from && e.getTarget() == to
                        && e.type != GralogGraphicsContext.LineType.DASHED) {
                    e.color = new GralogColor(0xe16f00); // tangerine
                    e.thickness = 0.03; // slightly thicker line, as this increases font size,
                    break; // making the path in question more prominent
                }
            }
        } */
    }

    // calculaste bottleneck
    public static double calculatePathFlow(ArrayList<Vertex> path, HashMap<Vertex, Edge> parentEdge) {
        double pathFlow = Double.MAX_VALUE;
        for (int i = 1; i < path.size(); i++) {
            Edge e = parentEdge.get(path.get(i));
            if (e != null)
                pathFlow = Math.min(pathFlow, e.weight);
        }
        return pathFlow;
    }

    // does what it says on the tin
    public static void updateFlowAlongPath(ArrayList<Vertex> path, double pathFlow,
            HashMap<Vertex, Edge> parentEdge, Map<Edge, Edge> reverse, boolean isResidualGraphView) {
        for (int i = 1; i < path.size(); i++) {
            Edge e = parentEdge.get(path.get(i));
                if (e != null && reverse.containsKey(e)) {
                    Edge rev = reverse.get(e);
                    e.weight -= pathFlow;
                    rev.weight += pathFlow;
                    e.label = String.format("%.2f", e.weight);
                    rev.label = String.format("%.2f", rev.weight);
                }
            }
        }

    // string representation of path for control panel
    public static String buildPathString(ArrayList<Vertex> path) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < path.size(); i++) {
            if (i > 0)
                sb.append(" -> ");
            sb.append(path.get(i).label);
        }
        return sb.toString();
    }

    // min cut partition, red and blue for least common colour blindness issues
    // min cut edges found and converted to string for control panel output
    public static String minCut(Structure c, ArrayList<Vertex> sources) {
        Set<Edge> minCut = new HashSet<Edge>();
        StringBuilder cutEdgesString = new StringBuilder();

        Set<Vertex> reachable = new HashSet<Vertex>();
        Queue<Vertex> queue = new LinkedList<Vertex>();

        for (Vertex source : sources) {
            reachable.add(source);
            queue.add(source);
        }

        while (!queue.isEmpty()) {
            Vertex current = queue.poll();
            for (Edge e : (Set<Edge>) c.getEdges()) {
                if (e.getSource() == current && e.weight > 0) {
                    Vertex target = e.getTarget();
                    if (!reachable.contains(target)) {
                        reachable.add(target);
                        queue.add(target);
                    }
                }
            }
        }

        for (Vertex v : (Collection<Vertex>) c.getVertices()) {
            if (reachable.contains(v)) {
                //v.fillColor = new GralogColor(0xffd9d9); // coral 
                v.fillColor = new GralogColor(0x5167a2); // blu
            } else {
                v.fillColor = new GralogColor(0x740000); // maroon
            }
        }

        for (Edge e : (Set<Edge>) c.getEdges()) {
            Vertex v = e.getSource();
            Vertex u = e.getTarget();

            if (reachable.contains(v) && !reachable.contains(u) && e.weight == 0
                    && e.type != GralogGraphicsContext.LineType.DASHED) {
                minCut.add(e);
                e.color = GralogColor.NAVY;
                e.type = GralogGraphicsContext.LineType.DOTTED;
                e.thickness = 0.03; // CONCLUDED WITH THIS DESIGN
                // List<ControlPoint> ctrl = e.controlPoints;
                // Vector2D start = ctrl.get((ctrl.size()-1)/2).getPosition();
                // GralogGraphicsContext.putText(start.plus(e.thickness*10,e.thickness*10),
                // e.weight, 0.025, e.color);
            }
        }

        for (Edge e : minCut)
            cutEdgesString.append(e.getSource().label + " -> " + e.getTarget().label).append(", ");

        if (cutEdgesString.length() > 0) {
            cutEdgesString.setLength(cutEdgesString.length() - 2);
        }

        return cutEdgesString.toString();
    }

    // recolour structure edges and vertices to default, prevent confusion
    public static void recolour(Structure c) {
        List<Vertex> vertices = new ArrayList<>(c.getVertices());
        for (Vertex v : vertices)
            v.fillColor = GralogColor.WHITE;

        List<Edge> edges = new ArrayList<>(c.getEdges());
        for (Edge e : edges)
            if ((e.color != GralogColor.BLACK) && (e.type != GralogGraphicsContext.LineType.DASHED)) {
                e.color = GralogColor.BLACK;
                e.type = GralogGraphicsContext.LineType.PLAIN;
                e.thickness = 0.025;
            }
    }

    public static void updateEdgeDisplay(Structure c, Map<Edge, Edge> reverseEdgeMap,
            HashMap<Edge, Double> originalWeights, boolean isResidualView) {
        if (isResidualView) {
            // default residual graph view: show all edges with remaining capacity
            for (Edge e : (Set<Edge>) c.getEdges()) {
                e.label = String.format("%.2f", e.weight);
                if (e.type == GralogGraphicsContext.LineType.DASHED) {
                    e.color = new GralogColor(0x81b8c1); // minty blue
                    e.thickness = 0.025;
                }
            }
        } else {
            // alternative net flow view: hide reverse edges, show flow/capacity
            for (Edge forwardEdge : originalWeights.keySet()) {
                if (reverseEdgeMap.containsKey(forwardEdge)) {
                    Edge reverseEdge = reverseEdgeMap.get(forwardEdge);

                    // calculate current flow: original capacity - remaining capacity
                    double originalCapacity = originalWeights.get(forwardEdge);
                    double remainingCapacity = forwardEdge.weight;
                    double currentFlow = originalCapacity - remainingCapacity;

                    // Set forward edge label to "flow/capacity"
                    forwardEdge.label = String.format("%.2f/%.2f", currentFlow, originalCapacity);

                    // hide reverse edge, making it practically invisible, and "removing" edge label
                    // by setting thickness to 0
                    reverseEdge.color = new GralogColor(0xf2f2f2); // transparent
                    reverseEdge.thickness = 0.0;
                }
            }
        }
    }

    public static double roundToSignificantFigures(double num, int n) { //STACKOVERFLOW
    if(num == 0) {
        return 0;
    }

    final double d = Math.ceil(Math.log10(num < 0 ? -num: num));
    final int power = n - (int) d;

    final double magnitude = Math.pow(10, power);
    final long shifted = Math.round(num*magnitude);
    return shifted/magnitude;
}

}