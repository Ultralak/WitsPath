"use strict";

/**
 * Client for the routing-service (the same routing-core A* the Android app uses).
 * Fails closed: any problem throws, and the caller tells the user it cannot confirm a route.
 *
 * @param url       base URL of the Cloud Run service
 * @param getToken  async () => identity token for the service, or null when it is public
 * @param fetchFn   injectable for tests
 */
function createRoutingClient({ url, getToken, fetchFn = fetch, timeoutMs = 8000 }) {
  return {
    async route(request) {
      if (!url) throw new Error("ROUTING_URL is not configured");
      const headers = { "Content-Type": "application/json" };
      const token = getToken ? await getToken() : null;
      if (token) headers.Authorization = `Bearer ${token}`;
      const res = await fetchFn(`${url.replace(/\/$/, "")}/v1/route`, {
        method: "POST",
        headers,
        body: JSON.stringify(request),
        signal: AbortSignal.timeout(timeoutMs),
      });
      const body = await res.json().catch(() => null);
      if (!body || typeof body !== "object") throw new Error(`Routing service returned HTTP ${res.status}`);
      if (res.status >= 500) throw new Error(`Routing service error ${res.status}`);
      return body;
    },
  };
}

module.exports = { createRoutingClient };
