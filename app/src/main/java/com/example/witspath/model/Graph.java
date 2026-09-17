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

        JSONArray nodesArray = root.getJSONArray("nodes");

        for (int i = 0; i < nodesArray.length(); i++)
        {
            JSONObject n = nodesArray.getJSONObject(i);

            String name = n.optString("label", n.getString("nodeId"));
            String area = n.optString("floorId", "WSS");
            double x = n.getDouble("x");
            double y = n.getDouble("y");
            String type = n.getString("type");

            new Node(name, area, x, y, type);
        }

        JSONArray edgesArray = root.getJSONArray("edges");

        for (int i = 0; i < edgesArray.length(); i++)
        {
            JSONObject e = edgesArray.getJSONObject(i);

            String fromName = e.optString("from", e.optString("fromNodeId"));
            String toName = e.optString("to", e.optString("toNodeId"));
            double distance = e.getDouble("distance");
            double accessibilityCost = e.optDouble("accessibilityCost", 1.0);
            boolean ramp = e.optBoolean("ramp", false);
            Boolean stairs = optNullableBoolean(e, "stairs");
            Boolean elevator = optNullableBoolean(e, "elevator");
            boolean status = e.optString("status", "ok").equalsIgnoreCase("ok");

            Node from = Node.getByName(fromName);
            Node to = Node.getByName(toName);

            if (from == null || to == null)
            {
                System.out.println("Warning: edge references unknown node ('"
                        + fromName + "' -> '" + toName + "'), skipping.");
                continue;
            }

            new Edge(from, to, distance, accessibilityCost, ramp, Boolean.TRUE.equals(stairs), Boolean.TRUE.equals(elevator), status);
        }
    }

    public static void loadFromAssets(Context context, String assetFileName)
    {
        try (InputStream is = context.getAssets().open(assetFileName))
        {
            String json = readStream(is);
            loadFromJson(json);
        }
        catch (IOException e)
        {
            System.out.println("Failed to read asset file '" + assetFileName + "': " + e.getMessage());
        }
        catch (JSONException e)
        {
            System.out.println("Failed to parse JSON in '" + assetFileName + "': " + e.getMessage());
        }
    }

    public static void loadFromFile(String filePath)
    {
        try (InputStream is = new FileInputStream(filePath))
        {
            String json = readStream(is);
            loadFromJson(json);
        }
        catch (IOException e)
        {
            System.out.println("Failed to read file '" + filePath + "': " + e.getMessage());
        }
        catch (JSONException e)
        {
            System.out.println("Failed to parse JSON in '" + filePath + "': " + e.getMessage());
        }
    }

    private static String readStream(InputStream is) throws IOException
    {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(is, StandardCharsets.UTF_8)))
        {
            String line;

            while ((line = reader.readLine()) != null)
            {
                sb.append(line).append('\n');
            }
        }
        return sb.toString();
    }

    private static Boolean optNullableBoolean(JSONObject obj, String key)
    {
        if (!obj.has(key) || obj.isNull(key))
        {
            return null;
        }
        return obj.optBoolean(key);
    }
}
