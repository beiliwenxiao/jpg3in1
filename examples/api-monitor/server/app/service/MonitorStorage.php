<?php
/**
 * MonitorStorage - 监控数据内存存储
 *
 * 使用内存存储监控数据，支持按分钟/小时/天聚合统计。
 * 生产环境可替换为 Redis 或数据库存储。
 */

namespace app\service;

class MonitorStorage
{
    /** @var self|null */
    private static ?self $instance = null;

    /** @var array 原始请求记录（保留最近1小时） */
    private array $records = [];

    /** @var array 按分钟聚合 [key => [minute => stats]] */
    private array $minuteStats = [];

    /** @var array 按小时聚合 */
    private array $hourStats = [];

    /** @var array 按天聚合 */
    private array $dayStats = [];

    /** @var array 项目→类→方法 树形结构 */
    private array $tree = [];

    /** @var int 最大原始记录数 */
    private int $maxRecords = 100000;

    public static function getInstance(): self
    {
        if (self::$instance === null) {
            self::$instance = new self();
        }
        return self::$instance;
    }

    /**
     * 写入一条监控记录
     */
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

        // 保存原始记录
        $record = [
            'project'   => $project,
            'class'     => $class,
            'method'    => $method,
            'uri'       => $uri,
            'duration'  => $duration,
            'status'    => $status,
            'success'   => $success,
            'timestamp' => $time,
            'key'       => $key,
        ];
        $this->records[] = $record;

        // 限制记录数
        if (count($this->records) > $this->maxRecords) {
            $this->records = array_slice($this->records, -($this->maxRecords / 2));
        }

        // 更新树形结构
        $this->tree[$project][$class][$method] = $uri;

        // 聚合统计
        $minute = date('Y-m-d H:i', $time);
        $hour   = date('Y-m-d H', $time);
        $day    = date('Y-m-d', $time);

