<?php
/**
 * MonitorStorage - 监控数据存储代理
 *
 * 根据配置自动选择存储后端（memory 或 redis）。
 * - memory: 内存存储，无外部依赖，自动降为单 worker
 * - redis:  Redis 存储，支持多 worker 共享数据
 *
 * 配置文件: config/monitor.php
 *   'driver' => 'memory' | 'redis'
 *
 * 同时支持从日志文件读取历史数据（无论哪种驱动）。
 */

namespace app\service;

class MonitorStorage implements StorageInterface
{
    private static ?self $instance = null;
    private StorageInterface $backend;
    private string $driver;

    /** 当前运行周期的唯一标识，用于日志合并时区分不同运行周期 */
    private string $sessionId;

    private function __construct(StorageInterface $backend, string $driver)
    {
        $this->backend = $backend;
        $this->driver  = $driver;
        $this->sessionId = 's_' . time() . '_' . substr(md5(uniqid((string)mt_rand(), true)), 0, 6);
    }

    /** 获取当前 sessionId */
    public function getSessionId(): string
    {
        return $this->sessionId;
    }

    public static function getInstance(): self
    {
        if (self::$instance === null) {
            $config = self::loadMonitorConfig();
            $driver = $config['driver'] ?? 'memory';

            if ($driver === 'redis' && extension_loaded('redis')) {
                $redisConfig = self::loadRedisConfig();
                try {
                    $backend = new RedisStorage(
                        $redisConfig['host'] ?? '127.0.0.1',
                        $redisConfig['port'] ?? 6379,
                        $redisConfig['password'] ?? '',
                        $redisConfig['db'] ?? 0
                    );
                    echo "[监控] 使用 Redis 存储\n";
                } catch (\Throwable $e) {
                    echo "[监控] Redis 连接失败({$e->getMessage()})，回退到内存存储\n";
                    $backend = new MemoryStorage();
                    $driver = 'memory';
                }
            } else {
                if ($driver === 'redis' && !extension_loaded('redis')) {
                    echo "[监控] 未安装 redis 扩展，回退到内存存储\n";
                } else {
                    echo "[监控] 使用内存存储\n";
                }
                $backend = new MemoryStorage();
                $driver = 'memory';
            }

            self::$instance = new self($backend, $driver);
        }
        return self::$instance;
    }

    /** 获取当前驱动类型 */
    public function getDriver(): string
    {
        return $this->driver;
    }

    /** 是否为内存模式（需要单 worker） */
    public function isMemoryMode(): bool
    {
        return $this->driver === 'memory';
    }

    private static function loadMonitorConfig(): array
    {
        $configFile = config_path('monitor.php');
        if (is_file($configFile)) {
            return require $configFile;
        }
        return ['driver' => 'memory'];
    }

    private static function loadRedisConfig(): array
    {
        $configFile = config_path('redis.php');
        if (is_file($configFile)) {
            return require $configFile;
        }
        return [];
    }

    // ========== StorageInterface 代理 ==========

    public function record(array $data): void { $this->backend->record($data); }
    public function getTree(): array { return $this->backend->getTree(); }
    public function getDashboard(string $date = ''): array { return $this->backend->getDashboard($date); }
    public function getDetail(string $project, string $class, string $method, string $date = '', string $granularity = 'minute'): array { return $this->backend->getDetail($project, $class, $method, $date, $granularity); }
    public function getSlowRanking(string $date = '', int $limit = 20): array { return $this->backend->getSlowRanking($date, $limit); }
    public function getCountRanking(string $date = '', int $limit = 20): array { return $this->backend->getCountRanking($date, $limit); }
    public function getRealtime(): array { return $this->backend->getRealtime(); }
    public function search(string $keyword, string $date = ''): array { return $this->backend->search($keyword, $date); }
    public function getDayTrend(string $date = ''): array {
        if (!$date) $date = date('Y-m-d');
        $live = $this->backend->getDayTrend($date);

        $log = MonitorLogger::readHourLog($this->getLogDir(), $date, $this->sessionId);
        if ($log === null) return $live;

        // 构建日志中的分钟汇总（已是所有历史 session 的累加）
        $logMinutes = [];
        foreach ($log['details'] ?? [] as $detail) {
            foreach ($detail['periods'] ?? [] as $period => $stats) {
                if (!isset($logMinutes[$period])) {
                    $logMinutes[$period] = ['count' => 0, 'success' => 0, 'fail' => 0];
                }
                $logMinutes[$period]['count'] += $stats['count'] ?? 0;
                $logMinutes[$period]['success'] += $stats['success'] ?? 0;
                $logMinutes[$period]['fail'] += $stats['fail'] ?? 0;
            }
        }
        if (empty($logMinutes)) return $live;

        // 直接累加（日志是历史 session，内存是当前 session，不重复）
        foreach ($live as &$item) {
            if (isset($logMinutes[$item['time']])) {
                $item['count'] += $logMinutes[$item['time']]['count'];
                $item['success'] += $logMinutes[$item['time']]['success'];
                $item['fail'] += $logMinutes[$item['time']]['fail'];
            }
        }
        unset($item);
        return $live;
    }
    public function hasData(string $date): bool { return $this->backend->hasData($date); }
    public function getRecords(string $project, string $class, string $method, string $minute, int $limit = 100): array {
        $records = $this->backend->getRecords($project, $class, $method, $minute, $limit);
        // 内存/Redis 无数据时从日志文件回退
        if (empty($records)) {
            $date = substr($minute, 0, 10);
            $records = MonitorLogger::readRecordsFromLog($this->getLogDir(), $date, $project, $class, $method, $minute, $this->sessionId);
        }
        return array_slice($records, 0, $limit);
    }

