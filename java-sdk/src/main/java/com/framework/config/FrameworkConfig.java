package com.framework.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * 框架配置管理
 * 
 * 支持从配置文件（YAML/Properties）、环境变量、远程配置中心加载配置。
 * 配置优先级：本地文件 < 环境变量 < 远程配置（etcd）
 * 
 * 需求: 9.1, 9.2, 9.4
 */
public class FrameworkConfig {

    private static final Logger logger = LoggerFactory.getLogger(FrameworkConfig.class);

    private final ConcurrentHashMap<String, String> properties = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<Consumer<ConfigChangeEvent>> listeners = new CopyOnWriteArrayList<>();
    private final List<String> loadedSources = new ArrayList<>();

    // 配置验证规则
    private final Map<String, ConfigValidator> validators = new ConcurrentHashMap<>();

    public FrameworkConfig() {
    }

    /**
     * 从 YAML 文件加载配置
     */
    public FrameworkConfig loadFromYaml(String filePath) {
        File file = new File(filePath);
        if (!file.exists()) {
            logger.warn("配置文件不存在: {}", filePath);
            return this;
        }
        try {
            ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
            @SuppressWarnings("unchecked")
            Map<String, Object> yamlMap = yamlMapper.readValue(file, Map.class);
            flattenMap("", yamlMap);
            loadedSources.add("yaml:" + filePath);
            logger.info("已从 YAML 文件加载配置: {}", filePath);
        } catch (IOException e) {
            logger.error("加载 YAML 配置文件失败: {}", filePath, e);
        }
        return this;
    }

