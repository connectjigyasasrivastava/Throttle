# Throttle

A distributed rate limiter built in Java 21 and Spring Boot, backed by Redis for consistent rate limiting across horizontally scaled instances.

Rate limiting decisions are executed as atomic Redis Lua scripts, so concurrent requests hitting multiple application instances share a single source of truth and cannot race past the limit. A Resilience4j circuit breaker with a local in-memory fallback keeps the API protecting itself even if Redis becomes unreachable.

## Status

| Area | Status |
|------|--------|
| Token bucket algorithm (atomic Lua) | Done |
| Redis-backed shared state | Done |
| Sliding window counter algorithm | Done |
| Sliding window log algorithm | Done (currently uses the same weighted two-bucket estimate as the counter, not a true per-timestamp sorted-set log — see Notes) |
| Per-client rate limit filter (429 + standard headers) | Done |
| Config-driven path matching & algorithm selection | Done |
| Resilience4j circuit breaking + local in-memory fallback | Done |
| Load testing (k6) | Planned |
| AWS deployment (ALB, Auto Scaling, ElastiCache) | Planned |

## How it works

Each client (resolved by IP, honoring `X-Forwarded-For` when behind a proxy) gets rate-limiting state stored in Redis. Three algorithms are available, selectable via config:

- **Token bucket** (default): a bucket holds up to `capacity` tokens and refills at `refill-rate` tokens/sec. Every request consumes one token; an empty bucket yields an HTTP 429.
- **Sliding window counter**: estimates requests in a rolling window using a weighted average of the current and previous fixed windows.
- **Sliding window log**: currently implemented with the same weighted-estimate logic as the sliding window counter (see Notes below).

All algorithm state reads, calculations, and writes happen inside a single Lua script per call, so Redis executes the entire operation atomically — this is what makes the limiter correct across multiple application instances sharing the same Redis.

Idle keys expire automatically so Redis does not accumulate stale state.

## Resilience

`ResilientRateLimiter` wraps the active Redis-backed limiter in a Resilience4j circuit breaker (opens if ≥50% of the last 10 calls fail, stays open 5s, then probes with 3 half-open calls). When the breaker is open, requests are served by `InMemoryFallbackLimiter`, a per-instance token bucket — so limiting degrades from global to per-instance rather than failing outright.

## Request handling

A servlet filter (`RateLimitFilter`, an `OncePerRequestFilter`) intercepts requests under a configurable path prefix. On every response it sets:

- `X-RateLimit-Limit` — the configured capacity/limit
- `X-RateLimit-Remaining` — quota left

On a denied request it additionally returns HTTP 429 with a `Retry-After` header (seconds) and a small JSON error body. `/actuator` endpoints are always exempt so load balancer health checks never get rate limited.

## Tech stack

Java 21, Spring Boot, Spring Data Redis, Redis (Lua scripting), Resilience4j, Maven.

## Configuration

Defined in `application.yaml`:

```yaml
ratelimit:
  algorithm: token_bucket   # or sliding_window_log / sliding_window_counter
  capacity: 10               # max tokens (burst size, token bucket)
  refill-rate: 5.0           # tokens refilled per second (token bucket)
  limit: 10                  # max requests per window (sliding window algorithms)
  window-ms: 2000            # window size in milliseconds (sliding window algorithms)
  path-prefix: /api          # only paths under this prefix are rate limited
```

## Running locally

Requires JDK 21 and a Redis instance on `localhost:6379`.

Start Redis (via Docker):

```bash
docker run -d --name throttle-redis -p 6379:6379 redis:7-alpine
```

Run the application:

```bash
./mvnw spring-boot:run
```

## Trying it out

Endpoints under `/api` are rate limited; others are not.

```bash
# Limited: ~10 requests succeed, then 429s
for i in $(seq 1 13); do curl -s -o /dev/null -w "%{http_code} " "http://localhost:8080/api/demo"; done; echo

# Inspect headers
curl -s -i "http://localhost:8080/api/demo" | grep -iE "HTTP/|X-RateLimit|Retry-After"
```

## Notes

The "sliding window log" algorithm is currently implemented using the same weighted two-fixed-window approximation as the sliding window counter, rather than a true per-request timestamp log (which would use a Redis sorted set with `ZADD`/`ZREMRANGEBYSCORE`/`ZCARD`). This is accurate for moderate traffic but not exact. A true log-based implementation is on the roadmap.

## Roadmap

- Implement a true sliding window log using a Redis sorted set for exact (not approximated) counting
- Load test with k6 to validate throughput and tail latency (~10K req/sec, sub-40ms p99 target)
- Deploy on AWS behind an Application Load Balancer with Auto Scaling and ElastiCache for Redis
- Externalize richer observability (custom metrics, structured logging, dashboards)