package com.example.witspath.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class Node
{
    private static final AtomicInteger count = new AtomicInteger(0);
    public int id;
    public String name;
    public Area area;
    public Point point;
    public NodeType type;

    public ArrayList<Node> neighbours;
    public ArrayList<Edge> edges;
    static Map<String, Node> nodes = new HashMap<>();

    public enum NodeType
    {
        ENTRANCE,
        BATHROOM,
        DISABILITY_BATHROOM,
        CLASSROOM,
        BUILDING,
        RAMP
    }

    public enum Area
    {
        WSS,
        ARM,
        COMMERCE,
        NCB,
        TOWER
    }

    public Node(String n, String a, double x, double y, String t)
    {
        id = count.incrementAndGet();
        name = n;

        try
        {
            area = Area.valueOf(a.toUpperCase());
            point = new Point(x, y);
            type = NodeType.valueOf(t.toUpperCase());
        }
        catch (IllegalArgumentException e)
        {
            System.out.println("Invalid node type or area.");
        }

        neighbours = new ArrayList<>();
        edges = new ArrayList<>();

        nodes.put(name, this);
    }

    public void addNeighbour(Node n)
    {
        neighbours.add(n);
    }

    public boolean isNeighbour(Node n)
    {
        return neighbours.contains(n);
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
}
