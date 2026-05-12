/**
 * 监控数据日志持久化模块。
 *
 * <p>使用 sessionId 机制避免重启后数据重复或丢失：
 * 每次进程启动生成唯一 sessionId，日志按 session 分槽存储，
 * 查询时合并所有历史 session（排除当前 session），内存实时数据单独叠加。</p>
 */
package com.example.monitor.server.logger;