        $this->aggregate($this->minuteStats, $key, $minute, $duration, $success);
        $this->aggregate($this->hourStats, $key, $hour, $duration, $success);
        $this->aggregate($this->dayStats, $key, $day, $duration, $success);
    }

    private function aggregate(array &$store, string $key, string $period, float $duration, bool $success): void
    {
        if (!isset($store[$key][$period])) {
            $store[$key][$period] = [
                'count'        => 0,
                'success'      => 0,
                'fail'         => 0,
                'total_time'   => 0.0,
                'max_time'     => 0.0,
                'min_time'     => PHP_FLOAT_MAX,
            ];
        }
        $s = &$store[$key][$period];
        $s['count']++;
        $s['total_time'] += $duration;
        if ($duration > $s['max_time']) $s['max_time'] = $duration;
        if ($duration < $s['min_time']) $s['min_time'] = $duration;
        if ($success) {
            $s['success']++;
        } else {
            $s['fail']++;
        }
    }

    /**
     * 获取树形菜单数据
     */
    public function getTree(): array
    {
        return $this->tree;
    }

    /**
     * 获取仪表盘概览
     */
    public function getDashboard(string $date = ''): array
    {
        if (!$date) $date = date('Y-m-d');

        $totalCount   = 0;
        $totalSuccess = 0;
        $totalFail    = 0;
        $totalTime    = 0.0;

        foreach ($this->dayStats as $key => $periods) {
            if (isset($periods[$date])) {
                $s = $periods[$date];
                $totalCount   += $s['count'];
                $totalSuccess += $s['success'];
                $totalFail    += $s['fail'];
                $totalTime    += $s['total_time'];
            }
        }

        return [
            'date'         => $date,
            'total_count'  => $totalCount,
            'success'      => $totalSuccess,
            'fail'         => $totalFail,
            'success_rate' => $totalCount > 0 ? round($totalSuccess / $totalCount * 100, 2) : 0,
            'avg_time'     => $totalCount > 0 ? round($totalTime / $totalCount, 2) : 0,
        ];
    }

    /**
     * 获取接口详情统计
     */
    public function getDetail(string $project, string $class, string $method, string $date = '', string $granularity = 'minute'): array
    {
        if (!$date) $date = date('Y-m-d');

        $result = [];
        // 查找匹配的key
        foreach ($this->tree as $p => $classes) {
            if ($project && $p !== $project) continue;
            foreach ($classes as $c => $methods) {
                if ($class && $c !== $class) continue;
                foreach ($methods as $m => $uri) {
                    if ($method && $m !== $method) continue;
                    $key = "{$p}|{$c}|{$m}|{$uri}";

                    $store = match ($granularity) {
                        'hour' => $this->hourStats,
                        'day'  => $this->dayStats,
                        default => $this->minuteStats,
                    };

                    $periods = $store[$key] ?? [];
                    $filtered = [];
                    foreach ($periods as $period => $stats) {
                        if (str_starts_with($period, $date)) {
                            $stats['avg_time'] = $stats['count'] > 0
                                ? round($stats['total_time'] / $stats['count'], 2) : 0;
                            $stats['success_rate'] = $stats['count'] > 0
                                ? round($stats['success'] / $stats['count'] * 100, 2) : 0;
                            if ($stats['min_time'] === PHP_FLOAT_MAX) $stats['min_time'] = 0;
                            $filtered[$period] = $stats;
                        }
                    }
                    ksort($filtered);

                    $result[] = [
                        'project' => $p,
                        'class'   => $c,
                        'method'  => $m,
                        'uri'     => $uri,
                        'periods' => $filtered,
                    ];
                }
            }
        }
        return $result;
    }

    /**
     * 慢速接口排行
     */
    public function getSlowRanking(string $date = '', int $limit = 20): array
    {
        if (!$date) $date = date('Y-m-d');

        $ranking = [];
        foreach ($this->dayStats as $key => $periods) {
            if (!isset($periods[$date])) continue;
            $s = $periods[$date];
            $parts = explode('|', $key);
            $ranking[] = [
                'project'  => $parts[0] ?? '',
                'class'    => $parts[1] ?? '',
                'method'   => $parts[2] ?? '',
                'uri'      => $parts[3] ?? '',
                'avg_time' => $s['count'] > 0 ? round($s['total_time'] / $s['count'], 2) : 0,
                'max_time' => round($s['max_time'], 2),
                'count'    => $s['count'],
            ];
        }

        usort($ranking, fn($a, $b) => $b['avg_time'] <=> $a['avg_time']);
        return array_slice($ranking, 0, $limit);
    }

    /**
     * 访问次数排行
     */
    public function getCountRanking(string $date = '', int $limit = 20): array
    {
        if (!$date) $date = date('Y-m-d');

        $ranking = [];
        foreach ($this->dayStats as $key => $periods) {
            if (!isset($periods[$date])) continue;
            $s = $periods[$date];
            $parts = explode('|', $key);
            $ranking[] = [
                'project'      => $parts[0] ?? '',
                'class'        => $parts[1] ?? '',
                'method'       => $parts[2] ?? '',
                'uri'          => $parts[3] ?? '',
                'count'        => $s['count'],
                'success'      => $s['success'],
                'fail'         => $s['fail'],
                'success_rate' => $s['count'] > 0 ? round($s['success'] / $s['count'] * 100, 2) : 0,
            ];
        }

        usort($ranking, fn($a, $b) => $b['count'] <=> $a['count']);
        return array_slice($ranking, 0, $limit);
    }

    /**
     * 实时访问量（最近10分钟，按分钟）
     */
    public function getRealtime(): array
    {
        $now = time();
        $result = [];

        for ($i = 9; $i >= 0; $i--) {
            $minute = date('Y-m-d H:i', $now - $i * 60);
            $count = 0;
            $success = 0;
            $fail = 0;
            foreach ($this->minuteStats as $key => $periods) {
                if (isset($periods[$minute])) {
                    $count   += $periods[$minute]['count'];
                    $success += $periods[$minute]['success'];
                    $fail    += $periods[$minute]['fail'];
                }
            }
            $result[] = [
                'time'    => $minute,
                'count'   => $count,
                'success' => $success,
                'fail'    => $fail,
            ];
        }
        return $result;
    }

    /**
     * 搜索接口
     */
    public function search(string $keyword, string $date = ''): array
    {
        if (!$date) $date = date('Y-m-d');
        $keyword = strtolower($keyword);

        $results = [];
        foreach ($this->dayStats as $key => $periods) {
            if (!str_contains(strtolower($key), $keyword)) continue;
            if (!isset($periods[$date])) continue;
            $s = $periods[$date];
            $parts = explode('|', $key);
            $results[] = [
                'project'      => $parts[0] ?? '',
                'class'        => $parts[1] ?? '',
                'method'       => $parts[2] ?? '',
                'uri'          => $parts[3] ?? '',
                'count'        => $s['count'],
                'success_rate' => $s['count'] > 0 ? round($s['success'] / $s['count'] * 100, 2) : 0,
                'avg_time'     => $s['count'] > 0 ? round($s['total_time'] / $s['count'], 2) : 0,
            ];
        }
        return $results;
    }
}
