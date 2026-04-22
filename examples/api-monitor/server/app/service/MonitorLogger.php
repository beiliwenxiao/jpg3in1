<?php
/**
 * MonitorLogger - 监控数据日志持久化
 *
 * 使用 sessionId 机制避免重启后数据重复或丢失：
 *   - 每次进程启动生成唯一 sessionId
 *   - 日志文件按 session 分槽存储，每个 session 的数据独立
 *   - 导出时只更新当前 session 的槽位，不影响其他 session
 *   - 查询时将所有 session 的数据累加返回
 *
 * 日志文件结构：
 *   {date}.json - 天级汇总
 *     { sessions: { "s_xxx": {dashboard, ranking, tree}, "s_yyy": {...} } }
 *   {date}_minute.json - 分钟级明细
 *     { sessions: { "s_xxx": {details: [...]}, "s_yyy": {...} } }
 */

namespace app\service;

use Workerman\Timer;

class MonitorLogger
{
    private MonitorStorage $storage;
    private string $logDir;
    private int $interval;
    private string $sessionId;

    public function __construct(int $interval = 300)
    {
        $this->storage   = MonitorStorage::getInstance();
        $this->logDir    = runtime_path('logs/monitor');
        $this->interval  = $interval;
        $this->sessionId = $this->storage->getSessionId();

        if (!is_dir($this->logDir)) {
            mkdir($this->logDir, 0777, true);
        }
    }

    public function start(): void
    {
        Timer::add($this->interval, function () {
            $this->export();
        });
        echo "[监控] 日志持久化启动 (session: {$this->sessionId})，间隔 {$this->interval} 秒\n";
    }

    public function export(): void
    {
        $date = date('Y-m-d');
        try {
            $this->exportDaySummary($date);
            $this->exportMinuteDetail($date);
        } catch (\Throwable $e) {
            echo "[监控日志] 导出失败: {$e->getMessage()}\n";
        }
    }

    // ========== 导出（写入当前 session 槽位） ==========

    private function exportDaySummary(string $date): void
    {
        $file = $this->logDir . "/{$date}.json";
        $log = is_file($file) ? (json_decode(file_get_contents($file), true) ?: []) : [];

        // 迁移旧格式（无 sessions 字段）到新格式
        if (!isset($log['sessions']) && isset($log['dashboard'])) {
            $log = ['sessions' => ['legacy' => $log]];
        }
        if (!isset($log['sessions'])) {
            $log['sessions'] = [];
        }

        // 写入当前 session 的数据
        $log['sessions'][$this->sessionId] = [
            'exported_at'   => date('Y-m-d H:i:s'),
            'dashboard'     => $this->storage->getDashboard($date),
            'slow_ranking'  => $this->storage->getSlowRanking($date, 50),
            'count_ranking' => $this->storage->getCountRanking($date, 50),
            'tree'          => $this->storage->getTree(),
        ];

        file_put_contents($file, json_encode($log, JSON_UNESCAPED_UNICODE | JSON_PRETTY_PRINT));
    }

    private function exportMinuteDetail(string $date): void
    {
        $file = $this->logDir . "/{$date}_minute.json";
        $log = is_file($file) ? (json_decode(file_get_contents($file), true) ?: []) : [];

        // 迁移旧格式
        if (!isset($log['sessions']) && isset($log['details'])) {
            $log = ['sessions' => ['legacy' => $log]];
        }
        if (!isset($log['sessions'])) {
            $log['sessions'] = [];
        }

        $tree = $this->storage->getTree();
        $details = [];

        foreach ($tree as $project => $classes) {
            foreach ($classes as $class => $methods) {
                foreach ($methods as $method => $uri) {
                    $detail = $this->storage->getDetail($project, $class, $method, $date, 'minute');
                    if (empty($detail) || empty($detail[0]['periods'])) continue;

                    $item = $detail[0];
                    $records = [];
                    foreach (array_keys($item['periods']) as $minute) {
                        $recs = $this->storage->getRecords($project, $class, $method, $minute, 200);
                        if (!empty($recs)) {
                            $records[$minute] = $recs;
                        }
                    }
                    $item['records'] = $records;
                    $details[] = $item;
                }
            }
        }

        $log['sessions'][$this->sessionId] = [
            'exported_at' => date('Y-m-d H:i:s'),
            'details'     => $details,
        ];

        file_put_contents($file, json_encode($log, JSON_UNESCAPED_UNICODE | JSON_PRETTY_PRINT));
    }

