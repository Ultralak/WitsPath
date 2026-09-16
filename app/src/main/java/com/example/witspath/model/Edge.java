package com.example.witspath.model;

public class Edge
{
    public Node node1;
    public Node node2;
    public double distance;
    public boolean ramp;
    public boolean stairs;
    public boolean elevator;
    public boolean status;

    public Edge(Node n1, Node n2, double d, boolean r, boolean s, boolean e, boolean st)
    {
        node1 = n1;
        node2 = n2;
        distance = d;
        ramp = r;
        stairs = s;
        elevator = e;
        status = st;

        n1.addNeighbour(n2);
        n2.addNeighbour(n1);
        n1.addEdge(this);
        n2.addEdge(this);
    }
}
