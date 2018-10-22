package ru.nsu.fit.sogreshilin;

import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jgroups.*;
import org.jgroups.jmx.JmxConfigurator;
import org.jgroups.util.Util;


public class MyChat extends ReceiverAdapter {
    private static final Logger LOG = LogManager.getLogger(MyChat.class);

    private static final String CLUSTER_NAME = "small-little-cluster";
    private static final String DOMAIN = "sogreshilin-channel";
    private static final String DEFAULT_CONFIG_DIR = "src/resources/";
    private static final String DEFAULT_CONFIG_NAME = "chat-config.xml";

    private static final Set<String> EXIT_KEY_WORDS = new HashSet<>();
    private static final String UNKNOWN = "Unknown";

    static {
        EXIT_KEY_WORDS.add("/exit");
        EXIT_KEY_WORDS.add("/quit");
    }

    private JChannel channel;

    @Override
    public void viewAccepted(View view) {
        System.out.println("Change in membership: " + view.getMembers());
        super.viewAccepted(view);
    }

    @Override
    public void receive(Message message) {
        System.out.printf("[%s]: %s\n", message.getSrc(), message.getObject());
    }

    private void start(String pathToXmlConfigFile, String name) throws Exception {
        if (Paths.get(pathToXmlConfigFile).toFile().isFile()) {
            channel = new JChannel(pathToXmlConfigFile);
            channel.setName(name);
            channel.setReceiver(this);
            channel.connect(CLUSTER_NAME);
            JmxConfigurator.registerChannel(channel, Util.getMBeanServer(), DOMAIN, channel.getClusterName(), true);
            printWelcomeMessage(name);
            loop();
            channel.close();
        } else {
            Path path = Paths.get(pathToXmlConfigFile);
            throw new FileNotFoundException("Specified path is invalid: " + path.toAbsolutePath());
        }
    }

    private void loop() throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        while (true) {
            String line = reader.readLine();
            if (EXIT_KEY_WORDS.contains(line.toLowerCase())) {
                break;
            }
            channel.send(new Message(null, line));
        }
    }

    private void printWelcomeMessage(String name) {
        System.out.printf("Hello %s! Welcome to %s chat!\n", name, CLUSTER_NAME);
        System.out.printf("To quit, please, use one of the following key words: %s.\n", EXIT_KEY_WORDS);
    }

    private static String requestUserName() {
        System.out.print("Enter your name, please: ");
        Reader reader = new BufferedReader(new InputStreamReader(System.in));
        try {
            return ((BufferedReader) reader).readLine();
        } catch (IOException e) {
            LOG.error("Error reading user name", e);
        }
        return UNKNOWN;
    }

    public static void main(String[] args) throws Exception {
        String name = requestUserName();
        new MyChat().start(DEFAULT_CONFIG_DIR + DEFAULT_CONFIG_NAME, name);
    }
}
