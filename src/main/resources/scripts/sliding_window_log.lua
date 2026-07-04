-- KEYS[1] = key prefix (e.g. "rl:sliding_counter:<client>")
-- ARGV[1] = limit (max requests per window)
-- ARGV[2] = window_ms (window size in milliseconds)
-- ARGV[3] = now (current time, ms)
-- Returns: {allowed (1/0), remaining, retry_after_ms}
--
-- Approximates a rolling window using two fixed buckets (current + previous),
-- weighting the previous bucket by how much of it still overlaps the window.

local limit     = tonumber(ARGV[1])
local window_ms = tonumber(ARGV[2])
local now       = tonumber(ARGV[3])

-- Which fixed window are we in, and how far into it (0..1)?
local current_window = math.floor(now / window_ms)
local previous_window = current_window - 1
local elapsed_in_current = (now % window_ms) / window_ms

local current_key  = KEYS[1] .. ':' .. current_window
local previous_key = KEYS[1] .. ':' .. previous_window

local current_count  = tonumber(redis.call('GET', current_key))  or 0
local previous_count = tonumber(redis.call('GET', previous_key)) or 0

-- Weighted estimate: the previous window contributes the fraction of it
-- that still lies inside the rolling window.
local estimated = (previous_count * (1 - elapsed_in_current)) + current_count

local allowed = 0
local retry_after = 0

if estimated < limit then
  redis.call('INCR', current_key)
  -- Keep each fixed bucket for two windows so the previous one stays readable.
  redis.call('PEXPIRE', current_key, window_ms * 2)
  allowed = 1
  estimated = estimated + 1
else
  -- Approximate wait: time left in the current fixed window.
  retry_after = window_ms - (now % window_ms)
end

local remaining = math.floor(limit - estimated)
if remaining < 0 then remaining = 0 end

return { allowed, remaining, retry_after }