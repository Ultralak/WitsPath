package com.example.witspath.model;

import java.util.*;

public class PathFinder {

    public static String errorMessage = "";
    static class NodeDetails {
        Node parent = null;
        double f = Double.MAX_VALUE;
        double g = Double.MAX_VALUE;
        double h = Double.MAX_VALUE;
    }

    static class PQNode implements Comparable<PQNode>
    {
        double f;
        Node node;

        PQNode(double f, Node node)
        {
            this.f = f;
            this.node = node;
        }

        @Override
        public int compareTo(PQNode other)
        {
            if (this.f != other.f)
            {
                return Double.compare(this.f, other.f);
            }

            return Integer.compare(this.node.id, other.node.id);
        }
    }
    List<Edge> getSuccessors(Node node) {
        return node.edges;
    }

    List<Edge> getSuccessors(Node node, boolean requireAccessible)
    {
        List<Edge> result = new ArrayList<>();
        for (Edge e : node.edges)
        {
            if (!e.status)
            {
                continue;
            }

            if (!requireAccessible)
            {
                result.add(e);
                continue;
            }

            if (!e.stairs || e.ramp || e.elevator)
            {
                result.add(e);
            }
        }

        return result;
    }

    double calculateHValue(Node node, Node goal)
    {
        if (node.point == null || goal.point == null)
        {
            return 0.0;
        }

        double dx = node.point.x - goal.point.x;
        double dy = node.point.y - goal.point.y;
        return Math.sqrt(dx * dx + dy * dy);
    }

    public LinkedList<Node> tracePath(Map<Node, NodeDetails> details, Node src, Node goal)
    {
        LinkedList<Node> path = new LinkedList<>();
        Node current = goal;

        while (current != null && current != src)
        {
            path.addFirst(current);
            current = Objects.requireNonNull(details.get(current)).parent;
        }

        path.addFirst(src);

        return path;
    }

    void aStarSearch(String srcName, String goalName, boolean requireAccessible)
    {
        Node src = Node.getByName(srcName);
        Node goal = Node.getByName(goalName);

        if (src == null)
        {
            System.out.println("Source node '" + srcName + "' does not exist.");
            return;
        }

        if (goal == null)
        {
            System.out.println("Destination node '" + goalName + "' does not exist.");
            return;
        }

        aStarSearch(src, goal, requireAccessible);
    }

    void aStarSearch(String srcName, String goalName)
    {
        aStarSearch(srcName, goalName, false);
    }

    void aStarSearch(Node src, Node goal)
    {
        aStarSearch(src, goal, false);
    }

    public LinkedList<Node> aStarSearch(Node src, Node goal, boolean requireAccessible)
    {

        if (src == goal)
        {
            return null;
        }

        Set<Node> closedSet = new HashSet<>();
        Map<Node, NodeDetails> details = new HashMap<>();

        NodeDetails startDetails = new NodeDetails();
        startDetails.g = 0.0;
        startDetails.h = calculateHValue(src, goal);
        startDetails.f = startDetails.h;
        startDetails.parent = src;
        details.put(src, startDetails);

        PriorityQueue<PQNode> openList = new PriorityQueue<>();
        openList.offer(new PQNode(startDetails.f, src));

        boolean foundDest = false;

        while (!openList.isEmpty())
        {

            PQNode current = openList.poll();
            assert current != null;
            Node currentNode = current.node;

            if (closedSet.contains(currentNode))
            {
                continue;
            }

            closedSet.add(currentNode);

            if (currentNode == goal)
            {
                foundDest = true;
                return tracePath(details, src, goal);
            }

            for (Edge edge : getSuccessors(currentNode, requireAccessible))
            {

                Node neighbour = currentNode.other(edge);
                if (neighbour == null)
                {
                    continue;
                }

                if (!closedSet.contains(neighbour))
                {

                    double gNew = Objects.requireNonNull(details.get(currentNode)).g + edge.distance;
                    double hNew = calculateHValue(neighbour, goal);
                    double fNew = gNew + hNew;

                    NodeDetails neighbourDetails = details.computeIfAbsent(neighbour, k -> new NodeDetails());

                    if (neighbourDetails.f == Double.MAX_VALUE
                            || neighbourDetails.f > fNew)
                    {
                        openList.offer(new PQNode(fNew, neighbour));

                        neighbourDetails.f = fNew;
                        neighbourDetails.g = gNew;
                        neighbourDetails.h = hNew;
                        neighbourDetails.parent = currentNode;
                    }
                }
            }
        }

        if (!foundDest)
        {
            if (requireAccessible)
            {
                errorMessage = "Failed to find an accessible route to the destination node "
                        + "(a path may exist, but only via stairs with no ramp/elevator).";
            }
            else
            {
                errorMessage = "Failed to find the destination node.";
            }
        }
        return null;
    }
}