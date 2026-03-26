local time_deal_id = KEYS[1]
local request_stock = tonumber(ARGV[1])

local current_stock = tonumber(redis.call('GET', time_deal_id))
if not current_stock then
    return -2
end

if current_stock < request_stock then
    return -1
end

local remain_stock = redis.call('DECRBY', time_deal_id, request_stock)
return remain_stock