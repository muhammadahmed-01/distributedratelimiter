local key = KEYS[1]
local limit = tonumber(ARGV[1])
local windowSeconds = tonumber(ARGV[2])
local now = tonumber(ARGV[3])

-- Remove all requests older than the window
redis.call('ZREMRANGEBYSCORE', key, 0, now - windowSeconds)

-- Check if the current count is within the limit
local count = redis.call('ZCARD', key)

if count < limit then
    -- If within the limit, add the current timestamp
    -- Add a unique member for this timestamp
    redis.call('ZADD', key, now, now .. ":" .. redis.call('INCR', key .. ":seq"))

    -- Set TTL for both the main key and counter
    redis.call('EXPIRE', key, windowSeconds + 1)
    redis.call('EXPIRE', key .. ":seq", windowSeconds + 1)

    -- Return the new count
    return count + 1
else
    -- DENY: If the limit is reached, return the count (no increment)
    return -1
end