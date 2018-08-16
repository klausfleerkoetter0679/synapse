package de.otto.synapse.testsupport;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.otto.synapse.configuration.aws.SqsTestConfiguration;
import de.otto.synapse.endpoint.SqsClientHelper;
import de.otto.synapse.endpoint.sender.aws.SqsMessageSender;
import de.otto.synapse.message.Message;
import de.otto.synapse.translator.JsonStringMessageTranslator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.sqs.SQSAsyncClient;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.concurrent.ExecutionException;

/**
 * This class is a data source for supplying input to the Amazon Kinesis stream. It reads lines from the
 * input file specified in the constructor and emits them by calling String.getBytes() into the
 * stream defined in the KinesisConnectorConfiguration.
 */
public class SqsTestStreamSource {

    private static final Logger LOG = LoggerFactory.getLogger(SqsTestStreamSource.class);

    private final String channelName;
    private final String inputFile;
    private final SqsMessageSender messageSender;

    public SqsTestStreamSource(String channelName, String inputFile) {
        this.inputFile = inputFile;
        this.channelName = channelName;
        final SQSAsyncClient sqsAsyncClient = new SqsTestConfiguration().sqsAsyncClient();
        final URL queueUrl = new SqsClientHelper(sqsAsyncClient).getQueueUrl(channelName);
        messageSender = new SqsMessageSender(
                channelName,
                queueUrl.toString(),
                new JsonStringMessageTranslator(new ObjectMapper()), sqsAsyncClient, "SqsTestStreamSource");
    }

    public void writeToStream() {

        InputStream inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(inputFile);
        if (inputStream == null) {
            throw new IllegalStateException("Could not find input file: " + inputFile);
        }
        try {
            processInputStream(channelName, inputStream);
        } catch (Exception e) {
            LOG.error("Encountered exception while putting data in source stream.", e);
        }
    }

    protected void processInputStream(final String channelName, final InputStream inputStream) throws IOException, ExecutionException, InterruptedException {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            while ((line = br.readLine()) != null) {
                messageSender.send(Message.message("some-message", line));
            }
        }
    }

}
