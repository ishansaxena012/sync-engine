package com.ishan.syncCanvas.config.redis;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import com.ishan.syncCanvas.chat.publisher.ChatEventSubscriber;
import com.ishan.syncCanvas.collaboration.cursor.CursorEventSubscriber;
import com.ishan.syncCanvas.collaboration.lifecycle.BoardClosureSubscriber;
import com.ishan.syncCanvas.collaboration.presence.PresenceEventSubscriber;
import com.ishan.syncCanvas.collaboration.publisher.RedisOperationSubscriber;

@Configuration
public class RedisConfig {

    public static final String BOARD_OPERATIONS_CHANNEL = "syncCanvas:board-operations";
    public static final String CURSOR_EVENTS_CHANNEL = "syncCanvas:cursor-events";
    public static final String PRESENCE_EVENTS_CHANNEL = "syncCanvas:presence-events";
    public static final String BOARD_CLOSURE_CHANNEL = "syncCanvas:board-closure";
    public static final String CHAT_EVENTS_CHANNEL = "syncCanvas:chat-events";

    @Bean
    public ChannelTopic boardOperationsTopic() {
        return new ChannelTopic(BOARD_OPERATIONS_CHANNEL);
    }

    @Bean
    public ChannelTopic cursorEventsTopic() {
        return new ChannelTopic(CURSOR_EVENTS_CHANNEL);
    }

    @Bean
    public ChannelTopic presenceEventsTopic() {
        return new ChannelTopic(PRESENCE_EVENTS_CHANNEL);
    }

    @Bean
    public ChannelTopic boardClosureTopic() {
        return new ChannelTopic(BOARD_CLOSURE_CHANNEL);
    }

    /** Chat has its own channel so it never shares traffic with operations, cursors or presence. */
    @Bean
    public ChannelTopic chatEventsTopic() {
        return new ChannelTopic(CHAT_EVENTS_CHANNEL);
    }

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            RedisOperationSubscriber operationSubscriber,
            ChannelTopic boardOperationsTopic,
            CursorEventSubscriber cursorEventSubscriber,
            ChannelTopic cursorEventsTopic,
            PresenceEventSubscriber presenceEventSubscriber,
            ChannelTopic presenceEventsTopic,
            BoardClosureSubscriber boardClosureSubscriber,
            ChannelTopic boardClosureTopic,
            ChatEventSubscriber chatEventSubscriber,
            ChannelTopic chatEventsTopic) {

        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(operationSubscriber, boardOperationsTopic);
        container.addMessageListener(cursorEventSubscriber, cursorEventsTopic);
        container.addMessageListener(presenceEventSubscriber, presenceEventsTopic);
        container.addMessageListener(boardClosureSubscriber, boardClosureTopic);
        container.addMessageListener(chatEventSubscriber, chatEventsTopic);
        return container;
    }
}
