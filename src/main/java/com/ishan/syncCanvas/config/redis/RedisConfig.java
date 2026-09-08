package com.ishan.syncCanvas.config.redis;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import com.ishan.syncCanvas.collaboration.cursor.CursorEventSubscriber;
import com.ishan.syncCanvas.collaboration.publisher.RedisOperationSubscriber;

@Configuration
public class RedisConfig {

    public static final String BOARD_OPERATIONS_CHANNEL = "syncCanvas:board-operations";
    public static final String CURSOR_EVENTS_CHANNEL = "syncCanvas:cursor-events";

    @Bean
    public ChannelTopic boardOperationsTopic() {
        return new ChannelTopic(BOARD_OPERATIONS_CHANNEL);
    }

    @Bean
    public ChannelTopic cursorEventsTopic() {
        return new ChannelTopic(CURSOR_EVENTS_CHANNEL);
    }

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            RedisOperationSubscriber operationSubscriber,
            ChannelTopic boardOperationsTopic,
            CursorEventSubscriber cursorEventSubscriber,
            ChannelTopic cursorEventsTopic) {

        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(operationSubscriber, boardOperationsTopic);
        container.addMessageListener(cursorEventSubscriber, cursorEventsTopic);
        return container;
    }
}
