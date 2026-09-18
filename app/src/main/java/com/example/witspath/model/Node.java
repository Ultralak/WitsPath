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
    static Map<String, Node> nodes = new HashMap<>();
    static Map<String, Node> nodesByID = new HashMap<>();

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
        catch (Exception e)
        {
            // Default to CLASSROOM if type doesn't match enum
            type = NodeType.CLASSROOM;
        }

        neighbours = new ArrayList<>();
        edges = new ArrayList<>();

        nodes.put(name, this);
        nodesByID.put(i, this);
    }

    public void addNeighbour(Node n)
    {
        neighbours.add(n);
    }

    public void addEdge(Edge e)
    {
        edges.add(e);
    }

    public Node other(Edge e)
    {
        if(e.node1 == this)
        {
            return e.node2;
        }

        if(e.node2 == this)
        {
            return e.node1;
        }

        return null;
    }

    public static Node getByName(String n)
    {
        return nodes.get(n);
    }

    public static Node getByID(String i)
    {
        return nodesByID.get(i);
    }

    public static ArrayList<Node> searchByName(String n)
    {
        ArrayList<Node> matchingNodes = new ArrayList<>();
        for(String name : nodes.keySet())
        {
            if(name != null && name.contains(n))
            {
                matchingNodes.add(getByName(name));
            }
        }

        return matchingNodes;
    }

    public static void clearNodes()
    {
        nodesByID.clear();
        nodes.clear();
    }
}

































