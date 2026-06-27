# Throttle

A distributed rate limiter built in Java 21 and Spring Boot, backed by Redis for consistent rate limiting across horizontally scaled instances.

Rate limiting decisions are executed as atomic Redis Lua scripts, so concurrent requests hitting multiple application instances share a single source of truth and cannot race past the limit.

## Status

This project is under active development. The table below reflects what is implemented today versus what is planned.

| Area | Status |
|------|--------|
| Token bucket algorithm (atomic Lua) | Done |
| Redis-backed shared state | Done |
| Per-client rate limit filter (429 + standard headers) | Done |
| Config-driven path matching | Done |
| Sliding window log algorithm | Planned |
| Sliding window counter algorithm | Planned |
| Resilience4j circuit breaking + local fallback | Planned |
| Load testing (k6) | Planned |
| AWS deployment (ALB, Auto Scaling, ElastiCache) | Planned |

## How it works

Each client (resolved by IP, honoring `X-Forwarded-For` when behind a proxy) gets a token bucket stored in Redis. A bucket holds up to `capacity` tokens and refills at `refillRate` tokens per second. Every request consumes one token; an empty bucket yields an HTTP 429.

Refill is computed lazily: rather than running a background timer, the Lua script calculates the tokens accrued since the bucket was last touched, based on elapsed time. The entire read-compute-write happens inside a single Lua script so Redis executes it atomically, which is what makes the limiter correct across multiple application instances sharing the same Redis.

Idle buckets expire automatically so Redis does not accumulate stale keys.

## Request handling

A servlet filter (`OncePerRequestFilter`) intercepts requests under a configurable path prefix. On every response it sets:

- `X-RateLimit-Limit` — the bucket capacity
- `X-RateLimit-Remaining` — tokens left

On a denied request it additionally returns HTTP 429 with a `Retry-After` header (seconds) and a small JSON error body.

## Tech stack

Java 21, Spring Boot, Spring Data Redis, Redis (Lua scripting), Maven.

## Configuration

Defined in `application.yaml`:

```yaml
ratelimit:
  capacity: 10        # max tokens (burst size)
  refill-rate: 5.0    # tokens refilled per second (sustained rate)
  path-prefix: /api   # only paths under this prefix are rate limited
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

## Roadmap

- Add sliding window log and sliding window counter algorithms, selectable by configuration
- Add Resilience4j circuit breaking with a local in-memory fallback limiter for graceful degradation when Redis is unreachable
- Externalize observability (Actuator metrics, structured logging)
- Load test with k6 to validate throughput and tail latency
- Deploy on AWS behind an Application Load Balancer with Auto Scaling and ElastiCache for Redis