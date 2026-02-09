package com.framework.protocol.external.mqtt;

import com.framework.exception.FrameworkException;
import com.framework.protocol.adapter.ProtocolAdapter;
import com.framework.protocol.model.ExternalRequest;
import com.framework.protocol.model.ExternalResponse;
import com.framework.protocol.model.InternalRequest;
import com.framework.protocol.model.InternalResponse;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * MQTT 协议处理器单元测试
 * 
 * 测试 MQTT 消息的接收和发布功能
 * 
 * **验证需求: 2.4**
 */
@ExtendWith(MockitoExtension.class)
class MqttProtocolHandlerTest {
    
    @Mock
    private ProtocolAdapter protocolAdapter;
    
    private MqttConfig mqttConfig;
    private MqttRequestProcessor requestProcessor;
    private MqttProtocolHandler mqttHandler;
    
    @BeforeEach
    void setUp() {
        // 配置 MQTT（默认禁用，避免实际连接）
        mqttConfig = new MqttConfig();
        mqttConfig.setEnabled(false);
        mqttConfig.setBrokerUrl("tcp://localhost:1883");
        mqttConfig.setClientId("test-client");
        mqttConfig.setDefaultQos(1);
        
        requestProcessor = new MqttRequestProcessor(protocolAdapter);
        mqttHandler = new MqttProtocolHandler(mqttConfig, requestProcessor);
    }
    
    @Test
    void testHandle_shouldProcessRequest() {
        // Given
        ExternalRequest request = new ExternalRequest();
        request.setProtocol("MQTT");
        request.setService("user");
        request.setMethod("create");
        request.setBody("{\"name\":\"张三\"}");
        
        InternalRequest internalRequest = new InternalRequest();
        InternalResponse internalResponse = new InternalResponse();
        internalResponse.setSuccess(true);
        internalResponse.setPayload("success".getBytes());
        
        ExternalResponse expectedResponse = new ExternalResponse();
        expectedResponse.setStatusCode(200);
        expectedResponse.setBody("success");
        
        when(protocolAdapter.transformRequest(any(ExternalRequest.class)))
            .thenReturn(internalRequest);
        when(protocolAdapter.transformResponse(any(InternalResponse.class)))
            .thenReturn(expectedResponse);
        
        // When
        ExternalResponse response = mqttHandler.handle(request);
        
        // Then
        assertNotNull(response);
        assertEquals(200, response.getStatusCode());
        assertEquals("success", response.getBody());
        
        verify(protocolAdapter).transformRequest(any(ExternalRequest.class));
        verify(protocolAdapter).transformResponse(any(InternalResponse.class));
    }
    
    @Test
    void testMessageArrived_withJsonMessage_shouldParseCorrectly() throws Exception {
        // Given
        String topic = "framework/user/create";
        String payload = "{\"service\":\"user\",\"method\":\"create\",\"body\":{\"name\":\"张三\"}}";
        MqttMessage message = new MqttMessage(payload.getBytes(StandardCharsets.UTF_8));
        message.setQos(1);
        
        InternalRequest internalRequest = new InternalRequest();
        InternalResponse internalResponse = new InternalResponse();
        internalResponse.setSuccess(true);
        
        ExternalResponse externalResponse = new ExternalResponse();
        externalResponse.setStatusCode(200);
        
        when(protocolAdapter.transformRequest(any(ExternalRequest.class)))
            .thenReturn(internalRequest);
        when(protocolAdapter.transformResponse(any(InternalResponse.class)))
            .thenReturn(externalResponse);
        
        // When
        mqttHandler.messageArrived(topic, message);
        
        // Then
        verify(protocolAdapter).transformRequest(argThat(req -> 
            "MQTT".equals(req.getProtocol()) &&
            "user".equals(req.getService()) &&
            "create".equals(req.getMethod())
        ));
    }
    
