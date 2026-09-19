package com.example.witspath.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class Node
{
    private static final AtomicInteger count = new AtomicInteger(0);
    public int id;
    public String nodeId;
    public String name;
    public String label;
    public Area area;
    public Point point;
    public NodeType type;
    public String floorId;
    public String bssid;

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

    public enum Area {
        WSS,
        ARM,
        COMMERCE,
        NCB,
        TOWER,
        LAW,
        ACCOUNTANCY,
        FACILITIES,
        BUSINESS_SCIENCES,
        KAMBULE,
        SCIENCE_STADIUM,
        CCDU,
        GENMIN,
        FLOWER_HALL
    }

    public Node(String i, String n, double x, double y, String t, String floorId)
    {
        this(i, n, "WSS", x, y, t, floorId, "");
    }

    public Node(String i, String n, double x, double y, String t, String floorId, String bssid)
    {
        this(i, n, "WSS", x, y, t, floorId, bssid);
    }

    public Node(String i, String l, String a, double x, double y, String t, String floorId)
    {
        this(i, l, a, x, y, t, floorId, "");
    }

    public Node(String i, String l, String a, double x, double y, String t, String floorId, String bssid)
    {
        id = count.incrementAndGet();
        nodeId = i;
        label = l;
        name = i; // Use ID as the internal name key for lookups
        this.floorId = floorId;
        this.bssid = bssid;

        try {
            area = Area.valueOf(a.toUpperCase());
        } catch (Exception e) {
            area = Area.WSS;
        }

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
        for(Node node : nodes.values())
        {
            if((node.label != null && node.label.toLowerCase().contains(n.toLowerCase())) ||
               (node.nodeId != null && node.nodeId.toLowerCase().contains(n.toLowerCase())))
            {
                matchingNodes.add(node);
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

































