local capacity   = tonumber(ARGV[1])
local refill_rate = tonumber(ARGV[2])
local now        = tonumber(ARGV[3])
local requested  = tonumber(ARGV[4])

-- Load current state; default to a full bucket on first sight.
local state = redis.call('HMGET', KEYS[1], 'tokens', 'last_refill')
local tokens = tonumber(state[1])
local last_refill = tonumber(state[2])

if tokens == nil then
  tokens = capacity
  last_refill = now
end

-- Refill based on elapsed time since last_refill.
local elapsed = math.max(0, now - last_refill)
local refilled = (elapsed / 1000.0) * refill_rate
tokens = math.min(capacity, tokens + refilled)
last_refill = now

local allowed = 0
local retry_after = 0

if tokens >= requested then
  tokens = tokens - requested
  allowed = 1
else
  -- How long until enough tokens accumulate for this request.
  local deficit = requested - tokens
  retry_after = math.ceil((deficit / refill_rate) * 1000)
end

-- Persist new state. Expire the key after it would naturally refill to full,
-- so idle buckets don't linger in Redis forever.
redis.call('HSET', KEYS[1], 'tokens', tokens, 'last_refill', last_refill)
local ttl = math.ceil((capacity / refill_rate) * 2)
redis.call('EXPIRE', KEYS[1], ttl)

return { allowed, math.floor(tokens), retry_after }