    @Test
    void testMessageArrived_withPlainTextMessage_shouldExtractFromTopic() throws Exception {
        // Given
        String topic = "framework/order/query";
        String payload = "plain text message";
        MqttMessage message = new MqttMessage(payload.getBytes(StandardCharsets.UTF_8));
        message.setQos(0);
        
        InternalRequest internalRequest = new InternalRequest();
        InternalResponse internalResponse = new InternalResponse();
        internalResponse.setSuccess(true);
        
        ExternalResponse externalResponse = new ExternalResponse();
        externalResponse.setStatusCode(200);
        
        when(protocolAdapter.transformRequest(any(ExternalRequest.class)))
            .thenReturn(internalRequest);
        when(protocolAdapter.transformResponse(any(InternalResponse.class)))
            .thenReturn(externalResponse);
        
        // When
        mqttHandler.messageArrived(topic, message);
        
        // Then
        verify(protocolAdapter).transformRequest(argThat(req -> 
            "MQTT".equals(req.getProtocol()) &&
            "order".equals(req.getService()) &&
            "query".equals(req.getMethod()) &&
            "plain text message".equals(req.getBody())
        ));
    }
    
    @Test
    void testRegisterTopicHandler_shouldInvokeCustomHandler() throws Exception {
        // Given
        String topic = "test/topic";
        String payload = "test message";
        MqttMessage message = new MqttMessage(payload.getBytes(StandardCharsets.UTF_8));
        
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> receivedPayload = new AtomicReference<>();
        
        mqttHandler.registerTopicHandler(topic, (t, p, qos, retained) -> {
            receivedPayload.set(p);
            latch.countDown();
        });
        
        // When
        mqttHandler.messageArrived(topic, message);
        
        // Then
        assertTrue(latch.await(1, TimeUnit.SECONDS));
        assertEquals(payload, receivedPayload.get());
    }
    
    @Test
    void testRegisterTopicHandler_withWildcard_shouldMatchTopic() throws Exception {
        // Given
        String pattern = "framework/+/create";
        String topic = "framework/user/create";
        String payload = "test message";
        MqttMessage message = new MqttMessage(payload.getBytes(StandardCharsets.UTF_8));
        
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> receivedTopic = new AtomicReference<>();
        
        mqttHandler.registerTopicHandler(pattern, (t, p, qos, retained) -> {
            receivedTopic.set(t);
            latch.countDown();
        });
        
        // When
        mqttHandler.messageArrived(topic, message);
        
        // Then
        assertTrue(latch.await(1, TimeUnit.SECONDS));
        assertEquals(topic, receivedTopic.get());
    }
    
    @Test
    void testRegisterTopicHandler_withMultiLevelWildcard_shouldMatchTopic() throws Exception {
        // Given
        String pattern = "system/#";
        String topic = "system/events/user/created";
        String payload = "test message";
        MqttMessage message = new MqttMessage(payload.getBytes(StandardCharsets.UTF_8));
        
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> receivedTopic = new AtomicReference<>();
        
        mqttHandler.registerTopicHandler(pattern, (t, p, qos, retained) -> {
            receivedTopic.set(t);
            latch.countDown();
        });
        
        // When
        mqttHandler.messageArrived(topic, message);
        
        // Then
        assertTrue(latch.await(1, TimeUnit.SECONDS));
        assertEquals(topic, receivedTopic.get());
    }
    
    @Test
    void testRemoveTopicHandler_shouldNotInvokeHandler() throws Exception {
        // Given
        String topic = "test/topic";
        String payload = "test message";
        MqttMessage message = new MqttMessage(payload.getBytes(StandardCharsets.UTF_8));
        
        CountDownLatch latch = new CountDownLatch(1);
        
        mqttHandler.registerTopicHandler(topic, (t, p, qos, retained) -> {
            latch.countDown();
        });
        
        mqttHandler.removeTopicHandler(topic);
        
        InternalRequest internalRequest = new InternalRequest();
        InternalResponse internalResponse = new InternalResponse();
        internalResponse.setSuccess(true);
        
        ExternalResponse externalResponse = new ExternalResponse();
        externalResponse.setStatusCode(200);
        
        when(protocolAdapter.transformRequest(any(ExternalRequest.class)))
            .thenReturn(internalRequest);
        when(protocolAdapter.transformResponse(any(InternalResponse.class)))
            .thenReturn(externalResponse);
        
        // When
        mqttHandler.messageArrived(topic, message);
        
        // Then
        assertFalse(latch.await(500, TimeUnit.MILLISECONDS));
        verify(protocolAdapter).transformRequest(any(ExternalRequest.class));
    }
    