    // ========== 读取（合并所有 session） ==========

    public static function readDayLog(string $logDir, string $date, string $excludeSession = ''): ?array
    {
        $file = $logDir . "/{$date}.json";
        if (!is_file($file)) return null;
        $log = json_decode(file_get_contents($file), true);
        if (!$log) return null;

        // 旧格式兼容
        if (isset($log['dashboard'])) return $log;

        // 新格式：合并所有 session（排除指定的）
        $sessions = $log['sessions'] ?? [];
        if ($excludeSession !== '') {
            unset($sessions[$excludeSession]);
        }
        return self::mergeDaySessions($sessions);
    }

    public static function readHourLog(string $logDir, string $date, string $excludeSession = ''): ?array
    {
        $file = $logDir . "/{$date}_minute.json";
        if (!is_file($file)) $file = $logDir . "/{$date}_hour.json";
        if (!is_file($file)) return null;
        $log = json_decode(file_get_contents($file), true);
        if (!$log) return null;

        // 旧格式兼容
        if (isset($log['details'])) return $log;

        // 新格式：合并所有 session（排除指定的）
        $sessions = $log['sessions'] ?? [];
        if ($excludeSession !== '') {
            unset($sessions[$excludeSession]);
        }
        return self::mergeMinuteSessions($sessions);
    }

    /**
     * 合并所有 session 的天级汇总
     */
    private static function mergeDaySessions(array $sessions): array
    {
        $totalCount = 0; $totalSuccess = 0; $totalFail = 0; $totalTime = 0.0;
        $tree = [];
        $slowMap = []; $countMap = [];

        foreach ($sessions as $session) {
            $d = $session['dashboard'] ?? [];
            $c = $d['total_count'] ?? 0;
            $totalCount += $c;
            $totalSuccess += $d['success'] ?? 0;
            $totalFail += $d['fail'] ?? 0;
            $totalTime += ($d['avg_time'] ?? 0) * $c;

            // 合并 tree
            foreach ($session['tree'] ?? [] as $proj => $classes) {
                foreach ($classes as $cls => $methods) {
                    foreach ($methods as $mtd => $uri) {
                        $tree[$proj][$cls][$mtd] = $uri;
                    }
                }
            }

            // 合并排行榜
            self::accumulateRanking($slowMap, $session['slow_ranking'] ?? []);
            self::accumulateRanking($countMap, $session['count_ranking'] ?? []);
        }

        $slow = array_values($slowMap);
        usort($slow, fn($a, $b) => ($b['avg_time'] ?? 0) <=> ($a['avg_time'] ?? 0));

        $count = array_values($countMap);
        usort($count, fn($a, $b) => ($b['count'] ?? 0) <=> ($a['count'] ?? 0));

        return [
            'dashboard' => [
                'date'         => '',
                'total_count'  => $totalCount,
                'success'      => $totalSuccess,
                'fail'         => $totalFail,
                'success_rate' => $totalCount > 0 ? round($totalSuccess / $totalCount * 100, 2) : 0,
                'avg_time'     => $totalCount > 0 ? round($totalTime / $totalCount, 2) : 0,
            ],
            'slow_ranking'  => array_slice($slow, 0, 50),
            'count_ranking' => array_slice($count, 0, 50),
            'tree'          => $tree,
        ];
    }

    private static function accumulateRanking(array &$map, array $ranking): void
    {
        foreach ($ranking as $item) {
            $key = ($item['project'] ?? '') . '|' . ($item['class'] ?? '') . '|' . ($item['method'] ?? '');
            if (isset($map[$key])) {
                $o = $map[$key];
                $tc = ($o['count'] ?? 0) + ($item['count'] ?? 0);
                $map[$key]['count'] = $tc;
                if (isset($item['success'])) {
                    $map[$key]['success'] = ($o['success'] ?? 0) + ($item['success'] ?? 0);
                    $map[$key]['fail'] = ($o['fail'] ?? 0) + ($item['fail'] ?? 0);
                    $map[$key]['success_rate'] = $tc > 0 ? round($map[$key]['success'] / $tc * 100, 2) : 0;
                }
                if (isset($item['avg_time'])) {
                    $ot = ($o['avg_time'] ?? 0) * ($o['count'] ?? 0);
                    $nt = ($item['avg_time'] ?? 0) * ($item['count'] ?? 0);
                    $map[$key]['avg_time'] = $tc > 0 ? round(($ot + $nt) / $tc, 2) : 0;
                }
                if (isset($item['max_time'])) {
                    $map[$key]['max_time'] = max($o['max_time'] ?? 0, $item['max_time'] ?? 0);
                }
            } else {
                $map[$key] = $item;
            }
        }
    }

