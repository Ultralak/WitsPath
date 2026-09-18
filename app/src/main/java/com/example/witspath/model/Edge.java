package com.example.witspath.model;

public class Edge
{
    public Node node1;
    public Node node2;
    public double distance;
    public double accessibilityCost;
    public boolean status;
    public boolean ramp;
    public boolean stairs;
    public boolean elevator;

    public Edge(Node n1, Node n2, double d, double ac, boolean st)
    {
        this(n1, n2, d, ac, false, false, false, st);
    }

    public Edge(Node n1, Node n2, double d, double ac, boolean rm, boolean sr, boolean el, boolean st)
    {
        node1 = n1;
        node2 = n2;
        distance = d;
        accessibilityCost = ac;
        ramp = rm;
        stairs = sr;
        elevator = el;
        status = st;

        n1.addNeighbour(n2);
        n2.addNeighbour(n1);
        n1.addEdge(this);
        n2.addEdge(this);
    }
}
