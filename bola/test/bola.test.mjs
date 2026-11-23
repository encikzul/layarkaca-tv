import test from "node:test";
import assert from "node:assert/strict";
import { shape, active, plan, prune, feed, refresh, mytDate } from "../src/bola.js";

const T = Date.parse("2026-11-23T14:00:00+08:00");   // Monday 2 PM in Malaysia
const fx = (id, league, home, away, st, ko, goals = [null, null]) => ({
  fixture: { id, date: new Date(ko).toISOString(), status: { short: st, elapsed: st === "2H" ? 67 : null } },
  league: { id: league, name: "League " + league },
  teams: { home: { id: home[0], name: home[1] }, away: { id: away[0], name: away[1] } },
  goals: { home: goals[0], away: goals[1] },
});

test("Malaysian dates", () => {
  assert.equal(mytDate(Date.parse("2026-11-23T16:30:00Z")), "2026-11-24");   // 00:30 on the 24th in Malaysia
  assert.equal(mytDate(T, -1), "2026-11-22");
});

test("keeps our competitions and Malaysia's matches, drops the rest", () => {
  assert.equal(shape(fx(1, 39, [40, "Liverpool"], [42, "Arsenal"], "NS", T)).comp, "EPL");
  assert.equal(shape(fx(2, 278, [2523, "JDT"], [2526, "Selangor"], "FT", T, [3, 1])).comp, "Liga Super");
  const aff = shape(fx(3, 4321, [1538, "Malaysia"], [1, "Vietnam"], "NS", T));
  assert.equal(aff.home, "Malaysia");
  assert.equal(shape(fx(4, 140, [529, "Barcelona"], [530, "Atletico"], "NS", T)), null);   // La Liga: not ours
  assert.equal(shape(fx(5, 39, [40, "Liverpool"], [42, "Arsenal"], "PST", T)), null);       // postponed
});

test("goal scorers from events", () => {
  const f = fx(6, 39, [40, "Liverpool"], [42, "Arsenal"], "2H", T, [2, 1]);
  f.events = [
    { type: "Goal", detail: "Normal Goal", team: { id: 40 }, player: { name: "Mohamed Salah" }, time: { elapsed: 23 } },
    { type: "Goal", detail: "Penalty", team: { id: 42 }, player: { name: "Bukayo Saka" }, time: { elapsed: 41 } },
    { type: "Goal", detail: "Own Goal", team: { id: 40 }, player: { name: "W. Saliba" }, time: { elapsed: 58 } },
    { type: "Goal", detail: "Missed Penalty", team: { id: 42 }, player: { name: "X" }, time: { elapsed: 60 } },
    { type: "Card", detail: "Yellow Card", team: { id: 40 }, player: { name: "Y" }, time: { elapsed: 30 } },
  ];
  const m = shape(f);
  assert.deepEqual(m.goals, { h: ["Salah 23'", "Saliba 58' (bs)"], a: ["Saka 41' (p)"] });
  assert.equal(m.min, 67);
});

test("asks about a match only around its time", () => {
  const m = { st: "NS", ko: T };
  assert.equal(active(m, T - 10 * 60_000), false);
  assert.equal(active(m, T - 2 * 60_000), true);
  assert.equal(active(m, T + 4 * 3600_000), false);
  assert.equal(active({ st: "2H", ko: T - 3600_000 }, T), true);
  assert.equal(active({ st: "FT", ko: T - 3600_000 }, T), false);
});

test("plan: first run fetches seasons, the day's fixtures and Malaysia's matches", () => {
  const p = plan({}, T, 300);
  assert.equal(p.seasons.length, 6);
  assert.equal(p.range, true);
  assert.equal(p.teams, true);
});

test("plan: same day, nothing live, nothing to fetch", () => {
  const seasons = Object.fromEntries(["278", "499", "500", "1170", "39", "2"].map((id) => [id, { season: 2026, at: T }]));
  const p = plan({ seasons, rangeAt: T - 3600_000, teamsAt: T - 3600_000, matches: {} }, T, 300);
  assert.deepEqual(p, { seasons: [], range: false, teams: false, live: [] });
});

test("plan: live checks wait LIVE_SECONDS, and a failure waits 10 minutes", () => {
  const seasons = Object.fromEntries(["278", "499", "500", "1170", "39", "2"].map((id) => [id, { season: 2026, at: T }]));
  const base = { seasons, rangeAt: T, teamsAt: T, matches: { 9: { id: 9, st: "2H", ko: T - 3600_000 } } };
  assert.deepEqual(plan({ ...base, liveAt: T - 100_000 }, T, 300).live, []);
  assert.deepEqual(plan({ ...base, liveAt: T - 300_000 }, T, 300).live, [9]);
  assert.deepEqual(plan({ ...base, errorAt: T - 60_000 }, T, 300).live, []);
});

