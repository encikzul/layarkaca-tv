// LayarKaca Bola: football scores for the TV's Bola Sepak display, from API-Football.
//
// The API key stays here (a Cloudflare secret). TVs never call API-Football: they read /bola.json, and this
// server decides when to ask API-Football, so the number of TVs does not change how many requests it makes.
// Requests drive the refreshes (no cron): the saved state says what is stale.

export const LEAGUES = {
  278: "Liga Super", 499: "Piala Malaysia", 500: "Piala FA", 1170: "Piala MFL",
  39: "EPL", 2: "Liga Juara-Juara",
};
// Harimau Malaya and Malaysia U23: every match, whatever the competition.
export const TEAMS = { 1538: "Malaysia", 10945: "Malaysia U23" };

const API = "https://v3.football.api-sports.io";
const TZ = "Asia/Kuala_Lumpur";
const MYT = 8 * 3600_000;
const HOUR = 3600_000;
export const LIVE = new Set(["1H", "HT", "2H", "ET", "BT", "P", "SUSP", "INT", "LIVE"]);
export const DONE = new Set(["FT", "AET", "PEN"]);
const DROP = new Set(["PST", "CANC", "ABD", "AWD", "WO", "TBD"]);

/** "2026-11-23" for the Malaysian date of {@code ms}, moved by {@code days}. */
export function mytDate(ms, days = 0) {
  return new Date(ms + MYT + days * 24 * HOUR).toISOString().slice(0, 10);
}

/** The match as the TV needs it; null for one that is not ours or not happening. */
export function shape(f) {
  const leagueId = f?.league?.id, home = f?.teams?.home, away = f?.teams?.away;
  if (!home || !away || !f.fixture) return null;
  const ours = leagueId in LEAGUES || home.id in TEAMS || away.id in TEAMS;
  const st = f.fixture.status?.short || "NS";
  if (!ours || DROP.has(st)) return null;
  const m = {
    id: f.fixture.id,
    comp: LEAGUES[leagueId] || shortName(f.league?.name || ""),
    home: home.name, away: away.name, hid: home.id, aid: away.id,
    hs: f.goals?.home ?? null, as: f.goals?.away ?? null,
    st, min: f.fixture.status?.elapsed ?? null,
    ko: Date.parse(f.fixture.date) || 0,
  };
  if (Array.isArray(f.events)) {
    const goals = f.events.filter((e) => e.type === "Goal" && e.detail !== "Missed Penalty");
    const line = (e) => (e.player?.name || "?").split(" ").pop() + " " + (e.time?.elapsed ?? "") + "'"
        + (e.detail === "Own Goal" ? " (bs)" : e.detail === "Penalty" ? " (p)" : "");
    m.goals = {
      h: goals.filter((e) => e.team?.id === home.id).map(line),
      a: goals.filter((e) => e.team?.id === away.id).map(line),
    };
  }
  return m;
}

function shortName(name) {
  return name.replace(/^AFF\s+/, "AFF ").replace(/Championship/, "Ch.").slice(0, 22);
}

/** Should this match be asked about now? Playing, or about to start, or just finished without a final score. */
export function active(m, now) {
  if (LIVE.has(m.st)) return true;
  if (DONE.has(m.st)) return false;
  return m.st === "NS" && now >= m.ko - 5 * 60_000 && now <= m.ko + 3 * HOUR;
}

/** What to fetch now, given the saved state: {seasons, range, teams, live}. Pure, so it can be tested. */
export function plan(state, now, liveSeconds) {
  const todo = { seasons: [], range: false, teams: false, live: [] };
  // API-Football failed a moment ago: wait, so a down API does not use up the day's requests.
  if (state.errorAt && now - state.errorAt < 10 * 60_000) return todo;
  for (const id of Object.keys(LEAGUES)) {
    const s = state.seasons?.[id];
    if (!s || now - s.at > 7 * 24 * HOUR) todo.seasons.push(id);
  }
  const rangeDay = state.rangeAt ? mytDate(state.rangeAt) : "";
  // Once a Malaysian day: the day's fixtures and Malaysia's matches. Scores after that come from the live checks.
  if (!state.rangeAt || rangeDay !== mytDate(now)) todo.range = true;
  if (!state.teamsAt || mytDate(state.teamsAt) !== mytDate(now)) todo.teams = true;
  if (!state.liveAt || now - state.liveAt >= liveSeconds * 1000) {
    todo.live = Object.values(state.matches || {}).filter((m) => active(m, now)).map((m) => m.id).slice(0, 20);
  }
  return todo;
}

