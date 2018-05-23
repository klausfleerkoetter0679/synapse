package de.otto.synapse.endpoint.receiver;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.otto.synapse.channel.ChannelPosition;
import de.otto.synapse.consumer.MessageConsumer;
import de.otto.synapse.consumer.MessageDispatcher;
import de.otto.synapse.endpoint.EndpointType;
import de.otto.synapse.endpoint.MessageEndpoint;
import de.otto.synapse.info.MessageEndpointNotification;
import de.otto.synapse.info.MessageEndpointStatus;
import org.springframework.context.ApplicationEventPublisher;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;

import static de.otto.synapse.endpoint.EndpointType.RECEIVER;

/**
 * Receiver-side {@code MessageEndpoint endpoint} of a Message Channel
 * <p>
 *     <img src="http://www.enterpriseintegrationpatterns.com/img/MessageEndpointSolution.gif" alt="Message Endpoint">
 * </p>
 */
public class MessageReceiverEndpoint extends MessageEndpoint {

    private final MessageDispatcher messageDispatcher;
    private final ApplicationEventPublisher eventPublisher;

    public MessageReceiverEndpoint(final @Nonnull String channelName,
                                   final @Nonnull ObjectMapper objectMapper,
                                   final @Nullable ApplicationEventPublisher eventPublisher) {
        super(channelName);
        messageDispatcher = new MessageDispatcher(objectMapper);
        this.eventPublisher = eventPublisher;
    }

    /**
     * Registers a MessageConsumer at the receiver endpoint.
     *
     * {@link MessageConsumer consumers} have to be thread safe as they might be called from multiple threads
     * in parallel (e.g. for kinesis streams there is one thread per shard).
     *
     * @param messageConsumer registered EventConsumer
     */
    public final void register(MessageConsumer<?> messageConsumer) {
        messageDispatcher.add(messageConsumer);
    }

    /**
     * Returns the MessageDispatcher that is used to dispatch messages.
     *
     * @return MessageDispatcher
     */
    public final MessageDispatcher getMessageDispatcher() {
        return messageDispatcher;
    }

    @Override
    protected final EndpointType getEndpointType() {
        return RECEIVER;
    }

    protected void publishEvent(final @Nonnull ChannelPosition channelPosition,
                                final @Nonnull MessageEndpointStatus status,
                                final @Nullable String message) {
        if (eventPublisher != null) {
            MessageEndpointNotification notification = MessageEndpointNotification.builder()
                    .withChannelName(this.getChannelName())
                    .withChannelPosition(channelPosition)
                    .withStatus(status)
                    .withMessage(Objects.toString(message, ""))
                    .build();
            eventPublisher.publishEvent(notification);
        }
    }

}
