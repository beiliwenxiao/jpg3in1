<?php
use Webman\Route;

// API 接口
Route::get('/api/dashboard',    'app\controller\MonitorController@dashboard');
Route::get('/api/tree',         'app\controller\MonitorController@tree');
Route::get('/api/detail',       'app\controller\MonitorController@detail');
Route::get('/api/ranking/slow', 'app\controller\MonitorController@rankingSlow');
Route::get('/api/ranking/count','app\controller\MonitorController@rankingCount');
Route::get('/api/realtime',     'app\controller\MonitorController@realtime');
Route::get('/api/search',       'app\controller\MonitorController@search');
Route::get('/api/dates',        'app\controller\MonitorController@dates');

// 仪表盘页面
Route::get('/',                 'app\controller\MonitorController@index');

Route::disableDefaultRoute();
