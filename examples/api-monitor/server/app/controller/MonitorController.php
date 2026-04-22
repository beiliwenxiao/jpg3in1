<?php
/**
 * MonitorController - 监控仪表盘控制器
 *
 * 提供 API 接口和 Web 仪表盘页面。
 * 查询历史数据时自动回退到日志文件。
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
        $data = $this->storage->getDashboard($date);
        if ($data['total_count'] === 0) {
            $logData = $this->storage->getDashboardFromLog($date);
            if ($logData['total_count'] > 0) {
                $data = $logData;
            }
        }
        return $this->json($data);
    }

    /** 树形菜单 */
    public function tree(Request $request): Response
    {
        $date = $request->get('date', '');
        $tree = $this->storage->getTree();
        if (empty($tree) && $date) {
            $tree = $this->storage->getTreeFromLog($date);
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

        $data = $this->storage->getDetail($project, $class, $method, $date, $granularity);
        if (empty($data) || (isset($data[0]) && empty($data[0]['periods']))) {
            $logData = $this->storage->getDetailFromLog($project, $class, $method, $date);
            if (!empty($logData)) {
                $data = $logData;
            }
        }
        return $this->json($data);
    }

    /** 慢速排行 */
    public function rankingSlow(Request $request): Response
    {
        $date  = $request->get('date', date('Y-m-d'));
        $limit = (int)$request->get('limit', 20);
        $data = $this->storage->getSlowRanking($date, $limit);
        if (empty($data)) {
            $data = $this->storage->getSlowRankingFromLog($date, $limit);
        }
        return $this->json($data);
    }

    /** 访问次数排行 */
    public function rankingCount(Request $request): Response
    {
        $date  = $request->get('date', date('Y-m-d'));
        $limit = (int)$request->get('limit', 20);
        $data = $this->storage->getCountRanking($date, $limit);
        if (empty($data)) {
            $data = $this->storage->getCountRankingFromLog($date, $limit);
        }
        return $this->json($data);
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
