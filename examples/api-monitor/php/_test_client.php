<?php
require_once __DIR__ . '/client/src/MonitorClient.php';
$c = new app\common\MonitorClient('127.0.0.1', 9501, 'test', 1.0, 1);
$c->report('TestController', 'index', '/test', 200, 42.5);
$c->flush();
echo "UDP 客户端测试完成\n";
