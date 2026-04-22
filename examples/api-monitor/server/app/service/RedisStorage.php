<?php
/**
 * RedisStorage - 监控数据 Redis 存储
 *
 * 使用 Redis 存储，支持多 worker 进程共享数据。
 * 需要 PHP redis 扩展。
 *
 * Redis Key 设计：
 *   monitor:tree                          - Hash, 树形结构
 *   monitor:keys:{day}                    - Set, 当天所有接口 key
 *   monitor:stat:{granularity}:{key}:{period} - Hash, 聚合统计
 */

namespace app\service;

class RedisStorage implements StorageInterface
{
    private \Redis $redis;
    private string $prefix = 'monitor:';

    private int $minuteTTL = 86400;
    private int $hourTTL   = 259200;
    private int $dayTTL    = 2592000;
    private int $treeTTL   = 2592000;
    private int $keysTTL   = 2592000;

    public function __construct(string $host = '127.0.0.1', int $port = 6379, string $password = '', int $db = 0)
    {
        $this->redis = new \Redis();
        $this->redis->connect($host, $port, 3.0);
        if ($password !== '') {
            $this->redis->auth($password);
        }
        $this->redis->select($db);
    }

    public function record(array $data): void
    {
        $project  = $data['project']  ?? 'default';
        $class    = $data['class']    ?? 'Unknown';
        $method   = $data['method']   ?? 'unknown';
        $uri      = $data['uri']      ?? '/';
        $duration = (float)($data['duration'] ?? 0);
        $status   = (int)($data['status'] ?? 200);
        $success  = $status >= 200 && $status < 400;
        $time     = (int)($data['timestamp'] ?? time());

        $key = "{$project}|{$class}|{$method}|{$uri}";
        $minute = date('Y-m-d H:i', $time);
        $hour   = date('Y-m-d H', $time);
        $day    = date('Y-m-d', $time);

        $pipe = $this->redis->pipeline();
        $treeKey = $this->prefix . 'tree';
        $pipe->hSet($treeKey, "{$project}|{$class}|{$method}", $uri);
        $pipe->expire($treeKey, $this->treeTTL);
        $keysKey = $this->prefix . "keys:{$day}";
        $pipe->sAdd($keysKey, $key);
        $pipe->expire($keysKey, $this->keysTTL);
        $pipe->exec();

        $this->aggregate('minute', $key, $minute, $duration, $success, $this->minuteTTL);
        $this->aggregate('hour', $key, $hour, $duration, $success, $this->hourTTL);
        $this->aggregate('day', $key, $day, $duration, $success, $this->dayTTL);
    }

    private function aggregate(string $granularity, string $key, string $period, float $duration, bool $success, int $ttl): void
    {
        $redisKey = $this->prefix . "stat:{$granularity}:{$key}:{$period}";
        $pipe = $this->redis->pipeline();
        $pipe->hIncrBy($redisKey, 'count', 1);
        $pipe->hIncrByFloat($redisKey, 'total_time', $duration);
        $success ? $pipe->hIncrBy($redisKey, 'success', 1) : $pipe->hIncrBy($redisKey, 'fail', 1);
        $pipe->expire($redisKey, $ttl);
        $pipe->exec();
        $this->updateMinMax($redisKey, $duration, $ttl);
    }

    private function updateMinMax(string $redisKey, float $duration, int $ttl): void
    {
        $lua = <<<'LUA'
local key = KEYS[1]
local duration = tonumber(ARGV[1])
local ttl = tonumber(ARGV[2])
local max_time = tonumber(redis.call('HGET', key, 'max_time') or 0)
if duration > max_time then redis.call('HSET', key, 'max_time', duration) end
local min_time = tonumber(redis.call('HGET', key, 'min_time') or 0)
if min_time == 0 or duration < min_time then redis.call('HSET', key, 'min_time', duration) end
redis.call('EXPIRE', key, ttl)
return 1
LUA;
        $this->redis->eval($lua, [$redisKey, $duration, $ttl], 1);
    }