test("a new Malaysian day fetches the fixtures again", () => {
  const seasons = Object.fromEntries(["278", "499", "500", "1170", "39", "2"].map((id) => [id, { season: 2026, at: T }]));
  const lastNight = Date.parse("2026-11-22T23:30:00+08:00");
  assert.equal(plan({ seasons, rangeAt: lastNight, teamsAt: lastNight }, T, 300).range, true);
});

test("prune keeps yesterday to three days ahead", () => {
  const ms = { 1: { id: 1, ko: Date.parse("2026-11-21T20:00:00+08:00") }, 2: { id: 2, ko: Date.parse("2026-11-22T20:00:00+08:00") },
    3: { id: 3, ko: Date.parse("2026-11-26T20:00:00+08:00") }, 4: { id: 4, ko: Date.parse("2026-11-27T20:00:00+08:00") } };
  assert.deepEqual(Object.keys(prune(ms, T)), ["2", "3"]);
});

test("feed sorts live, upcoming and results", () => {
  const matches = {
    1: { id: 1, st: "FT", ko: T - 20 * 3600_000 }, 2: { id: 2, st: "2H", ko: T - 3600_000 },
    3: { id: 3, st: "NS", ko: T + 3600_000 }, 4: { id: 4, st: "FT", ko: T - 3 * 3600_000 },
  };
  const f = feed({ matches, updated: T }, T);
  assert.deepEqual(f.live.map((m) => m.id), [2]);
  assert.deepEqual(f.upcoming.map((m) => m.id), [3]);
  assert.deepEqual(f.results.map((m) => m.id), [4, 1]);
});

test("refresh with a fake API-Football: spread over minutes, then quiet, then live", async () => {
  const calls = [];
  const get = async (key, path) => {
    calls.push(path);
    assert.equal(key, "k");
    if (path.startsWith("/leagues")) return [{ seasons: [{ year: 2025, current: false }, { year: 2026, current: true }] }];
    if (path.startsWith("/fixtures?league=39")) return [fx(11, 39, [40, "Liverpool"], [42, "Arsenal"], "NS", T + 3600_000)];
    if (path.startsWith("/fixtures?team=1538&next")) return [fx(12, 4321, [1538, "Malaysia"], [1, "Vietnam"], "NS", T + 48 * 3600_000)];
    return [];
  };
  const env = { API_FOOTBALL_KEY: "k", LIVE_SECONDS: "300" };
  // First request: 8 at most (the free plan allows 10 a minute).
  let r = await refresh({}, T, env, get);
  assert.equal(calls.length, 8);
  assert.equal(r.state.error, undefined);
  // Another TV asks 20 seconds later: same minute, nothing more.
  r = await refresh(r.state, T + 20_000, env, get);
  assert.equal(calls.length, 8);
  // A minute later the rest goes out: 16 in all, none twice.
  r = await refresh(r.state, T + 61_000, env, get);
  assert.equal(calls.length, 16);
  assert.equal(new Set(calls).size, 16);
  assert.ok(calls.includes("/fixtures?league=39&season=2026&from=2026-11-22&to=2026-11-26&timezone=Asia/Kuala_Lumpur"));
  assert.deepEqual(Object.keys(r.state.matches).sort(), ["11", "12"]);
  assert.ok(r.state.rangeAt && r.state.teamsAt);
  // Later the same day: nothing to fetch.
  r = await refresh(r.state, T + 30 * 60_000, env, get);
  assert.equal(calls.length, 16);
  // When Liverpool kick off, one request for the live score.
  r = await refresh(r.state, T + 3600_000 + 60_000, env, get);
  assert.deepEqual(calls.slice(16), ["/fixtures?ids=11&timezone=Asia/Kuala_Lumpur"]);
  assert.ok(r.state.liveAt);
});

test("an API failure is kept and backs off", async () => {
  const get = async () => { throw new Error("API-Football HTTP 500"); };
  const { state } = await refresh({}, T, { API_FOOTBALL_KEY: "k" }, get);
  assert.match(state.error, /500/);
  assert.equal(state.errorAt, T);
  assert.deepEqual(plan(state, T + 60_000, 300).seasons, []);
});

test("the feed shows API-Football's last error, without the key", () => {
  const f = feed({ matches: {}, error: "API-Football: {\"plan\":\"no access\"}", errorAt: T }, T);
  assert.match(f.error, /no access/);
  assert.equal(feed({ matches: {} }, T).error, undefined);
});
