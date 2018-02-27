package de.otto.edison.eventsourcing.inmemory;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.otto.edison.eventsourcing.consumer.AbstractEventSource;
import de.otto.edison.eventsourcing.consumer.EventSourceNotification;
import de.otto.edison.eventsourcing.consumer.StreamPosition;
import de.otto.edison.eventsourcing.message.Message;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.function.Predicate;

import static de.otto.edison.eventsourcing.message.Header.responseHeader;
import static de.otto.edison.eventsourcing.message.Message.message;

public class InMemoryEventSource extends AbstractEventSource {


    private final InMemoryChannel inMemoryChannel;
    private final String streamName;

    public InMemoryEventSource(final String name,
                               final String streamName,
                               final InMemoryChannel inMemoryChannel,
                               final ApplicationEventPublisher eventPublisher,
                               final ObjectMapper objectMapper) {
        super(name, eventPublisher, objectMapper);
        this.streamName = streamName;
        this.inMemoryChannel = inMemoryChannel;
    }

    @Override
    public String getStreamName() {
        return streamName;
    }

    @Override
    public StreamPosition consumeAll(final StreamPosition startFrom,
                                     final Predicate<Message<?>> stopCondition) {
        publishEvent(startFrom, EventSourceNotification.Status.STARTED);
        boolean shouldStop;
        do {
            final Message<String> receivedMessage = inMemoryChannel.receive();

            if (receivedMessage == null) {
                return null;
            }

            final Message<String> messageWithHeaders = message(receivedMessage.getKey(), responseHeader("0", Instant.now()), receivedMessage.getPayload());
            dispatchingMessageConsumer().accept(messageWithHeaders);
            shouldStop = stopCondition.test(receivedMessage);
        } while (!shouldStop);
        publishEvent(null, EventSourceNotification.Status.FINISHED);
        return null;
    }
}