    // ========== 日志文件回退读取（与驱动无关） ==========

    private function getLogDir(): string
    {
        return runtime_path('logs/monitor');
    }

    public function getDashboardFromLog(string $date): array
    {
        $log = MonitorLogger::readDayLog($this->getLogDir(), $date, $this->sessionId);
        if ($log === null) {
            return ['date' => $date, 'total_count' => 0, 'success' => 0, 'fail' => 0, 'success_rate' => 0, 'avg_time' => 0];
        }
        return $log['dashboard'] ?? ['date' => $date, 'total_count' => 0, 'success' => 0, 'fail' => 0, 'success_rate' => 0, 'avg_time' => 0];
    }

    public function getSlowRankingFromLog(string $date, int $limit = 20): array
    {
        $log = MonitorLogger::readDayLog($this->getLogDir(), $date, $this->sessionId);
        if ($log === null) return [];
        return array_slice($log['slow_ranking'] ?? [], 0, $limit);
    }

    public function getCountRankingFromLog(string $date, int $limit = 20): array
    {
        $log = MonitorLogger::readDayLog($this->getLogDir(), $date, $this->sessionId);
        if ($log === null) return [];
        return array_slice($log['count_ranking'] ?? [], 0, $limit);
    }

    public function getDetailFromLog(string $project, string $class, string $method, string $date): array
    {
        $log = MonitorLogger::readHourLog($this->getLogDir(), $date, $this->sessionId);
        if ($log === null) return [];
        $result = [];
        foreach ($log['details'] ?? [] as $detail) {
            if ($project && ($detail['project'] ?? '') !== $project) continue;
            if ($class && ($detail['class'] ?? '') !== $class) continue;
            if ($method && ($detail['method'] ?? '') !== $method) continue;
            $result[] = $detail;
        }
        return $result;
    }

    public function getTreeFromLog(string $date): array
    {
        $log = MonitorLogger::readDayLog($this->getLogDir(), $date, $this->sessionId);
        if ($log === null) return [];
        return $log['tree'] ?? [];
    }

    public function searchFromLog(string $keyword, string $date): array
    {
        $log = MonitorLogger::readDayLog($this->getLogDir(), $date, $this->sessionId);
        if ($log === null) return [];
        $keyword = strtolower($keyword);
        $results = [];
        foreach (($log['slow_ranking'] ?? []) as $item) {
            $searchStr = strtolower(($item['project'] ?? '') . ($item['class'] ?? '') . ($item['method'] ?? '') . ($item['uri'] ?? ''));
            if (!str_contains($searchStr, $keyword)) continue;
            $results[] = [
                'project' => $item['project'] ?? '', 'class' => $item['class'] ?? '',
                'method' => $item['method'] ?? '', 'uri' => $item['uri'] ?? '',
                'count' => $item['count'] ?? 0, 'success_rate' => 0, 'avg_time' => $item['avg_time'] ?? 0,
            ];
        }
        return $results;
    }

    public function getAvailableDates(): array
    {
        $today = date('Y-m-d');
        $dates = [];
        for ($i = -7; $i <= 7; $i++) {
            $date = date('Y-m-d', strtotime("{$i} days"));
            $hasBackend = $this->hasData($date);
            $hasLog = is_file($this->getLogDir() . "/{$date}.json");
            $dates[] = [
                'date' => $date, 'has_data' => $hasBackend || $hasLog,
                'source' => $hasBackend ? $this->driver : ($hasLog ? 'log' : 'none'),
                'is_today' => $date === $today,
            ];
        }
        return $dates;
    }
}
