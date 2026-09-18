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

public class Graph
{
    public static void loadFromJson(String jsonText) throws JSONException
    {

        JSONObject root = new JSONObject(jsonText);
        Node.clearNodes();

        JSONArray floorsArray = root.optJSONArray("floors");

        if(floorsArray != null)
        {
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

                new Floor(
                        floorId,
                        name,
                        level,
                        imageWidth,
                        imageHeight,
                        metresPerPixel
                );
            }
        }

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

            new Node(
                    nodeId,
                    name,
                    x,
                    y,
                    type,
                    floorId
            );
        }

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
                    e.optString("status", "ok");

            Node from = Node.getByID(fromNodeId);
            Node to = Node.getByID(toNodeId);

            if(from == null)
            {
                System.out.println(
                        "Warning: unknown fromNodeId: "
                                + fromNodeId
                );
                continue;
            }

            if(to == null)
            {
                System.out.println(
                        "Warning: unknown toNodeId: "
                                + toNodeId
                );
                continue;
            }

            boolean statusOK =
                    status.equalsIgnoreCase("ok");

            new Edge(
                    from,
                    to,
                    distance,
                    accessibilityCost,
                    statusOK
            );

            System.out.println(
                    "Loaded edge " + edgeId +
                            ": " + from.name +
                            " -> " + to.name
            );
        }
    }

    public static void loadFromAssets(
            Context context,
            String assetFileName)
    {
        try(InputStream is = context.getAssets().open(assetFileName))
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

    public static void loadFromFile(String filePath)
    {
        try(InputStream is = new FileInputStream(filePath))
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
}
