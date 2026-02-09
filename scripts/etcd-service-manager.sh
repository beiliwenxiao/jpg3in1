#!/bin/bash

# etcd 服务管理脚本
# 用于注册、注销和查询服务

set -e

# 配置
ETCD_ENDPOINTS=${ETCD_ENDPOINTS:-"http://localhost:2379"}
TIMEOUT=${TIMEOUT:-5}

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 显示帮助信息
show_help() {
    cat << EOF
用法: $0 <命令> [选项]

命令:
  register    注册服务
  deregister  注销服务
  list        列出所有服务
  get         获取服务详情
  watch       监听服务变化

注册服务选项:
  --name <服务名称>        (必需)
  --id <实例ID>            (必需)
  --language <语言>        (必需: java, golang, php)
  --address <地址>         (必需)
  --port <端口>            (必需)
  --version <版本>         (可选, 默认: 1.0.0)
  --protocols <协议列表>   (可选, 默认: grpc,rest)
  --ttl <TTL秒数>          (可选, 默认: 30)

注销服务选项:
  --name <服务名称>        (必需)
  --id <实例ID>            (可选, 如果不指定则注销所有实例)

获取服务选项:
  --name <服务名称>        (必需)
  --id <实例ID>            (可选)

监听服务选项:
  --name <服务名称>        (可选, 如果不指定则监听所有服务)

示例:
  # 注册服务
  $0 register --name my-service --id instance-1 --language java --address localhost --port 8080

  # 注销服务
  $0 deregister --name my-service --id instance-1

  # 列出所有服务
  $0 list

  # 获取服务详情
  $0 get --name my-service

  # 监听服务变化
  $0 watch --name my-service

EOF
}

# 注册服务
register_service() {
    local name=""
    local id=""
    local language=""
    local address=""
    local port=""
    local version="1.0.0"
    local protocols="grpc,rest"
    local ttl=30
    
    # 解析参数
    while [[ $# -gt 0 ]]; do
        case $1 in
            --name) name="$2"; shift 2 ;;
            --id) id="$2"; shift 2 ;;
            --language) language="$2"; shift 2 ;;
            --address) address="$2"; shift 2 ;;
            --port) port="$2"; shift 2 ;;
            --version) version="$2"; shift 2 ;;
            --protocols) protocols="$2"; shift 2 ;;
            --ttl) ttl="$2"; shift 2 ;;
            *) echo -e "${RED}未知参数: $1${NC}"; show_help; exit 1 ;;
        esac
    done
    
    # 验证必需参数
    if [ -z "$name" ] || [ -z "$id" ] || [ -z "$language" ] || [ -z "$address" ] || [ -z "$port" ]; then
        echo -e "${RED}错误: 缺少必需参数${NC}"
        show_help
        exit 1
    fi
    
    # 验证语言
    if [[ ! "$language" =~ ^(java|golang|php)$ ]]; then
        echo -e "${RED}错误: 语言必须是 java, golang 或 php${NC}"
        exit 1
    fi
    
    # 构建服务键和值
    local service_key="/framework/services/${name}/${id}"
    local protocols_json=$(echo "$protocols" | sed 's/,/", "/g' | sed 's/^/["/' | sed 's/$/"]/')
    local service_value=$(cat <<EOF
{
  "id": "${id}",
  "name": "${name}",
  "version": "${version}",
  "language": "${language}",
  "address": "${address}",
  "port": ${port},
  "protocols": ${protocols_json},
  "metadata": {
    "registered_at": "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  }
}
EOF
)
    
    # 创建租约
    echo "创建租约 (TTL: ${ttl}s)..."
    local lease_id=$(etcdctl --endpoints=$ETCD_ENDPOINTS --command-timeout=${TIMEOUT}s lease grant $ttl | grep -oP 'lease \K[0-9a-f]+')
    
    if [ -z "$lease_id" ]; then
        echo -e "${RED}✗ 创建租约失败${NC}"
        exit 1
    fi
    
    echo -e "${GREEN}✓ 租约创建成功: $lease_id${NC}"
    
    # 注册服务（关联租约）
    echo "注册服务..."
    if etcdctl --endpoints=$ETCD_ENDPOINTS --command-timeout=${TIMEOUT}s put "$service_key" "$service_value" --lease=$lease_id > /dev/null; then
        echo -e "${GREEN}✓ 服务注册成功${NC}"
        echo ""
        echo "服务信息:"
        echo "  名称: $name"
        echo "  实例ID: $id"
        echo "  语言: $language"
        echo "  地址: $address:$port"
        echo "  版本: $version"
        echo "  协议: $protocols"
        echo "  租约ID: $lease_id"
        echo "  TTL: ${ttl}s"
        echo ""
        echo -e "${YELLOW}注意: 服务将在 ${ttl} 秒后自动过期，需要定期续约${NC}"
    else
        echo -e "${RED}✗ 服务注册失败${NC}"
        exit 1
    fi
}

