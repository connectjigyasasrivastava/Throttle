-- KEYS[1] = key (e.g. "rl:sliding_log:<client>")
-- ARGV[1] = limit (max requests per window)
-- ARGV[2] = window_ms (window size in milliseconds)
-- ARGV[3] = now (current time, ms)
-- Returns: {allowed (1/0), remaining, retry_after_ms}

local limit     = tonumber(ARGV[1])
local window_ms = tonumber(ARGV[2])
local now       = tonumber(ARGV[3])

local window_start = now - window_ms

-- Drop entries older than the window.
redis.call('ZREMRANGEBYSCORE', KEYS[1], 0, window_start)

-- Count requests currently in the window.
local count = redis.call('ZCARD', KEYS[1])

local allowed = 0
local retry_after = 0

if count < limit then
  -- Record this request; member must be unique, so use now + a random suffix.
  redis.call('ZADD', KEYS[1], now, now .. '-' .. math.random(1, 1000000))
  allowed = 1
  count = count + 1
else
  -- Retry when the oldest request in the window falls out.
  local oldest = redis.call('ZRANGE', KEYS[1], 0, 0, 'WITHSCORES')
  if oldest[2] then
    retry_after = (tonumber(oldest[2]) + window_ms) - now
    if retry_after < 0 then retry_after = 0 end
  end
end

-- Expire the key one window after now so idle clients clean up.
redis.call('PEXPIRE', KEYS[1], window_ms)

local remaining = limit - count
if remaining < 0 then remaining = 0 end

return { allowed, remaining, retry_after }