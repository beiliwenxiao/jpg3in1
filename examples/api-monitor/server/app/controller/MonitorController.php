<?php
/**
 * MonitorController - 监控仪表盘控制器
 *
 * 提供 API 接口和 Web 仪表盘页面。
 * 所有查询自动合并内存/Redis 实时数据与日志历史数据。
 */

namespace app\controller;

use app\service\MonitorStorage;
use support\Request;
use Webman\Http\Response;

class MonitorController
{
    private MonitorStorage $storage;

    public function __construct()
    {
        $this->storage = MonitorStorage::getInstance();
    }

    /** 仪表盘概览 */
    public function dashboard(Request $request): Response
    {
        $date = $request->get('date', date('Y-m-d'));
        $live = $this->storage->getDashboard($date);
        $log  = $this->storage->getDashboardFromLog($date);

        // 日志中是所有历史 session 的累加，内存中是当前 session，直接累加
        if ($log['total_count'] > 0 && $live['total_count'] > 0) {
            $totalCount   = $log['total_count'] + $live['total_count'];
            $totalSuccess = $log['success'] + $live['success'];
            $totalFail    = $log['fail'] + $live['fail'];
            $logTime  = $log['avg_time'] * $log['total_count'];
            $liveTime = $live['avg_time'] * $live['total_count'];
            $data = [
                'date'         => $date,
                'total_count'  => $totalCount,
                'success'      => $totalSuccess,
                'fail'         => $totalFail,
                'success_rate' => $totalCount > 0 ? round($totalSuccess / $totalCount * 100, 2) : 0,
                'avg_time'     => $totalCount > 0 ? round(($logTime + $liveTime) / $totalCount, 2) : 0,
            ];
        } elseif ($log['total_count'] > 0) {
            $data = $log;
        } else {
            $data = $live;
        }
        return $this->json($data);
    }

    /** 树形菜单 */
    public function tree(Request $request): Response
    {
        $date = $request->get('date', '');
        $tree = $this->storage->getTree();
        // 合并日志中的树
        if ($date) {
            $logTree = $this->storage->getTreeFromLog($date);
            foreach ($logTree as $proj => $classes) {
                foreach ($classes as $cls => $methods) {
                    foreach ($methods as $mtd => $uri) {
                        if (!isset($tree[$proj][$cls][$mtd])) {
                            $tree[$proj][$cls][$mtd] = $uri;
                        }
                    }
                }
            }
        }
        return $this->json($tree);
    }

    /** 接口详情 */
    public function detail(Request $request): Response
    {
        $project     = $request->get('project', '');
        $class       = $request->get('class', '');
        $method      = $request->get('method', '');
        $date        = $request->get('date', date('Y-m-d'));
        $granularity = $request->get('granularity', 'minute');

        $live = $this->storage->getDetail($project, $class, $method, $date, $granularity);
        $log  = $this->storage->getDetailFromLog($project, $class, $method, $date);

        // 合并 periods
        $livePeriods = (!empty($live) && isset($live[0]['periods'])) ? $live[0]['periods'] : [];
        $logPeriods  = (!empty($log) && isset($log[0]['periods'])) ? $log[0]['periods'] : [];

        if (empty($livePeriods) && empty($logPeriods)) {
            return $this->json([]);
        }

        // 合并 periods：日志是历史 session 累加，内存是当前 session，直接累加
        $merged = $logPeriods;
        foreach ($livePeriods as $period => $stats) {
            if (isset($merged[$period])) {
                $old = $merged[$period];
                $totalCount = ($old['count'] ?? 0) + $stats['count'];
                $merged[$period] = [
                    'count'        => $totalCount,
                    'success'      => ($old['success'] ?? 0) + $stats['success'],
                    'fail'         => ($old['fail'] ?? 0) + $stats['fail'],
                    'total_time'   => ($old['total_time'] ?? 0) + $stats['total_time'],
                    'max_time'     => max($old['max_time'] ?? 0, $stats['max_time'] ?? 0),
                    'min_time'     => min($old['min_time'] ?? PHP_FLOAT_MAX, $stats['min_time'] ?? PHP_FLOAT_MAX),
                    'avg_time'     => $totalCount > 0 ? round((($old['total_time'] ?? 0) + $stats['total_time']) / $totalCount, 2) : 0,
                    'success_rate' => $totalCount > 0 ? round((($old['success'] ?? 0) + $stats['success']) / $totalCount * 100, 2) : 0,
                ];
            } else {
                $merged[$period] = $stats;
            }
        }
        ksort($merged);

        $base = !empty($live[0]) ? $live[0] : $log[0];
        $data = [['project' => $base['project'], 'class' => $base['class'], 'method' => $base['method'], 'uri' => $base['uri'], 'periods' => $merged]];
        return $this->json($data);
    }

