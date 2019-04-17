package de.otto.synapse.messagestore;

import com.google.common.collect.ImmutableSet;
import de.otto.synapse.channel.ChannelPosition;

import javax.annotation.concurrent.ThreadSafe;
import java.util.Deque;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Stream;

import static de.otto.synapse.messagestore.Indexers.noOpIndexer;

/**
 * Concurrent in-memory and on-heap implementation of a MessageStore that is storing all messages in insertion order.
 *
 * <p>Indexing of messages is supported by this implementation</p>
 */
@ThreadSafe
public class InMemoryMessageStore implements MessageStore {

    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final Deque<MessageStoreEntry> entries = new ConcurrentLinkedDeque<>();
    private final ConcurrentMap<String, Deque<MessageStoreEntry>> indexes = new ConcurrentHashMap<>();
    private final ChannelPositions channelPositions = new ChannelPositions();
    private final Indexer indexer;

    public InMemoryMessageStore() {
        this.indexer = noOpIndexer();
    }

    public InMemoryMessageStore(final Indexer indexer) {
        this.indexer = indexer;
    }

    @Override
    public void add(final MessageStoreEntry entry) {
        lock.writeLock().lock();
        try {
            final MessageStoreEntry indexedEntry = indexer.index(entry);
            entries.add(indexedEntry);
            indexedEntry.getFilterValues().forEach((key, value) -> {
                final String indexKey = indexKeyOf(key, value);
                if (!indexes.containsKey(indexKey)) {
                    indexes.put(indexKey, new ConcurrentLinkedDeque<>());
                }
                indexes.get(indexKey).addLast(indexedEntry);
            });
            channelPositions.updateFrom(indexedEntry);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public Set<String> getChannelNames() {
        lock.readLock().lock();
        try {
            return channelPositions.getChannelNames();
        } finally {
          lock.readLock().unlock();
        }
    }

    @Override
    public ImmutableSet<Index> getIndexes() {
        return indexer.getIndexes();
    }

    @Override
    public ChannelPosition getLatestChannelPosition(final String channelName) {
        lock.readLock().lock();
        try {
            return channelPositions.getLatestChannelPosition(channelName);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public Stream<MessageStoreEntry> stream() {
        lock.readLock().lock();
        try {
            return entries.stream();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public Stream<MessageStoreEntry> stream(Index index, String value) {
        String indexKey = indexKeyOf(index, value);
        if (indexes.containsKey(indexKey)) {
            return indexes.get(indexKey).stream();
        } else {
            return Stream.empty();
        }
    }

    @Override
    public int size() {
        return entries.size();
    }

    private String indexKeyOf(Index index, String value) {
        return index.getName() + "#" + value;
    }
}
