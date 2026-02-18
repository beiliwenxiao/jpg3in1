FROM php:8.1-cli-alpine

WORKDIR /app

# Install system dependencies
RUN apk add --no-cache \
    curl \
    libzip-dev \
    oniguruma-dev \
    openssl-dev \
    && docker-php-ext-install \
    mbstring \
    zip \
    pcntl \
    sockets \
    pdo \
    pdo_mysql \
    opcache

# Install Composer
COPY --from=composer:latest /usr/bin/composer /usr/bin/composer

# Copy composer files
COPY composer.json composer.lock* ./

# Install PHP dependencies
RUN composer install --no-dev --optimize-autoloader --no-interaction

# Copy source code
COPY . .

EXPOSE 8082

CMD ["php", "-r", "echo 'PHP SDK ready';"]