    /**
     * 从 classpath 资源加载 YAML 配置
     */
    public FrameworkConfig loadFromClasspath(String resourcePath) {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                logger.warn("classpath 资源不存在: {}", resourcePath);
                return this;
            }
            ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
            @SuppressWarnings("unchecked")
            Map<String, Object> yamlMap = yamlMapper.readValue(is, Map.class);
            flattenMap("", yamlMap);
            loadedSources.add("classpath:" + resourcePath);
            logger.info("已从 classpath 加载配置: {}", resourcePath);
        } catch (IOException e) {
            logger.error("加载 classpath 配置失败: {}", resourcePath, e);
        }
        return this;
    }

    /**
     * 从 Properties 文件加载配置
     */
    public FrameworkConfig loadFromProperties(String filePath) {
        File file = new File(filePath);
        if (!file.exists()) {
            logger.warn("Properties 文件不存在: {}", filePath);
            return this;
        }
        try (var fis = new java.io.FileInputStream(file)) {
            Properties props = new Properties();
            props.load(fis);
            props.forEach((k, v) -> properties.put(k.toString(), v.toString()));
            loadedSources.add("properties:" + filePath);
            logger.info("已从 Properties 文件加载配置: {}", filePath);
        } catch (IOException e) {
            logger.error("加载 Properties 配置文件失败: {}", filePath, e);
        }
        return this;
    }

    /**
     * 从环境变量加载配置（覆盖已有配置）
     * 
     * 环境变量命名规则：FRAMEWORK_ 前缀，用下划线分隔层级
     * 例如：FRAMEWORK_NETWORK_HOST -> network.host
     */
    public FrameworkConfig loadFromEnvironment() {
        return loadFromEnvironment("FRAMEWORK_");
    }

    /**
     * 从环境变量加载配置，使用指定前缀
     */
    public FrameworkConfig loadFromEnvironment(String prefix) {
        System.getenv().forEach((key, value) -> {
            if (key.startsWith(prefix)) {
                String configKey = key.substring(prefix.length())
                        .toLowerCase()
                        .replace("_", ".");
                String oldValue = properties.put(configKey, value);
                if (oldValue != null) {
                    logger.debug("环境变量覆盖配置: {} = {} (原值: {})", configKey, value, oldValue);
                }
            }
        });
        loadedSources.add("environment:" + prefix);
        logger.info("已从环境变量加载配置 (前缀: {})", prefix);
        return this;
    }

    /**
     * 从远程配置源合并配置（优先级最高）
     */
    public FrameworkConfig mergeRemoteConfig(Map<String, String> remoteConfig) {
        remoteConfig.forEach((key, value) -> {
            String oldValue = properties.put(key, value);
            if (oldValue != null && !oldValue.equals(value)) {
                notifyListeners(new ConfigChangeEvent(key, oldValue, value, "remote"));
            }
        });
        loadedSources.add("remote");
        logger.info("已合并远程配置，共 {} 项", remoteConfig.size());
        return this;
    }

    /**
     * 运行时更新配置
     */
    public void updateConfig(String key, String value) {
        String oldValue = properties.put(key, value);
        if (!Objects.equals(oldValue, value)) {
            logger.info("配置已更新: {} = {} (原值: {})", key, value, oldValue);
            notifyListeners(new ConfigChangeEvent(key, oldValue, value, "runtime"));
        }
    }

    // ==================== 配置读取 ====================

    public String getString(String key) {
        return properties.get(key);
    }

    public String getString(String key, String defaultValue) {
        return properties.getOrDefault(key, defaultValue);
    }

    public int getInt(String key, int defaultValue) {
        String value = properties.get(key);
        if (value == null) return defaultValue;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            logger.warn("配置值不是有效整数: {} = {}", key, value);
            return defaultValue;
        }
    }

    public long getLong(String key, long defaultValue) {
        String value = properties.get(key);
        if (value == null) return defaultValue;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            logger.warn("配置值不是有效长整数: {} = {}", key, value);
            return defaultValue;
        }
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        String value = properties.get(key);
        if (value == null) return defaultValue;
        return Boolean.parseBoolean(value);
    }

    public double getDouble(String key, double defaultValue) {
        String value = properties.get(key);
        if (value == null) return defaultValue;
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            logger.warn("配置值不是有效浮点数: {} = {}", key, value);
            return defaultValue;
        }
    }

    /**
     * 获取指定前缀的所有配置
     */
    public Map<String, String> getSubConfig(String prefix) {
        Map<String, String> sub = new LinkedHashMap<>();
        String dotPrefix = prefix.endsWith(".") ? prefix : prefix + ".";
        properties.forEach((k, v) -> {
            if (k.startsWith(dotPrefix)) {
                sub.put(k.substring(dotPrefix.length()), v);
            }
        });
        return sub;
    }

    public Map<String, String> getAllProperties() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(properties));
    }

    public boolean containsKey(String key) {
        return properties.containsKey(key);
    }

    public int size() {
        return properties.size();
    }

    public List<String> getLoadedSources() {
        return Collections.unmodifiableList(loadedSources);
    }

    // ==================== 配置验证 ====================

    /**
     * 注册配置验证规则
     */
    public void registerValidator(String key, ConfigValidator validator) {
        validators.put(key, validator);
    }

    /**
     * 验证所有配置
     * 
     * @return 验证错误列表，空列表表示验证通过
     */
    public List<String> validate() {
        List<String> errors = new ArrayList<>();
        validators.forEach((key, validator) -> {
            String value = properties.get(key);
            String error = validator.validate(key, value);
            if (error != null) {
                errors.add(error);
            }
        });
        return errors;
    }

    // ==================== 配置变更监听 ====================

    /**
     * 注册配置变更监听器
     */
    public void addChangeListener(Consumer<ConfigChangeEvent> listener) {
        listeners.add(listener);
    }

    public void removeChangeListener(Consumer<ConfigChangeEvent> listener) {
        listeners.remove(listener);
    }

    private void notifyListeners(ConfigChangeEvent event) {
        for (Consumer<ConfigChangeEvent> listener : listeners) {
            try {
                listener.accept(event);
            } catch (Exception e) {
                logger.error("配置变更监听器执行失败", e);
            }
        }
    }

    // ==================== 敏感配置加密 ====================

    /**
     * 设置加密配置值
     * 值以 ENC() 包裹存储，读取时自动解密
     */
    public void setEncrypted(String key, String value, ConfigEncryptor encryptor) {
        String encrypted = encryptor.encrypt(value);
        properties.put(key, "ENC(" + encrypted + ")");
    }

    /**
     * 获取可能加密的配置值（自动解密）
     */
    public String getDecrypted(String key, ConfigEncryptor encryptor) {
        String value = properties.get(key);
        if (value == null) return null;
        if (value.startsWith("ENC(") && value.endsWith(")")) {
            String encrypted = value.substring(4, value.length() - 1);
            return encryptor.decrypt(encrypted);
        }
        return value;
    }

    // ==================== 内部方法 ====================

    @SuppressWarnings("unchecked")
    private void flattenMap(String prefix, Map<String, Object> map) {
        map.forEach((key, value) -> {
            String fullKey = prefix.isEmpty() ? key : prefix + "." + key;
            if (value instanceof Map) {
                flattenMap(fullKey, (Map<String, Object>) value);
            } else if (value != null) {
                properties.put(fullKey, value.toString());
            }
        });
    }

    /**
     * 配置验证器接口
     */
    @FunctionalInterface
    public interface ConfigValidator {
        /**
         * 验证配置值
         * @return 错误消息，null 表示验证通过
         */
        String validate(String key, String value);
    }

    /**
     * 配置加密器接口
     */
    public interface ConfigEncryptor {
        String encrypt(String plainText);
        String decrypt(String cipherText);
    }

    /**
     * 配置变更事件
     */
    public record ConfigChangeEvent(
            String key,
            String oldValue,
            String newValue,
            String source
    ) {}
}