/** Keeps matches from yesterday (Malaysian time) to three days ahead. */
export function prune(matches, now) {
  const from = Date.parse(mytDate(now, -1) + "T00:00:00+08:00"), to = Date.parse(mytDate(now, 4) + "T00:00:00+08:00");
  const out = {};
  for (const m of Object.values(matches)) if (m.ko >= from && m.ko < to) out[m.id] = m;
  return out;
}

/** The file the TVs read. */
export function feed(state, now) {
  const all = Object.values(state.matches || {}).sort((a, b) => a.ko - b.ko);
  const startToday = Date.parse(mytDate(now) + "T00:00:00+08:00");
  return {
    v: 1,
    updated: state.updated || 0,
    // API-Football's last error, if the last attempt failed: shown so a problem can be seen from outside.
    // It is API-Football's message, never the key.
    ...(state.error ? { error: state.error, errorAt: state.errorAt } : {}),
    live: all.filter((m) => LIVE.has(m.st)),
    upcoming: all.filter((m) => m.st === "NS" && m.ko > now - 3 * HOUR).slice(0, 8),
    results: all.filter((m) => DONE.has(m.st) && m.ko >= startToday - 24 * HOUR).reverse().slice(0, 10),
  };
}

async function api(key, path) {
  const r = await fetch(API + path, { headers: { "x-apisports-key": key } });
  if (!r.ok) throw new Error("API-Football HTTP " + r.status);
  const d = await r.json();
  const errors = d.errors && (Array.isArray(d.errors) ? d.errors.length : Object.keys(d.errors).length);
  if (errors) throw new Error("API-Football: " + JSON.stringify(d.errors).slice(0, 200));
  return d.response || [];
}

/** Brings the state up to date, as far as the plan says. Returns the new state; never throws. */
export async function refresh(state, now, env, get = api) {
  const key = env.API_FOOTBALL_KEY;
  const liveSeconds = Number(env.LIVE_SECONDS) || 300;
  const s = { seasons: {}, matches: {}, ...state };
  const todo = plan(s, now, liveSeconds);
  let changed = false;
  const put = (list) => { for (const f of list) { const m = shape(f); if (m) s.matches[m.id] = m; } changed = true; };
  try {
    for (const id of todo.seasons) {
      const r = await get(key, `/leagues?id=${id}&current=true`);
      const season = r[0]?.seasons?.find((x) => x.current)?.year ?? r[0]?.seasons?.at(-1)?.year;
      if (season) s.seasons = { ...s.seasons, [id]: { season, at: now } };
      changed = true;
    }
    if (todo.range) {
      const from = mytDate(now, -1), to = mytDate(now, 3);
      for (const id of Object.keys(LEAGUES)) {
        const season = s.seasons[id]?.season;
        if (season) put(await get(key, `/fixtures?league=${id}&season=${season}&from=${from}&to=${to}&timezone=${TZ}`));
      }
      s.rangeAt = now;
    }
    if (todo.teams) {
      for (const id of Object.keys(TEAMS)) {
        put(await get(key, `/fixtures?team=${id}&last=2&timezone=${TZ}`));
        put(await get(key, `/fixtures?team=${id}&next=2&timezone=${TZ}`));
      }
      s.teamsAt = now;
    }
    if (todo.live.length) {
      put(await get(key, `/fixtures?ids=${todo.live.join("-")}&timezone=${TZ}`));
      s.liveAt = now;
    }
    delete s.error;
    delete s.errorAt;
  } catch (e) {
    s.error = String(e.message || e).slice(0, 200);
    s.errorAt = now;
    changed = true;
  }
  s.matches = prune(s.matches, now);
  if (changed) s.updated = now;
  return { state: s, changed };
}
