package com.example.witspath.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class Node
{
    public String nodeId;
    public String name;
    public Point point;
    public NodeType type;
    public String floorId;

    public ArrayList<Node> neighbours;
    public ArrayList<Edge> edges;

    public enum NodeType {
        CLASSROOM,
        BUILDING,
        LAB,
        RAMP,
        JUNCTION,
        LIFT
    }

    public Node(String i, String n, double x, double y, String t, String floorId)
    {
        nodeId = i;
        name = n;
        this.floorId = floorId;

        try
        {
            point = new Point(x, y);
            type = NodeType.valueOf(t.toUpperCase());
        }
        catch (IllegalArgumentException e)
        {
            throw new IllegalArgumentException(
                    "Unknown node type: " + type
            );
        }

        neighbours = new ArrayList<>();
        edges = new ArrayList<>();
    }

    public void addNeighbour(Node n)
    {
        neighbours.add(n);
    }

    public void addEdge(Edge e)
    {
        edges.add(e);
    }

}