    private function getStat(string $granularity, string $key, string $period): ?array
    {
        $redisKey = $this->prefix . "stat:{$granularity}:{$key}:{$period}";
        $data = $this->redis->hGetAll($redisKey);
        if (empty($data)) return null;
        return [
            'count' => (int)($data['count'] ?? 0), 'success' => (int)($data['success'] ?? 0),
            'fail' => (int)($data['fail'] ?? 0), 'total_time' => (float)($data['total_time'] ?? 0),
            'max_time' => (float)($data['max_time'] ?? 0), 'min_time' => (float)($data['min_time'] ?? 0),
        ];
    }

    private function getDayKeys(string $date): array
    {
        return $this->redis->sMembers($this->prefix . "keys:{$date}") ?: [];
    }

    public function getTree(): array
    {
        $all = $this->redis->hGetAll($this->prefix . 'tree');
        $tree = [];
        foreach ($all as $field => $uri) {
            $parts = explode('|', $field);
            if (count($parts) === 3) $tree[$parts[0]][$parts[1]][$parts[2]] = $uri;
        }
        return $tree;
    }

    public function getDashboard(string $date = ''): array
    {
        if (!$date) $date = date('Y-m-d');
        $totalCount = 0; $totalSuccess = 0; $totalFail = 0; $totalTime = 0.0;
        foreach ($this->getDayKeys($date) as $key) {
            $s = $this->getStat('day', $key, $date);
            if ($s === null) continue;
            $totalCount += $s['count']; $totalSuccess += $s['success'];
            $totalFail += $s['fail']; $totalTime += $s['total_time'];
        }
        return [
            'date' => $date, 'total_count' => $totalCount,
            'success' => $totalSuccess, 'fail' => $totalFail,
            'success_rate' => $totalCount > 0 ? round($totalSuccess / $totalCount * 100, 2) : 0,
            'avg_time' => $totalCount > 0 ? round($totalTime / $totalCount, 2) : 0,
        ];
    }

    public function getDetail(string $project, string $class, string $method, string $date = '', string $granularity = 'minute'): array
    {
        if (!$date) $date = date('Y-m-d');
        $tree = $this->getTree();
        $result = [];
        foreach ($tree as $p => $classes) {
            if ($project && $p !== $project) continue;
            foreach ($classes as $c => $methods) {
                if ($class && $c !== $class) continue;
                foreach ($methods as $m => $uri) {
                    if ($method && $m !== $method) continue;
                    $key = "{$p}|{$c}|{$m}|{$uri}";
                    $filtered = $this->getPeriodsForDate($granularity, $key, $date);
                    ksort($filtered);
                    $result[] = ['project' => $p, 'class' => $c, 'method' => $m, 'uri' => $uri, 'periods' => $filtered];
                }
            }
        }
        return $result;
    }

    private function getPeriodsForDate(string $granularity, string $key, string $date): array
    {
        $filtered = [];
        $pattern = $this->prefix . "stat:{$granularity}:{$key}:{$date}*";
        $iterator = null;
        while (false !== ($keys = $this->redis->scan($iterator, $pattern, 200))) {
            foreach ($keys as $redisKey) {
                $data = $this->redis->hGetAll($redisKey);
                if (empty($data)) continue;
                $prefixLen = strlen($this->prefix . "stat:{$granularity}:{$key}:");
                $period = substr($redisKey, $prefixLen);
                $count = (int)($data['count'] ?? 0);
                $filtered[$period] = [
                    'count' => $count, 'success' => (int)($data['success'] ?? 0),
                    'fail' => (int)($data['fail'] ?? 0), 'total_time' => (float)($data['total_time'] ?? 0),
                    'max_time' => (float)($data['max_time'] ?? 0), 'min_time' => (float)($data['min_time'] ?? 0),
                    'avg_time' => $count > 0 ? round((float)$data['total_time'] / $count, 2) : 0,
                    'success_rate' => $count > 0 ? round((int)$data['success'] / $count * 100, 2) : 0,
                ];
            }
        }
        return $filtered;
    }

