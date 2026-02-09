FROM hyperf/hyperf:8.1-alpine-v3.18-swoole

WORKDIR /app

# 安装依赖
RUN apk add --no-cache protobuf-dev

# 复制 composer 文件
COPY composer.json composer.lock* ./

# 安装 PHP 依赖
RUN composer install --no-dev --optimize-autoloader

# 复制源代码
COPY . .

# 暴露端口
EXPOSE 8082 9002

# 启动应用
ENTRYPOINT ["php", "bin/hyperf.php", "start"]
