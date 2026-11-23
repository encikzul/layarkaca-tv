import { refresh, feed } from "../src/bola.js";

// GET /bola.json: what the TVs read. State lives in the BOLA KV namespace.
export async function onRequestGet({ env }) {
  const now = Date.now();
  let state = (await env.BOLA.get("state", "json")) || {};
  if (env.API_FOOTBALL_KEY) {
    const r = await refresh(state, now, env);
    state = r.state;
    if (r.changed) await env.BOLA.put("state", JSON.stringify(state));
  }
  return new Response(JSON.stringify(feed(state, now)), {
    headers: {
      "content-type": "application/json; charset=utf-8",
      "cache-control": "public, max-age=60",
      "access-control-allow-origin": "*",
    },
  });
}
