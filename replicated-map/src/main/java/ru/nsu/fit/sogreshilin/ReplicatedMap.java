package ru.nsu.fit.sogreshilin;

import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jgroups.JChannel;
import org.jgroups.ReceiverAdapter;
import org.jgroups.View;
import org.jgroups.blocks.RequestOptions;
import org.jgroups.blocks.RpcDispatcher;
import org.jgroups.blocks.locking.LockService;
import java.util.concurrent.locks.Lock;
import org.jgroups.util.RspList;
import org.jgroups.util.Util;

public class ReplicatedMap extends ReceiverAdapter {
    private static final Logger LOG = LogManager.getLogger(ReplicatedMap.class);
    private static final int TIMEOUT = 30000;
    private static final String LOCK_NAME = "cluster-lock";
    private final Map<String, Double> stocks = new HashMap<>();
    private final RpcDispatcher dispatcher;
    private final Lock lock;

    public ReplicatedMap(JChannel channel) throws Exception {
        lock = new LockService(channel).getLock(LOCK_NAME);
        this.dispatcher = new RpcDispatcher(channel, this);
        dispatcher.setMembershipListener(this);
        dispatcher.setStateListener(this);
        dispatcher.start();
        channel.getState(null, TIMEOUT);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void viewAccepted(View view) {
        LOG.info("Change in membership: {}", view.getMembers());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void getState(OutputStream output) throws Exception {
        DataOutput out = new DataOutputStream(output);
        lock.lock();
        try {
            synchronized (stocks) {
                LOG.info("Returning {} stocks", stocks.size());
                Util.objectToStream(stocks, out);
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setState(InputStream input) throws Exception {
        DataInput in = new DataInputStream(input);
        Map<String, Double> newStocks = Util.objectFromStream(in);
        synchronized (stocks) {
            LOG.info("Receiving {} stocks", newStocks.size());
            stocks.clear();
            stocks.putAll(newStocks);
        }
    }

    /**
     * Сопоставить ключ значению. Вызывается по названию метода RPC-вызовом.
     *
     * @param key   ключ
     * @param value значение
     */
    public boolean _setStock(String key, double value) {
//        synchronized (stocks) {
            stocks.put(key, value);
            LOG.info("map: set key={} to value={}", key, value);
//        }
        return true;
    }

    /**
     * Удалить запись из ассоциативного массива по ключу. Вызывается по названию метода RPC-вызовом.
     *
     * @param key ключ
     */
    public boolean _removeStock(String key) {
        Double value;
        synchronized (stocks) {
            value = stocks.remove(key);
            LOG.info("map: remove entry key={}, value={}", key, value);
        }
        return value != null;
    }

    public boolean remove(String key) {
        try {
            lock.lock();
            RspList<Boolean> responses = dispatcher.callRemoteMethods(
                    null,
                    "_removeStock",
                    new Object[]{key},
                    new Class[]{String.class},
                    RequestOptions.SYNC()
            );
            LOG.info("Responses: {}", responses);
            return !responses.getSuspectedMembers().isEmpty()
                    && responses.getResults().stream().allMatch(Boolean::booleanValue);
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            lock.unlock();
        }
    }

    public boolean put(String key, double value) {
        try {
            lock.lock();
            RspList<Boolean> responses = dispatcher.callRemoteMethods(
                    null,
                    "_setStock",
                    new Object[]{key, value},
                    new Class[]{String.class, double.class},
                    RequestOptions.SYNC()
            );
            LOG.info("Responses: {}", responses);
            return !responses.getSuspectedMembers().isEmpty()
                    && responses.getResults().stream().allMatch(Boolean::booleanValue);
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            lock.unlock();
        }
    }

    public Optional<Double> get(String key) {
        Double value;
        lock.lock();
        try {
            synchronized (stocks) {
                value = stocks.get(key);
            }
        } finally {
            lock.unlock();
        }
        return Optional.ofNullable(value);
    }

    public Set<Map.Entry<String, Double>> entrySet() {
        Set<Map.Entry<String, Double>> entrySet;
        synchronized (stocks) {
            entrySet = stocks.entrySet();
        }
        return entrySet;
    }

    public boolean compareAndSwap(String key, double oldValue, double newValue) {
        Double currentValue;
        lock.lock();
        try {
            currentValue = stocks.get(key);
            if (Objects.equals(currentValue, oldValue)) {
                RspList<Boolean> responses = dispatcher.callRemoteMethods(
                        null,
                        "_setStock",
                        new Object[]{key, newValue},
                        new Class[]{String.class, double.class},
                        RequestOptions.SYNC()
                );
                LOG.info("Responses: {}", responses);
                return !responses.getSuspectedMembers().isEmpty()
                        && responses.getResults().stream().allMatch(Boolean::booleanValue);
            }
            return false;
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            lock.unlock();
        }
    }

    public void clear() {
        synchronized (stocks) {
            stocks.clear();
        }
    }
}