    /** 慢速排行 */
    public function rankingSlow(Request $request): Response
    {
        $date  = $request->get('date', date('Y-m-d'));
        $limit = (int)$request->get('limit', 50);
        $live = $this->storage->getSlowRanking($date, $limit);
        $log  = $this->storage->getSlowRankingFromLog($date, $limit);
        return $this->json($this->mergeRanking($log, $live, 'avg_time', $limit));
    }

    /** 访问次数排行 */
    public function rankingCount(Request $request): Response
    {
        $date  = $request->get('date', date('Y-m-d'));
        $limit = (int)$request->get('limit', 50);
        $live = $this->storage->getCountRanking($date, $limit);
        $log  = $this->storage->getCountRankingFromLog($date, $limit);
        return $this->json($this->mergeRanking($log, $live, 'count', $limit));
    }

    /** 合并排行榜：日志（历史 session 累加）+ 内存（当前 session），直接累加 */
    private function mergeRanking(array $old, array $new, string $sortField, int $limit): array
    {
        $map = [];
        foreach ($old as $item) {
            $key = ($item['project'] ?? '') . '|' . ($item['class'] ?? '') . '|' . ($item['method'] ?? '');
            $map[$key] = $item;
        }
        foreach ($new as $item) {
            $key = ($item['project'] ?? '') . '|' . ($item['class'] ?? '') . '|' . ($item['method'] ?? '');
            if (isset($map[$key])) {
                $o = $map[$key];
                $tc = ($o['count'] ?? 0) + ($item['count'] ?? 0);
                $m = $item;
                $m['count'] = $tc;
                if (isset($item['success'])) {
                    $m['success'] = ($o['success'] ?? 0) + ($item['success'] ?? 0);
                    $m['fail'] = ($o['fail'] ?? 0) + ($item['fail'] ?? 0);
                    $m['success_rate'] = $tc > 0 ? round($m['success'] / $tc * 100, 2) : 0;
                }
                if (isset($item['avg_time'])) {
                    $ot = ($o['avg_time'] ?? 0) * ($o['count'] ?? 0);
                    $nt = ($item['avg_time'] ?? 0) * ($item['count'] ?? 0);
                    $m['avg_time'] = $tc > 0 ? round(($ot + $nt) / $tc, 2) : 0;
                }
                if (isset($item['max_time'])) {
                    $m['max_time'] = max($o['max_time'] ?? 0, $item['max_time'] ?? 0);
                }
                $map[$key] = $m;
            } else {
                $map[$key] = $item;
            }
        }
        $result = array_values($map);
        usort($result, fn($a, $b) => ($b[$sortField] ?? 0) <=> ($a[$sortField] ?? 0));
        return array_slice($result, 0, $limit);
    }

    /** 实时访问量 */
    public function realtime(Request $request): Response
    {
        return $this->json($this->storage->getRealtime());
    }

    /** 全天访问趋势（分钟级） */
    public function trend(Request $request): Response
    {
        $date = $request->get('date', date('Y-m-d'));
        return $this->json($this->storage->getDayTrend($date));
    }

    /** 搜索 */
    public function search(Request $request): Response
    {
        $keyword = $request->get('keyword', '');
        $date    = $request->get('date', date('Y-m-d'));
        return $this->json($this->storage->search($keyword, $date));
    }

    /** 可用日期列表 */
    public function dates(Request $request): Response
    {
        return $this->json($this->storage->getAvailableDates());
    }

    /** 访问明细（某接口某分钟） */
    public function records(Request $request): Response
    {
        $project = $request->get('project', '');
        $class   = $request->get('class', '');
        $method  = $request->get('method', '');
        $minute  = $request->get('minute', '');
        $limit   = (int)$request->get('limit', 100);
        return $this->json($this->storage->getRecords($project, $class, $method, $minute, $limit));
    }

    /** Web 仪表盘首页 */
    public function index(Request $request): Response
    {
        $htmlFile = base_path() . '/public/index.html';
        if (is_file($htmlFile)) {
            return new Response(200, ['Content-Type' => 'text/html; charset=utf-8'], file_get_contents($htmlFile));
        }
        return new Response(404, [], 'Dashboard HTML not found');
    }

    private function json(mixed $data): Response
    {
        return new Response(200,
            ['Content-Type' => 'application/json; charset=utf-8'],
            json_encode(['code' => 0, 'data' => $data], JSON_UNESCAPED_UNICODE)
        );
    }
}