    /**
     * 合并所有 session 的分钟级明细
     */
    private static function mergeMinuteSessions(array $sessions): array
    {
        // key => [periods => [...], records => [...], project, class, method, uri]
        $detailMap = [];

        foreach ($sessions as $session) {
            foreach ($session['details'] ?? [] as $detail) {
                $key = ($detail['project'] ?? '') . '|' . ($detail['class'] ?? '') . '|' . ($detail['method'] ?? '');

                if (!isset($detailMap[$key])) {
                    $detailMap[$key] = $detail;
                    continue;
                }

                $existing = &$detailMap[$key];

                // 合并 periods
                foreach ($detail['periods'] ?? [] as $minute => $stats) {
                    if (!isset($existing['periods'][$minute])) {
                        $existing['periods'][$minute] = $stats;
                    } else {
                        $old = $existing['periods'][$minute];
                        $tc = ($old['count'] ?? 0) + ($stats['count'] ?? 0);
                        $existing['periods'][$minute] = [
                            'count'        => $tc,
                            'success'      => ($old['success'] ?? 0) + ($stats['success'] ?? 0),
                            'fail'         => ($old['fail'] ?? 0) + ($stats['fail'] ?? 0),
                            'total_time'   => ($old['total_time'] ?? 0) + ($stats['total_time'] ?? 0),
                            'max_time'     => max($old['max_time'] ?? 0, $stats['max_time'] ?? 0),
                            'min_time'     => min($old['min_time'] ?? PHP_FLOAT_MAX, $stats['min_time'] ?? PHP_FLOAT_MAX),
                            'avg_time'     => $tc > 0 ? round((($old['total_time'] ?? 0) + ($stats['total_time'] ?? 0)) / $tc, 2) : 0,
                            'success_rate' => $tc > 0 ? round((($old['success'] ?? 0) + ($stats['success'] ?? 0)) / $tc * 100, 2) : 0,
                        ];
                    }
                }

                // 合并 records（按 time 去重）
                foreach ($detail['records'] ?? [] as $minute => $recs) {
                    if (!isset($existing['records'][$minute])) {
                        $existing['records'][$minute] = $recs;
                    } else {
                        $existingTimes = array_column($existing['records'][$minute], 'time');
                        foreach ($recs as $rec) {
                            if (!in_array($rec['time'] ?? '', $existingTimes)) {
                                $existing['records'][$minute][] = $rec;
                            }
                        }
                    }
                }
                unset($existing);
            }
        }

        // 排序 periods
        foreach ($detailMap as &$detail) {
            if (isset($detail['periods'])) {
                ksort($detail['periods']);
            }
        }
        unset($detail);

        return ['details' => array_values($detailMap)];
    }

    public static function getAvailableDates(string $logDir): array
    {
        if (!is_dir($logDir)) return [];
        $dates = [];
        foreach (scandir($logDir) as $file) {
            if (preg_match('/^(\d{4}-\d{2}-\d{2})\.json$/', $file, $m)) {
                $dates[] = $m[1];
            }
        }
        sort($dates);
        return $dates;
    }

    public static function readRecordsFromLog(string $logDir, string $date, string $project, string $class, string $method, string $minute, string $excludeSession = ''): array
    {
        $log = self::readHourLog($logDir, $date, $excludeSession);
        if ($log === null) return [];
        foreach ($log['details'] ?? [] as $detail) {
            if (($detail['project'] ?? '') === $project
                && ($detail['class'] ?? '') === $class
                && ($detail['method'] ?? '') === $method) {
                return $detail['records'][$minute] ?? [];
            }
        }
        return [];
    }
}
