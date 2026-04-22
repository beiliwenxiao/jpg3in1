<?php
/**
 * MonitorLogger - 监控数据日志持久化
 *
 * 定时将 Redis 中的聚合统计数据导出为 JSON 日志文件，长期保存。
 * 日志按天存储，每个文件包含当天所有接口的聚合统计。
 *
 * 日志目录结构：
 *   runtime/logs/monitor/
 *     2026-04-22.json      - 天级汇总
 *     2026-04-22_hour.json - 小时级明细
 */

namespace app\service;

use Workerman\Timer;

class MonitorLogger
{
    private MonitorStorage $storage;
    private string $logDir;

    /** @var int 导出间隔（秒），默认 5 分钟 */
    private int $interval;

    public function __construct(int $interval = 300)
    {
        $this->storage  = MonitorStorage::getInstance();
        $this->logDir   = runtime_path('logs/monitor');
        $this->interval = $interval;

        if (!is_dir($this->logDir)) {
            mkdir($this->logDir, 0777, true);
        }
    }

    /**
     * 启动定时导出（在 worker 中调用）
     */
    public function start(): void
    {
        // 启动后立即导出一次
        $this->export();

        // 定时导出
        Timer::add($this->interval, function () {
            $this->export();
        });

        echo "[监控] 日志持久化启动，间隔 {$this->interval} 秒，目录: {$this->logDir}\n";
    }

    /**
     * 导出当天数据到日志文件
     */
    public function export(): void
    {
        $date = date('Y-m-d');

        try {
            $this->exportDaySummary($date);
            $this->exportHourDetail($date);
        } catch (\Throwable $e) {
            echo "[监控日志] 导出失败: {$e->getMessage()}\n";
        }
    }

    /**
     * 导出天级汇总
     */
    private function exportDaySummary(string $date): void
    {
        $dashboard = $this->storage->getDashboard($date);
        $slow      = $this->storage->getSlowRanking($date, 50);
        $top       = $this->storage->getCountRanking($date, 50);
        $tree      = $this->storage->getTree();

        $data = [
            'date'       => $date,
            'exported_at' => date('Y-m-d H:i:s'),
            'dashboard'  => $dashboard,
            'slow_ranking'  => $slow,
            'count_ranking' => $top,
            'tree'       => $tree,
        ];

        $file = $this->logDir . "/{$date}.json";
        file_put_contents($file, json_encode($data, JSON_UNESCAPED_UNICODE | JSON_PRETTY_PRINT));
    }

    /**
     * 导出小时级明细
     */
    private function exportHourDetail(string $date): void
    {
        $tree = $this->storage->getTree();
        $details = [];

        foreach ($tree as $project => $classes) {
            foreach ($classes as $class => $methods) {
                foreach ($methods as $method => $uri) {
                    $detail = $this->storage->getDetail($project, $class, $method, $date, 'hour');
                    if (!empty($detail)) {
                        $details[] = $detail[0];
                    }
                }
            }
        }

        $data = [
            'date'        => $date,
            'exported_at' => date('Y-m-d H:i:s'),
            'details'     => $details,
        ];

        $file = $this->logDir . "/{$date}_hour.json";
        file_put_contents($file, json_encode($data, JSON_UNESCAPED_UNICODE | JSON_PRETTY_PRINT));
    }

    /**
     * 读取历史日志（天级汇总）
     */
    public static function readDayLog(string $logDir, string $date): ?array
    {
        $file = $logDir . "/{$date}.json";
        if (!is_file($file)) {
            return null;
        }
        $content = file_get_contents($file);
        return json_decode($content, true) ?: null;
    }

    /**
     * 读取历史日志（小时级明细）
     */
    public static function readHourLog(string $logDir, string $date): ?array
    {
        $file = $logDir . "/{$date}_hour.json";
        if (!is_file($file)) {
            return null;
        }
        $content = file_get_contents($file);
        return json_decode($content, true) ?: null;
    }

    /**
     * 获取可用的日志日期列表
     */
    public static function getAvailableDates(string $logDir): array
    {
        if (!is_dir($logDir)) {
            return [];
        }
        $dates = [];
        foreach (scandir($logDir) as $file) {
            // 只匹配 YYYY-MM-DD.json（不含 _hour）
            if (preg_match('/^(\d{4}-\d{2}-\d{2})\.json$/', $file, $m)) {
                $dates[] = $m[1];
            }
        }
        sort($dates);
        return $dates;
    }
}