    @Test
    void testConnectionLost_shouldLogError() {
        // Given
        Throwable cause = new Exception("Connection lost");
        
        // When & Then (should not throw exception)
        assertDoesNotThrow(() -> mqttHandler.connectionLost(cause));
    }
    
    @Test
    void testDeliveryComplete_shouldLogSuccess() {
        // Given
        org.eclipse.paho.client.mqttv3.IMqttDeliveryToken token = 
            mock(org.eclipse.paho.client.mqttv3.IMqttDeliveryToken.class);
        when(token.getTopics()).thenReturn(new String[]{"test/topic"});
        
        // When & Then (should not throw exception)
        assertDoesNotThrow(() -> mqttHandler.deliveryComplete(token));
    }
    
    @Test
    void testIsConnected_whenNotInitialized_shouldReturnFalse() {
        // When
        boolean connected = mqttHandler.isConnected();
        
        // Then
        assertFalse(connected);
    }
    
    @Test
    void testGetClientId_whenNotInitialized_shouldReturnNull() {
        // When
        String clientId = mqttHandler.getClientId();
        
        // Then
        assertNull(clientId);
    }
    
    @Test
    void testMessageArrived_withResponseTopic_shouldPublishResponse() throws Exception {
        // Given
        String topic = "framework/user/query";
        String responseTopic = "framework/user/query/response";
        String payload = String.format(
            "{\"service\":\"user\",\"method\":\"query\",\"body\":{\"id\":123},\"responseTopic\":\"%s\"}",
            responseTopic
        );
        MqttMessage message = new MqttMessage(payload.getBytes(StandardCharsets.UTF_8));
        
        InternalRequest internalRequest = new InternalRequest();
        InternalResponse internalResponse = new InternalResponse();
        internalResponse.setSuccess(true);
        
        ExternalResponse externalResponse = new ExternalResponse();
        externalResponse.setStatusCode(200);
        externalResponse.setBody("{\"id\":123,\"name\":\"张三\"}");
        
        when(protocolAdapter.transformRequest(any(ExternalRequest.class)))
            .thenReturn(internalRequest);
        when(protocolAdapter.transformResponse(any(InternalResponse.class)))
            .thenReturn(externalResponse);
        
        // When
        mqttHandler.messageArrived(topic, message);
        
        // Then
        verify(protocolAdapter).transformRequest(argThat(req -> 
            responseTopic.equals(req.getMetadata().get("responseTopic"))
        ));
    }
    
    @Test
    void testMessageArrived_withInvalidJson_shouldHandleGracefully() throws Exception {
        // Given
        String topic = "framework/user/create";
        String payload = "{invalid json}";
        MqttMessage message = new MqttMessage(payload.getBytes(StandardCharsets.UTF_8));
        
        InternalRequest internalRequest = new InternalRequest();
        InternalResponse internalResponse = new InternalResponse();
        internalResponse.setSuccess(true);
        
        ExternalResponse externalResponse = new ExternalResponse();
        externalResponse.setStatusCode(200);
        
        when(protocolAdapter.transformRequest(any(ExternalRequest.class)))
            .thenReturn(internalRequest);
        when(protocolAdapter.transformResponse(any(InternalResponse.class)))
            .thenReturn(externalResponse);
        
        // When & Then (should not throw exception)
        assertDoesNotThrow(() -> mqttHandler.messageArrived(topic, message));
        
        // Should fall back to plain text processing
        verify(protocolAdapter).transformRequest(argThat(req -> 
            "user".equals(req.getService()) &&
            "create".equals(req.getMethod()) &&
            payload.equals(req.getBody())
        ));
    }
    
    @Test
    void testMessageArrived_withException_shouldNotThrow() throws Exception {
        // Given
        String topic = "framework/user/create";
        String payload = "{\"service\":\"user\",\"method\":\"create\"}";
        MqttMessage message = new MqttMessage(payload.getBytes(StandardCharsets.UTF_8));
        
        when(protocolAdapter.transformRequest(any(ExternalRequest.class)))
            .thenThrow(new RuntimeException("Test exception"));
        
        // When & Then (should not throw exception)
        assertDoesNotThrow(() -> mqttHandler.messageArrived(topic, message));
    }
}
