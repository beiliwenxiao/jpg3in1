package com.framework.security;

import com.framework.exception.ErrorCode;
import com.framework.exception.FrameworkException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * API 密钥认证器
 * 
 * 支持 API 密钥的生成、验证和管理。
 * 密钥以哈希形式存储，支持关联角色和权限。
 * 
 * 需求: 11.4, 11.6
 */
public class ApiKeyAuthenticator {

    private static final Logger logger = LoggerFactory.getLogger(ApiKeyAuthenticator.class);

    // 存储 API 密钥哈希 -> 密钥信息
    private final ConcurrentHashMap<String, ApiKeyInfo> keyStore = new ConcurrentHashMap<>();

    /**
     * 生成新的 API 密钥
     */
    public String generateApiKey(String name, List<String> roles) {
        String apiKey = generateRandomKey();
        String keyHash = hashKey(apiKey);

        keyStore.put(keyHash, new ApiKeyInfo(name, roles, System.currentTimeMillis()));
        logger.info("已生成 API 密钥: name={}", name);
        return apiKey;
    }

    /**
     * 验证 API 密钥
     * 
     * @return 密钥关联的信息
     * @throws FrameworkException 如果密钥无效
     */
    public ApiKeyInfo validateApiKey(String apiKey) {
        String keyHash = hashKey(apiKey);
        ApiKeyInfo info = keyStore.get(keyHash);
        if (info == null) {
            throw new FrameworkException(ErrorCode.UNAUTHORIZED, "API 密钥无效");
        }
        return info;
    }

    /**
     * 撤销 API 密钥
     */
    public boolean revokeApiKey(String apiKey) {
        String keyHash = hashKey(apiKey);
        ApiKeyInfo removed = keyStore.remove(keyHash);
        if (removed != null) {
            logger.info("已撤销 API 密钥: name={}", removed.name());
            return true;
        }
        return false;
    }

    /**
     * 获取所有已注册的密钥名称
     */
    public List<String> listKeyNames() {
        return keyStore.values().stream().map(ApiKeyInfo::name).toList();
    }

    private String generateRandomKey() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return "fk_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashKey(String apiKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(apiKey.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("哈希计算失败", e);
        }
    }

    public record ApiKeyInfo(String name, List<String> roles, long createdAt) {}
}