# 注销服务
deregister_service() {
    local name=""
    local id=""
    
    # 解析参数
    while [[ $# -gt 0 ]]; do
        case $1 in
            --name) name="$2"; shift 2 ;;
            --id) id="$2"; shift 2 ;;
            *) echo -e "${RED}未知参数: $1${NC}"; show_help; exit 1 ;;
        esac
    done
    
    # 验证必需参数
    if [ -z "$name" ]; then
        echo -e "${RED}错误: 缺少服务名称${NC}"
        show_help
        exit 1
    fi
    
    if [ -z "$id" ]; then
        # 注销所有实例
        local service_prefix="/framework/services/${name}/"
        echo "注销服务的所有实例: $name"
        
        if etcdctl --endpoints=$ETCD_ENDPOINTS --command-timeout=${TIMEOUT}s del "$service_prefix" --prefix > /dev/null; then
            echo -e "${GREEN}✓ 服务注销成功${NC}"
        else
            echo -e "${RED}✗ 服务注销失败${NC}"
            exit 1
        fi
    else
        # 注销指定实例
        local service_key="/framework/services/${name}/${id}"
        echo "注销服务实例: $name/$id"
        
        if etcdctl --endpoints=$ETCD_ENDPOINTS --command-timeout=${TIMEOUT}s del "$service_key" > /dev/null; then
            echo -e "${GREEN}✓ 服务实例注销成功${NC}"
        else
            echo -e "${RED}✗ 服务实例注销失败${NC}"
            exit 1
        fi
    fi
}

