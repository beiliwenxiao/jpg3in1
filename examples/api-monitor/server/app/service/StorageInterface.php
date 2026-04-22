<?php
/**
 * StorageInterface - 监控数据存储接口
 *
 * 定义监控数据的读写操作，支持内存和 Redis 两种实现。
 */

namespace app\service;

interface StorageInterface
{
    /** 写入一条监控记录 */
    public function record(array $data): void;

    /** 获取树形菜单 */
    public function getTree(): array;

    /** 获取仪表盘概览 */
    public function getDashboard(string $date = ''): array;

    /** 获取接口详情 */
    public function getDetail(string $project, string $class, string $method, string $date = '', string $granularity = 'minute'): array;

    /** 慢速接口排行 */
    public function getSlowRanking(string $date = '', int $limit = 20): array;

    /** 访问次数排行 */
    public function getCountRanking(string $date = '', int $limit = 20): array;

    /** 实时访问量 */
    public function getRealtime(): array;

    /** 搜索接口 */
    public function search(string $keyword, string $date = ''): array;

    /** 全天分钟级访问趋势（1440个点） */
    public function getDayTrend(string $date = ''): array;

    /** 判断指定日期是否有数据 */
    public function hasData(string $date): bool;
}
