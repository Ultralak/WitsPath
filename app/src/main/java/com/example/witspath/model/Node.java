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

    // Which Floor this node's plan belongs to (see Floor.java). Distinct from
    // area -- area is a building/precinct grouping, floorId is a physical
    // level. Nullable: existing data/callers that don't supply one are
    // unaffected (see the 5-arg constructor below).
    public String floorId;

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
        // --- existing values -- kept as-is; no node in the current data
        // matches WSS or NCB by name, so left untouched rather than guessed at ---
        WSS,
        ARM,
        COMMERCE,
        NCB,
        TOWER,

        // --- added from wavelets-graph JSON: 24 of 33 nodes didn't match any
        // existing value. Grouped by spatial proximity + shared building-complex
        // naming, following the existing convention of naming each value after
        // its anchor building. Worth sanity-checking against how the team
        // actually thinks about these precincts -- boundaries between
        // BUSINESS_SCIENCES / SCIENCE_STADIUM / KAMBULE in particular are close
        // together spatially and could reasonably be split differently. ---
        LAW,                // School of Law, Law Clinic
        ACCOUNTANCY,        // School of Accountancy, -2, -3
        FACILITIES,         // DJ DU Plessis Centre, Waste Disposal Centre,
        // Facilities & Services, Chalstry Centre, Procurement Office
        BUSINESS_SCIENCES,  // School of Business Sciences, -2, Old Grand Stand
        // (+ its ramp), Wits Plus
        KAMBULE,             // Mathematical Science Building, TW Kambule MSB
        SCIENCE_STADIUM,    // Science Stadium Auditoriums, Science Stadium Laboratories
        CCDU,               // CCDU, CCDU - 2
        GENMIN,             // Genmin Laboratories
        FLOWER_HALL         // Flower Hall, CLTD
    }

    // Original constructor, kept exactly as-is so existing callers (Graph.java,
    // FirestoreGraphConverter.java, tests) don't break. floorId defaults to null.
    public Node(String n, String a, double x, double y, String t)
    {
        this(n, a, x, y, t, null);
    }

    // New overload -- use this one when the caller actually knows which floor
    // a node belongs to (e.g. once Graph.java/FirestoreGraphConverter.java are
    // updated to read floorId from the JSON/DTOs and pass it through).
    public Node(String n, String a, double x, double y, String t, String floorId)
    {
        id = count.incrementAndGet();
        name = n;
        this.floorId = floorId;

        try
        {
            area = Area.valueOf(a.toUpperCase());
        }
        catch (Exception e)
        {
            // Default to WSS if floorId doesn't match enum
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

































