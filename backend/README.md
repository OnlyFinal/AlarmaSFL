# API propia de AlarmaSFL (Cloudflare Workers + D1)

Backend para **Plaza (P2P) únicamente**, con captura programada cada 5 minutos y retención de 365 días.

## Qué incluye
- Cron de Cloudflare Workers cada 5 minutos.
- Lectura de la fuente upstream `https://sfl.world/api/v1/prices`, extracción del bloque `p2p`.
- Almacenamiento central en Cloudflare D1; un punto por cultivo y captura.
- Purga de puntos con más de 365 días, ejecutada en la primera captura de cada día UTC.
- API pública de solo lectura:
  - `GET /v1/health`
  - `GET /v1/prices/current`
  - `GET /v1/prices/history?crop=Pepper&range=24h`
- Rangos de historial: `1h`, `6h`, `24h`, `7d`, `30d`, `1y`.

## Despliegue (requiere una cuenta Cloudflare)
1. Instala Node.js y Wrangler: `npm install -g wrangler`.
2. Desde esta carpeta inicia sesión: `wrangler login`.
3. Crea la base: `wrangler d1 create alarmasfl-history`.
4. Copia el `database_id` devuelto dentro de `wrangler.toml`, sustituyendo el texto de ejemplo.
5. Aplica el esquema: `wrangler d1 execute alarmasfl-history --remote --file=./schema.sql`.
6. Despliega: `wrangler deploy`.
7. Prueba `https://alarmasfl-api.<tu-subdominio>.workers.dev/v1/health`.

El primer historial empezará a acumularse después del despliegue. No es posible reconstruir automáticamente el año anterior si esos datos nunca estuvieron en una base histórica accesible.

## Contrato de API
`/v1/prices/current` devuelve `{ market, updated_at, prices: { Cultivo: precio } }`.
`/v1/prices/history` devuelve `{ market, crop, range, points: [{ t: epoch_milliseconds, p: precio }] }`.

## Integración Android
El backend queda preparado, pero la app Android no se cambia a una URL ficticia: primero hay que desplegarlo y conocer la URL real del Worker. Después se configura esa URL en el cliente Android y se adapta el refresco de historial para consumir el endpoint central. El widget existente se mantiene intacto.
