import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.UUID;
import org.jgroups.*;
import org.jgroups.jmx.JmxConfigurator;
import org.jgroups.util.Util;


public class MyChat extends ReceiverAdapter {
    private static final String CLUSTER_NAME = "sogreshilin";
    private static final String DOMAIN = "sogreshilin-channel";
    private static final String DEFAULT_CONFIG_DIR = "src/resources/";
    private static final String DEFAULT_CONFIG_NAME = "chat-config.xml";

    private static final Set<String> EXIT_KEY_WORDS = Set.of("/exit", "/quit", "/finish");

    private JChannel channel;

    @Override
    public void viewAccepted(View view) {
        System.out.println("change in membership: " + view);
        super.viewAccepted(view);
    }

    @Override
    public void receive(Message message) {
        System.out.printf("[%s]: %s\n", message.getSrc(), message.getObject());
    }

    public void start(String pathToXmlConfigFile, String name) throws Exception {
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

    public static void main(String[] args) throws Exception {
        new MyChat().start(DEFAULT_CONFIG_DIR + DEFAULT_CONFIG_NAME,
                UUID.randomUUID().toString().substring(0, 7));
    }
}
