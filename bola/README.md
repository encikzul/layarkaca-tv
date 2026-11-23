# LayarKaca Bola

The small server behind the **Bola Sepak** display: `https://layarkaca-bola.pages.dev/bola.json`.

TVs never call API-Football. They read `/bola.json`; this server holds the API-Football key as a secret and decides
when to ask, so a thousand TVs cost the same requests as one:

- once a Malaysian day: the fixtures from yesterday to three days ahead for Liga Super (278), Piala Malaysia (499),
  Piala FA (500), Piala MFL (1170), EPL (39) and Liga Juara-Juara (2), and the last and next two matches of
  Harimau Malaya (1538) and Malaysia U23 (10945): 10 requests, plus 6 a week for the current seasons;
- while one of those matches is on: one request for their live scores and scorers every `LIVE_SECONDS`
  (300 on API-Football's free plan of 100 requests a day; 60 needs the Pro plan).

After a failure it waits 10 minutes, so an API-Football outage cannot use up the day's requests.

## Tests

```bash
cd bola
node --test test/bola.test.mjs
```

## First deploy

You need the Cloudflare account and your API-Football key (dashboard.api-football.com).

```bash
cd bola
npm install
npx wrangler login
npx wrangler kv namespace create BOLA      # copy the id into wrangler.toml, in place of REPLACE_WITH_KV_ID
npm run project:create                     # must print https://layarkaca-bola.pages.dev
npx wrangler pages secret put API_FOOTBALL_KEY --project-name layarkaca-bola
npm run deploy
```

Check `https://layarkaca-bola.pages.dev/bola.json`: it returns `live`, `upcoming` and `results`. If the project
name is taken and Cloudflare gives another address, change `URL_FEED` in
`launcher/app/src/main/java/com/gathin/tvhome/Bola.java` to match before releasing the app.

## How many people use it

Only TVs showing Bola Sepak ask this server. Cloudflare dashboard › Workers & Pages › layarkaca-bola › Metrics
shows the requests a day: that is the display's use, with no tracking of any TV.
