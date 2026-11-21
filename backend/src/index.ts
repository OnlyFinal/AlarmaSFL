export interface Env {
  DB: D1Database;
  UPSTREAM_URL?: string;
}

type PriceMap = Record<string, number>;

const DEFAULT_UPSTREAM = "https://sfl.world/api/v1/prices";
const YEAR_SECONDS = 365 * 24 * 60 * 60;

function json(data: unknown, status = 200): Response {
  return new Response(JSON.stringify(data), {
    status,
    headers: {
      "content-type": "application/json; charset=utf-8",
      "cache-control": "no-store",
      "access-control-allow-origin": "*",
      "access-control-allow-methods": "GET, OPTIONS",
      "access-control-allow-headers": "Content-Type",
    },
  });
}

function findMarket(value: unknown, wanted: string, depth = 0): Record<string, unknown> | null {
  if (depth > 8 || value === null || typeof value !== "object") return null;
  if (Array.isArray(value)) {
    for (const child of value) {
      const found = findMarket(child, wanted, depth + 1);
      if (found) return found;
    }
    return null;
  }
  const obj = value as Record<string, unknown>;
  for (const [key, child] of Object.entries(obj)) {
    if (key.toLowerCase() === wanted && child && typeof child === "object" && !Array.isArray(child)) {
      return child as Record<string, unknown>;
    }
  }
  for (const child of Object.values(obj)) {
    const found = findMarket(child, wanted, depth + 1);
    if (found) return found;
  }
  return null;
}

function numericPrice(value: unknown, depth = 0): number | null {
  if (depth > 4) return null;
  if (typeof value === "number") return Number.isFinite(value) && value >= 0 ? value : null;
  if (typeof value === "string") {
    const parsed = Number(value.trim().replace(",", "."));
    return Number.isFinite(parsed) && parsed >= 0 ? parsed : null;
  }
  if (value && typeof value === "object" && !Array.isArray(value)) {
    const obj = value as Record<string, unknown>;
    for (const key of ["price", "floor", "value", "min"]) {
      const parsed = numericPrice(obj[key], depth + 1);
      if (parsed !== null) return parsed;
    }
  }
  return null;
}

function collectPrices(value: unknown, out: PriceMap, depth = 0): void {
  if (depth > 8 || value === null || typeof value !== "object") return;
  if (Array.isArray(value)) {
    for (const child of value) collectPrices(child, out, depth + 1);
    return;
  }
  for (const [key, child] of Object.entries(value as Record<string, unknown>)) {
    const price = numericPrice(child);
    if (price !== null && !["price", "floor", "value", "min", "max", "updated", "timestamp"].includes(key.toLowerCase())) {
      out[key] = price;
    } else if (child && typeof child === "object") {
      collectPrices(child, out, depth + 1);
    }
  }
}

async function capture(env: Env): Promise<{ count: number; capturedAt: number }> {
  const response = await fetch(env.UPSTREAM_URL || DEFAULT_UPSTREAM, {
    headers: { accept: "application/json", "user-agent": "AlarmaSFL-API/1.0" },
    signal: AbortSignal.timeout(20_000),
  });
  if (!response.ok) throw new Error(`Fuente de precios respondió HTTP ${response.status}`);
  const payload: unknown = await response.json();
  const root = (payload && typeof payload === "object" && "data" in payload)
    ? (payload as { data: unknown }).data : payload;
  const plaza = findMarket(root, "p2p");
  if (!plaza) throw new Error("La respuesta no contiene el mercado Plaza (p2p)");
  const prices: PriceMap = {};
  collectPrices(plaza, prices);
  const entries = Object.entries(prices).filter(([name, price]) => name.trim() && Number.isFinite(price));
  if (!entries.length) throw new Error("La fuente no devolvió precios válidos para Plaza");

  const capturedAt = Math.floor(Date.now() / 1000);
  const stmt = env.DB.prepare(
    "INSERT INTO price_history (market, crop, price, captured_at) VALUES ('p2p', ?, ?, ?) " +
    "ON CONFLICT(market, crop, captured_at) DO UPDATE SET price = excluded.price"
  );
  await env.DB.batch(entries.map(([crop, price]) => stmt.bind(crop, price, capturedAt)));

  // Purga diaria de datos con más de 365 días.
  const date = new Date(capturedAt * 1000);
  if (date.getUTCHours() === 0 && date.getUTCMinutes() < 5) {
    await env.DB.prepare("DELETE FROM price_history WHERE captured_at < ?")
      .bind(capturedAt - YEAR_SECONDS).run();
  }
  return { count: entries.length, capturedAt };
}

async function route(request: Request, env: Env): Promise<Response> {
  if (request.method === "OPTIONS") return new Response(null, { status: 204, headers: {
    "access-control-allow-origin": "*",
    "access-control-allow-methods": "GET, OPTIONS",
    "access-control-allow-headers": "Content-Type",
  }});
  if (request.method !== "GET") return json({ error: "Method not allowed" }, 405);

  const url = new URL(request.url);
  if (url.pathname === "/v1/health") {
    const row = await env.DB.prepare("SELECT MAX(captured_at) AS last_capture, COUNT(*) AS points FROM price_history WHERE market='p2p'").first<{last_capture:number|null;points:number}>();
    return json({ ok: true, market: "p2p", interval_minutes: 5, retention_days: 365, ...row });
  }
  if (url.pathname === "/v1/prices/current") {
    const rows = await env.DB.prepare(
      "SELECT h.crop, h.price, h.captured_at FROM price_history h " +
      "JOIN (SELECT crop, MAX(captured_at) AS latest FROM price_history WHERE market='p2p' GROUP BY crop) x " +
      "ON h.crop=x.crop AND h.captured_at=x.latest WHERE h.market='p2p' ORDER BY h.crop"
    ).all<{crop:string;price:number;captured_at:number}>();
    const prices: Record<string, number> = {};
    for (const row of rows.results) prices[row.crop] = row.price;
    const latest = rows.results.reduce((max, row) => Math.max(max, row.captured_at), 0);
    return json({ market: "p2p", updated_at: latest, prices });
  }
  if (url.pathname === "/v1/prices/history") {
    const crop = (url.searchParams.get("crop") || "").trim();
    if (!crop || crop.length > 100) return json({ error: "Parámetro crop requerido (máximo 100 caracteres)" }, 400);
    const range = url.searchParams.get("range") || "24h";
    const durations: Record<string, number> = { "1h":3600, "6h":21600, "24h":86400, "7d":604800, "30d":2592000, "1y":YEAR_SECONDS };
    if (!(range in durations)) return json({ error: "Rango no válido" }, 400);
    const now = Math.floor(Date.now() / 1000);
    const rows = await env.DB.prepare(
      "SELECT price, captured_at FROM price_history WHERE market='p2p' AND crop=? AND captured_at>=? ORDER BY captured_at ASC"
    ).bind(crop, now - durations[range]).all<{price:number;captured_at:number}>();
    return json({ market:"p2p", crop, range, points: rows.results.map(r => ({ t:r.captured_at * 1000, p:r.price })) });
  }
  return json({ error: "Not found" }, 404);
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    try { return await route(request, env); }
    catch (error) {
      return json({ error: error instanceof Error ? error.message : "Internal error" }, 502);
    }
  },
  async scheduled(_event: ScheduledController, env: Env, ctx: ExecutionContext): Promise<void> {
    ctx.waitUntil(capture(env));
  },
};
