<?php
use Webman\Route;

Route::post('/jsonrpc', 'app\controller\HelloController@jsonrpc');
Route::get('/hello',    'app\controller\HelloController@hello');
Route::get('/',         'app\controller\HelloController@index');

Route::disableDefaultRoute();
