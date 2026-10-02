# Map digitizer

`wavelets-map-digitizer.html` is the page the team uses to trace the campus map and export the routing graph
(`wavelets-graph.json`). Open it in a browser; it has no server and needs no install.

## Where the exported graph goes
The exported file is the campus graph. Copy it to these places, which must stay identical:

1. `app/src/main/assets/graph_data.json` in this repository (the app's map; the app's place ids come from it).
2. `public/data/wits-braamfontein-map.json` in the WitsPath-Web repository (the companion's backend uses the
   same map, because route cards carry place ids).

If the app and the backend have different maps, the companion's routes cannot be shown in the app.

## Map image
The app draws the graph over `app/src/main/res/drawable-nodpi/campusmap.webp`. The graph's floor entry
(`imageWidth` and `imageHeight`, currently 4150 x 3380) must have the same shape as that image, or every point
will be misplaced.

## Large source files that were removed from the repository
`path-network-map.png` (2.4 MB) and `wits-map-v5-icons-nopanel.zip` (6 MB, the map as SVG and an 8000 px JPG)
were uploaded to the repository root and removed again to keep the project small. They are still in the git
history, commit `db4cc04`:

```
git show db4cc04:path-network-map.png > path-network-map.png
git show db4cc04:wits-map-v5-icons-nopanel.zip > wits-map-v5-icons-nopanel.zip
```

Keep working copies of such files outside the repository, for example in the team's shared drive.
