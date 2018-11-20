package ru.nsu.fit.sogreshilin;

import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jgroups.JChannel;
import org.jgroups.util.Util;

import java.io.*;
import java.util.Map;

public class Node {
    private static final String CLUSTER_NAME = "stocks";
    private static final String PATH_TO_XML_CONFIG_FILE = "src/resources/jgroups-config.xml";
    private static final Logger LOG = LogManager.getLogger(Node.class);
    private static final String USAGE = "[1] Get all entries; " +
            "[2] Get value; " +
            "[3] Set value; " +
            "[4] Compare and swap " +
            "[5] Remove value; " +
            "[t] Test; " +
            "[x] Exit";
    private static final int N = 1_000_000;


    public final ReplicatedMap stocks;
    private JChannel channel;
    private final Map<Character, Runnable> handlers = Map.of(
            '1', this::printAll,
            '2', this::readKeyAndGet,
            '3', this::readEntryAndSet,
            '4', this::readEntryAndSwap,
            '5', this::readKeyAndRemove,
            'x', this::finish,
            't', this::test
    );

    private void test() {
        System.out.println("test");
        long start = System.currentTimeMillis();
        for (int i = 0; i < N; ++i) {
            System.out.println(i);
            stocks.put(String.valueOf(i), i);
            long current = System.currentTimeMillis();
            if (current - start > 3000) {
                System.out.println("Speed: " + N / (current - start) / 1000 + " puts per sec");
            }
        }
    }

    public Node(String pathToXmlConfigFile) throws Exception {
        channel = new JChannel(pathToXmlConfigFile);
        channel.connect(CLUSTER_NAME);
        stocks = new ReplicatedMap(channel);
    }

    private void loop() throws Exception {
        while (true) {
            char keyPress = (char) Util.keyPress(USAGE);
            if (handlers.containsKey(keyPress)) {
                handlers.get(keyPress).run();
            } else {
                System.out.println("NO SUCH ENTRY");
            }
        }
    }

    private void readKeyAndGet() {
        String key = input("Symbol");
        Optional<Double> maybeValue = stocks.get(key);
        maybeValue.ifPresentOrElse(
                value -> System.out.println(key + " is " + value),
                () -> System.out.println("no value for the key=" + key)
        );
    }

    private void readEntryAndSet() {
        String key = input("Symbol");
        double value = Double.parseDouble(input("Value"));
        boolean result = stocks.put(key, value);
        System.out.printf("%s\n", result ? "Entry set" : "Entry was not set");
    }

    private void readEntryAndSwap() {
        String key = input("Symbol");
        double oldValue = Double.parseDouble(input("Old value"));
        double newValue = Double.parseDouble(input("New value"));
        boolean result = stocks.compareAndSwap(key, oldValue, newValue);
        System.out.printf("%s\n", result
                ? "Entry updated"
                : "Entry was not updated, because old value does not equal to current value");
    }

    private void readKeyAndRemove() {
        String key = input("Symbol");
        boolean result = stocks.remove(key);
        System.out.printf("%s\n", result ? "Entry removed" : "No entry with such key found");
    }

    private void printAll() {
        System.out.println("Stocks:");
        synchronized (stocks) {
            for (Map.Entry<String, Double> entry : stocks.entrySet()) {
                System.out.println(entry.getKey() + ": " + entry.getValue());
            }
        }
    }

    private static String input(String hint) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        System.out.print(hint + ": ");
        try {
            return reader.readLine();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void finish() {
        channel.close();
        System.exit(0);
    }

    public static void main(String[] args) throws Exception {
        new Node(PATH_TO_XML_CONFIG_FILE).loop();
    }
}