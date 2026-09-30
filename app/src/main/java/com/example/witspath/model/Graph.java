package com.example.witspath.model;

import android.content.Context;

import java.io.FileInputStream;
import java.util.ArrayList;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class Graph
{
    private Map<String, Node> nodes;
    private Map<String, Edge> edges;
    private Map<String, Floor> floors;

    public Graph()
    {
        nodes = new HashMap<>();
        edges = new HashMap<>();
        floors = new HashMap<>();
    }

    public void clear()
    {
        nodes.clear();
        edges.clear();
        floors.clear();
    }

    public void loadFromJson(String jsonText) throws JSONException
    {
        JSONObject root = new JSONObject(jsonText);
        clear();

        loadFloors(root);
        loadNodes(root);
        loadEdges(root);
    }

    private void loadFloors(JSONObject root) throws JSONException
    {
        JSONArray floorsArray = root.optJSONArray("floors");

        if(floorsArray == null)
        {
            return;
        }

        for(int i = 0; i < floorsArray.length(); i++)
        {
            JSONObject f = floorsArray.getJSONObject(i);

            String floorId = f.getString("floorId");
            String name = f.optString("name", "");
            int level = f.optInt("level", 0);
            int imageWidth = f.optInt("imageWidth", 0);
            int imageHeight = f.optInt("imageHeight", 0);

            double metresPerPixel =
                    f.optDouble("metresPerPixel", 1.0);

            Floor floor = new Floor(
                    floorId,
                    name,
                    level,
                    imageWidth,
                    imageHeight,
                    metresPerPixel
            );

            floors.put(floorId, floor);
        }
    }

    private void loadNodes(JSONObject root)
            throws JSONException
    {
        JSONArray nodesArray = root.getJSONArray("nodes");

        for(int i = 0; i < nodesArray.length(); i++)
        {
            JSONObject n = nodesArray.getJSONObject(i);

            String nodeId = n.getString("nodeId");

            String name = n.optString(
                    "label",
                    nodeId
            );

            String floorId = n.optString(
                    "floorId",
                    null
            );

            String type = n.getString("type");

            double x = n.getDouble("x");
            double y = n.getDouble("y");

            Node node = new Node(
                    nodeId,
                    name,
                    x,
                    y,
                    type,
                    floorId
            );

            nodes.put(nodeId, node);
        }
    }

    private void loadEdges(JSONObject root) throws JSONException
    {
        JSONArray edgesArray = root.getJSONArray("edges");

        for(int i = 0; i < edgesArray.length(); i++)
        {
            JSONObject e = edgesArray.getJSONObject(i);

            String edgeId = e.optString(
                    "edgeId",
                    "edge_" + i
            );

            String fromNodeId =
                    e.getString("fromNodeId");

            String toNodeId =
                    e.getString("toNodeId");

            double distance =
                    e.getDouble("distance");

            double accessibilityCost =
                    e.optDouble(
                            "accessibilityCost",
                            1.0
                    );

            String status =
                    e.optString(
                            "status",
                            "ok"
                    );

            Node from = nodes.get(fromNodeId);
            Node to = nodes.get(toNodeId);

            if(from == null)
            {
                System.out.println(
                        "Unknown node: " + fromNodeId
                );
                continue;
            }

            if(to == null)
            {
                System.out.println(
                        "Unknown node: " + toNodeId
                );
                continue;
            }

            if(distance < 0)
            {
                System.out.println(
                        "Negative distance: " + edgeId
                );
                continue;
            }

            if(accessibilityCost <= 0)
            {
                System.out.println(
                        "Invalid accessibility cost: "
                                + edgeId
                );
                continue;
            }

            boolean statusOK =
                    status.equalsIgnoreCase("ok");

            Edge edge = new Edge(
                    edgeId,
                    from,
                    to,
                    distance,
                    accessibilityCost,
                    statusOK
            );

            edges.put(edgeId, edge);
        }
    }

    public void loadFromAssets(
            Context context,
            String assetFileName)
    {
        try(InputStream is =
                    context.getAssets().open(assetFileName))
        {
            String json = readStream(is);

            loadFromJson(json);
        }
        catch(IOException e)
        {
            System.out.println(
                    "Failed to read asset file '"
                            + assetFileName
                            + "': "
                            + e.getMessage()
            );
        }
        catch(JSONException e)
        {
            System.out.println(
                    "Failed to parse JSON in '"
                            + assetFileName
                            + "': "
                            + e.getMessage()
            );
        }
    }

    public void loadFromFile(String filePath)
    {
        try(InputStream is =
                    new FileInputStream(filePath))
        {
            String json = readStream(is);

            loadFromJson(json);
        }
        catch(IOException e)
        {
            System.out.println(
                    "Failed to read file '"
                            + filePath
                            + "': "
                            + e.getMessage()
            );
        }
        catch(JSONException e)
        {
            System.out.println(
                    "Failed to parse JSON in '"
                            + filePath
                            + "': "
                            + e.getMessage()
            );
        }
    }

    private static String readStream(InputStream is)
            throws IOException
    {
        StringBuilder sb = new StringBuilder();

        try(BufferedReader reader =
                    new BufferedReader(
                            new InputStreamReader(
                                    is,
                                    StandardCharsets.UTF_8)))
        {
            String line;

            while((line = reader.readLine()) != null)
            {
                sb.append(line).append('\n');
            }
        }

        return sb.toString();
    }

    public Node getNodeByID(String id)
    {
        return nodes.get(id);
    }

    public Node getNodeByName(String name)
    {
        String id = null;
        for(Node node : nodes.values())
        {
            if(node.name.equalsIgnoreCase(name))
            {
                id = node.nodeId;
            }
        }

        return nodes.get(id);
    }

    public ArrayList<Node> searchByName(String search)
    {
        ArrayList<Node> result = new ArrayList<>();

        if(search == null)
        {
            return result;
        }

        String query = search.toLowerCase().trim();

        for(Node node : nodes.values())
        {
            if(node.name != null &&
                    node.name.toLowerCase().contains(query))
            {
                result.add(node);
            }
        }

        return result;
    }

    public Edge getEdgeByID(String id)
    {
        return edges.get(id);
    }

    public Map<String, Node> getNodes()
    {
        return nodes;
    }
    public Map<String, Edge> getEdges()
    {
        return edges;
    }
    public Map<String, Floor> getFloors()
    {
        return floors;
    }

    public ArrayList<Node> getNeighbours(Node node)
    {
        if(node == null)
        {
            return new ArrayList<>();
        }

        return node.neighbours;
    }

}
