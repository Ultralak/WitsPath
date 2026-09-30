# WitsPath companion backend

Two pieces run on Google Cloud. The Android app talks only to the first.

```
Android app --(Firebase ID token)--> companionMessage  (Cloud Function, Node 22)
                                        |-- Claude Haiku 4.5 (tool-use loop, key kept as a secret)
                                        |-- Firestore (nodes, edges, floors, path_status, reports, chat_sessions, phrase_templates)
                                        '-- routing-service  (Cloud Run, Java, the same routing-core A* as the app)
```

The model never computes a route, distance or time. Routes come only from the routing service, the
function re-checks them against the graph, and a number guard replaces any reply that contains a number
no tool returned.

## One-time setup

You need the Firebase CLI (`npm i -g firebase-tools`), the `gcloud` CLI, and the project on the **Blaze**
(pay-as-you-go) plan. Cloud Functions that call an outside API and use secrets require it.

```bash
firebase login
gcloud auth login
gcloud config set project wavelets-wits-nav
```

## 1. Deploy the routing service (Cloud Run)

Run from the repository root. The service is private, so only the function can call it.

```bash
gcloud builds submit --config routing-service/cloudbuild.yaml .
gcloud run deploy witspath-routing \
  --image gcr.io/wavelets-wits-nav/witspath-routing \
  --region us-central1 \
  --no-allow-unauthenticated \
  --memory 512Mi --max-instances 3
```

The first command builds the image from `routing-service/Dockerfile` in Cloud Build, so Docker does not
need to be running on your computer. The Dockerfile and Cloud Build config have not been run yet; the Java
server itself was tested locally (the Commerce Library route returned 68.4 metres).

Note the service URL it prints, then let the function's service account call it:

```bash
gcloud run services add-iam-policy-binding witspath-routing --region us-central1 \
  --member "serviceAccount:559692559984-compute@developer.gserviceaccount.com" \
  --role roles/run.invoker
```

## 2. Store the API key and deploy the function

```bash
cd functions
npm install
firebase functions:secrets:set ANTHROPIC_API_KEY      # paste the key when asked; it is never written to a file
firebase deploy --only functions
```

During the deploy, `ROUTING_URL` is asked for: paste the Cloud Run URL from step 1.

The deploy prints the URL of `companionMessage`. Put that full URL in the Android app:
`app/src/main/res/values/strings.xml`, `companion_endpoint`.

## 3. Data the companion reads

- `nodes`, `edges`, `floors`: already seeded from `graph_data.json` (drawer, "Populate Firestore").
- `phrase_templates/{lang}/phrases/{key}` = `{ text, verifiedBy }`: translated direction phrases. A phrase is
  shown only when `text` and `verifiedBy` are both non-empty; otherwise directions fall back to English.
- `path_status/{nodeId}` = `{ blocked: true, reason }`: the team marks a place blocked.
- Firestore rules: see `firestore.rules.example`. It is an example only and is not deployed by this project.

## Guard rails

- `maxInstances: 5` and a per-user limit of 20 messages a minute cap how much one person can spend.
- In the Anthropic Console, set a monthly spend limit on the key.
- Anonymous reports are saved but never count toward flagging a path.
- Only English is marked "full" until a native speaker has reviewed real replies in another language.

## Tests

```bash
cd functions && npm test          # 39 tests, no network and no API key needed
./gradlew :routing-core:test :routing-service:test
```
