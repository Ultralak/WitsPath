package com.example.witspath.model;

public class Edge
{
    public String edgeID;
    public Node node1;
    public Node node2;
    public double distance;
    public double accessibilityCost;
    public boolean status;

    public Edge(String i, Node n1, Node n2, double d, double ac, boolean st)
    {
        edgeID = i;
        node1 = n1;
        node2 = n2;
        distance = d;
        accessibilityCost = ac;
        status = st;

        n1.addNeighbour(n2);
        n2.addNeighbour(n1);
        n1.addEdge(this);
        n2.addEdge(this);
    }

    public double getWeight()
    {
        return distance * accessibilityCost;
    }

    public boolean isAccessible()
    {
        return status && accessibilityCost < 999;
    }

    public Node other(Node n1)
    {
        if(node1 == n1)
        {
            return node2;
        }

        if(node2 == n1)
        {
            return node1;
        }

        return null;
    }
}