    public function getSlowRanking(string $date = '', int $limit = 20): array
    {
        if (!$date) $date = date('Y-m-d');
        $ranking = [];
        foreach ($this->getDayKeys($date) as $key) {
            $s = $this->getStat('day', $key, $date);
            if ($s === null) continue;
            $parts = explode('|', $key);
            $ranking[] = [
                'project' => $parts[0] ?? '', 'class' => $parts[1] ?? '',
                'method' => $parts[2] ?? '', 'uri' => $parts[3] ?? '',
                'avg_time' => $s['count'] > 0 ? round($s['total_time'] / $s['count'], 2) : 0,
                'max_time' => round($s['max_time'], 2), 'count' => $s['count'],
            ];
        }
        usort($ranking, fn($a, $b) => $b['avg_time'] <=> $a['avg_time']);
        return array_slice($ranking, 0, $limit);
    }

    public function getCountRanking(string $date = '', int $limit = 20): array
    {
        if (!$date) $date = date('Y-m-d');
        $ranking = [];
        foreach ($this->getDayKeys($date) as $key) {
            $s = $this->getStat('day', $key, $date);
            if ($s === null) continue;
            $parts = explode('|', $key);
            $ranking[] = [
                'project' => $parts[0] ?? '', 'class' => $parts[1] ?? '',
                'method' => $parts[2] ?? '', 'uri' => $parts[3] ?? '',
                'count' => $s['count'], 'success' => $s['success'], 'fail' => $s['fail'],
                'success_rate' => $s['count'] > 0 ? round($s['success'] / $s['count'] * 100, 2) : 0,
            ];
        }
        usort($ranking, fn($a, $b) => $b['count'] <=> $a['count']);
        return array_slice($ranking, 0, $limit);
    }

    public function getRealtime(): array
    {
        $now = time();
        $result = [];
        $tree = $this->getTree();
        $allKeys = [];
        foreach ($tree as $p => $classes) {
            foreach ($classes as $c => $methods) {
                foreach ($methods as $m => $uri) {
                    $allKeys[] = "{$p}|{$c}|{$m}|{$uri}";
                }
            }
        }
        for ($i = 9; $i >= 0; $i--) {
            $minute = date('Y-m-d H:i', $now - $i * 60);
            $count = 0; $success = 0; $fail = 0;
            foreach ($allKeys as $key) {
                $s = $this->getStat('minute', $key, $minute);
                if ($s === null) continue;
                $count += $s['count']; $success += $s['success']; $fail += $s['fail'];
            }
            $result[] = ['time' => $minute, 'count' => $count, 'success' => $success, 'fail' => $fail];
        }
        return $result;
    }

    public function search(string $keyword, string $date = ''): array
    {
        if (!$date) $date = date('Y-m-d');
        $keyword = strtolower($keyword);
        $results = [];
        foreach ($this->getDayKeys($date) as $key) {
            if (!str_contains(strtolower($key), $keyword)) continue;
            $s = $this->getStat('day', $key, $date);
            if ($s === null) continue;
            $parts = explode('|', $key);
            $results[] = [
                'project' => $parts[0] ?? '', 'class' => $parts[1] ?? '',
                'method' => $parts[2] ?? '', 'uri' => $parts[3] ?? '',
                'count' => $s['count'],
                'success_rate' => $s['count'] > 0 ? round($s['success'] / $s['count'] * 100, 2) : 0,
                'avg_time' => $s['count'] > 0 ? round($s['total_time'] / $s['count'], 2) : 0,
            ];
        }
        return $results;
    }

    public function hasData(string $date): bool
    {
        return !empty($this->getDayKeys($date));
    }
}