# 列出所有服务
list_services() {
    echo "已注册的服务:"
    echo ""
    
    local services=$(etcdctl --endpoints=$ETCD_ENDPOINTS --command-timeout=${TIMEOUT}s get "/framework/services/" --prefix --keys-only 2>/dev/null | grep -v "/.namespace" || true)
    
    if [ -z "$services" ]; then
        echo -e "${YELLOW}未发现已注册的服务${NC}"
        return
    fi
    
    local service_names=()
    while IFS= read -r key; do
        if [ -n "$key" ]; then
            # 提取服务名称
            local service_name=$(echo "$key" | sed 's|/framework/services/||' | cut -d'/' -f1)
            if [[ ! " ${service_names[@]} " =~ " ${service_name} " ]]; then
                service_names+=("$service_name")
            fi
        fi
    done <<< "$services"
    
    # 显示每个服务的实例
    for service_name in "${service_names[@]}"; do
        echo -e "${BLUE}服务: $service_name${NC}"
        
        local instances=$(etcdctl --endpoints=$ETCD_ENDPOINTS --command-timeout=${TIMEOUT}s get "/framework/services/${service_name}/" --prefix 2>/dev/null)
        
        local instance_count=0
        while IFS= read -r line; do
            if [[ $line == /framework/services/* ]]; then
                instance_count=$((instance_count + 1))
                local instance_id=$(echo "$line" | sed "s|/framework/services/${service_name}/||")
                
                # 读取下一行（值）
                read -r value
                
                # 解析 JSON（简单提取）
                local language=$(echo "$value" | grep -oP '"language":\s*"\K[^"]+' || echo "unknown")
                local address=$(echo "$value" | grep -oP '"address":\s*"\K[^"]+' || echo "unknown")
                local port=$(echo "$value" | grep -oP '"port":\s*\K[0-9]+' || echo "unknown")
                local version=$(echo "$value" | grep -oP '"version":\s*"\K[^"]+' || echo "unknown")
                
                echo "  ├─ 实例: $instance_id"
                echo "  │  ├─ 语言: $language"
                echo "  │  ├─ 地址: $address:$port"
                echo "  │  └─ 版本: $version"
            fi
        done <<< "$instances"
        
        if [ $instance_count -eq 0 ]; then
            echo "  └─ (无实例)"
        fi
        
        echo ""
    done
}

# 获取服务详情
get_service() {
    local name=""
    local id=""
    
    # 解析参数
    while [[ $# -gt 0 ]]; do
        case $1 in
            --name) name="$2"; shift 2 ;;
            --id) id="$2"; shift 2 ;;
            *) echo -e "${RED}未知参数: $1${NC}"; show_help; exit 1 ;;
        esac
    done
    
    # 验证必需参数
    if [ -z "$name" ]; then
        echo -e "${RED}错误: 缺少服务名称${NC}"
        show_help
        exit 1
    fi
    
    if [ -z "$id" ]; then
        # 获取所有实例
        local service_prefix="/framework/services/${name}/"
        echo "服务详情: $name"
        echo ""
        
        etcdctl --endpoints=$ETCD_ENDPOINTS --command-timeout=${TIMEOUT}s get "$service_prefix" --prefix 2>/dev/null | while IFS= read -r line; do
            if [[ $line == /framework/services/* ]]; then
                echo -e "${BLUE}键: $line${NC}"
                read -r value
                echo "$value" | jq '.' 2>/dev/null || echo "$value"
                echo ""
            fi
        done
    else
        # 获取指定实例
        local service_key="/framework/services/${name}/${id}"
        echo "服务实例详情: $name/$id"
        echo ""
        
        local value=$(etcdctl --endpoints=$ETCD_ENDPOINTS --command-timeout=${TIMEOUT}s get "$service_key" --print-value-only 2>/dev/null)
        
        if [ -z "$value" ]; then
            echo -e "${RED}✗ 服务实例不存在${NC}"
            exit 1
        fi
        
        echo "$value" | jq '.' 2>/dev/null || echo "$value"
    fi
}

# 监听服务变化
watch_service() {
    local name=""
    
    # 解析参数
    while [[ $# -gt 0 ]]; do
        case $1 in
            --name) name="$2"; shift 2 ;;
            *) echo -e "${RED}未知参数: $1${NC}"; show_help; exit 1 ;;
        esac
    done
    
    if [ -z "$name" ]; then
        # 监听所有服务
        echo "监听所有服务的变化..."
        echo "按 Ctrl+C 停止监听"
        echo ""
        
        etcdctl --endpoints=$ETCD_ENDPOINTS watch "/framework/services/" --prefix
    else
        # 监听指定服务
        echo "监听服务变化: $name"
        echo "按 Ctrl+C 停止监听"
        echo ""
        
        etcdctl --endpoints=$ETCD_ENDPOINTS watch "/framework/services/${name}/" --prefix
    fi
}

# 主函数
main() {
    if [ $# -eq 0 ]; then
        show_help
        exit 1
    fi
    
    local command=$1
    shift
    
    case $command in
        register)
            register_service "$@"
            ;;
        deregister)
            deregister_service "$@"
            ;;
        list)
            list_services "$@"
            ;;
        get)
            get_service "$@"
            ;;
        watch)
            watch_service "$@"
            ;;
        help|--help|-h)
            show_help
            ;;
        *)
            echo -e "${RED}未知命令: $command${NC}"
            show_help
            exit 1
            ;;
    esac
}

main "$@"
