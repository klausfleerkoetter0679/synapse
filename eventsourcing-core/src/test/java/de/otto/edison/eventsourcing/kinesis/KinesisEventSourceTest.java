package de.otto.edison.eventsourcing.kinesis;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import de.otto.edison.eventsourcing.consumer.Event;
import de.otto.edison.eventsourcing.consumer.EventConsumer;
import de.otto.edison.eventsourcing.consumer.StreamPosition;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import software.amazon.awssdk.services.kinesis.KinesisClient;
import software.amazon.awssdk.services.kinesis.model.GetRecordsResponse;
import software.amazon.awssdk.services.kinesis.model.GetShardIteratorResponse;
import software.amazon.awssdk.services.kinesis.model.Record;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

import static com.google.common.collect.ImmutableList.of;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class KinesisEventSourceTest {

    private static final String STREAM_NAME = "test";

    @Mock
    private KinesisStream kinesisStream;

    @Mock
    private KinesisClient kinesisClient;

    @Mock
    private EventConsumer<TestData> consumer;

    private ObjectMapper objectMapper = new ObjectMapper();
    private KinesisEventSource<TestData> eventSource;

    @Before
    public void setUp() throws Exception {
        eventSource = new KinesisEventSource<>(TestData.class, objectMapper, kinesisStream);

        KinesisShard shard1 = new KinesisShard("shard1", kinesisStream, kinesisClient);
        when(kinesisStream.retrieveAllOpenShards()).thenReturn(of(shard1));
        when(kinesisClient.getShardIterator(any())).thenReturn(GetShardIteratorResponse.builder()
                .shardIterator("someIterator")
                .build());

    }

    @Test
    @SuppressWarnings("unchecked")
    public void shouldConsumeAllEventsFromKinesis() throws Exception {
        // given
        TestData testData1 = new TestData("blue");
        TestData testData2 = new TestData("green");
        StreamPosition initialPositions = StreamPosition.of(ImmutableMap.of("shard1", "xyz"));
        GetRecordsResponse response1 = GetRecordsResponse.builder()
                .records(createRecord("blue"))
                .millisBehindLatest(1234L)
                .nextShardIterator("nextIterator")
                .build();
        GetRecordsResponse response2 = GetRecordsResponse.builder()
                .records(createRecord("green"))
                .millisBehindLatest(2345L)
                .nextShardIterator("yetAnotherIterator")
                .build();
        when(kinesisClient.getRecords(any())).thenReturn(response1, response2);

        // when
        eventSource.consumeAll(initialPositions, this::stopIfGreen, consumer);

        // then
        ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
        verify(consumer, times(2)).accept(captor.capture());
        List<Event> events = captor.getAllValues();
        assertThat(events.get(0).payload(), is(testData1));
        assertThat(events.get(1).payload(), is(testData2));
    }

    private boolean stopIfGreen(Event<TestData> event) {
        if (event == null) {
            return false;
        }
        return "green".equals(event.payload().data);
    }

    private Record createRecord(String data) {
        String json = "{\"data\": \"" + data + "\"}";
        return Record.builder()
                .data(ByteBuffer.wrap(json.getBytes(StandardCharsets.UTF_8)))
                .sequenceNumber("sequence-" + data)
                .build();
    }

    public static class TestData {

        TestData() {

        }

        public TestData(String data) {
            this.data = data;
        }

        @JsonProperty
        public String data;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            TestData testData = (TestData) o;
            return Objects.equals(data, testData.data);
        }

        @Override
        public int hashCode() {
            return Objects.hash(data);
        }
    }
}