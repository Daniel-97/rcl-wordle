package server.entity;

import common.utils.ConfigReader;
import common.utils.WordleLogger;

import java.util.Properties;

public class ServerConfig {

	private static final WordleLogger logger = new WordleLogger(ServerConfig.class.getName());
	public final static String STUB_NAME = "WORDLE-SERVER";
	public static int TCP_PORT;
	public static int RMI_PORT;
	public static String MULTICAST_IP;
	public static int MULTICAST_PORT;
	public static int WORD_TIME_MINUTES;
	public static int WORDLE_MAX_ATTEMPTS = 12;

	public static void loadConfig() {
		// Leggi le configurazioni dal file
		Properties properties = ConfigReader.readConfig();
		try {
			ServerConfig.TCP_PORT = Integer.parseInt(ConfigReader.readProperty(properties,"app.tcp.port"));
			ServerConfig.RMI_PORT = Integer.parseInt(ConfigReader.readProperty(properties,"app.rmi.port"));
			ServerConfig.MULTICAST_IP = ConfigReader.readProperty(properties, "app.multicast.ip");
			ServerConfig.MULTICAST_PORT = Integer.parseInt(ConfigReader.readProperty(properties, "app.multicast.port"));
			ServerConfig.WORD_TIME_MINUTES = Integer.parseInt(ConfigReader.readProperty(properties, "app.wordle.word.time.minutes"));
			if (ServerConfig.WORD_TIME_MINUTES <= 1) {
				logger.error("Valore app.wordle.word.time.minutes invalido!");
				System.exit(-1);
			}
		} catch (NoSuchFieldException e) {
			logger.error("Parametro di configurazione non trovato! " + e.getMessage());
			System.exit(-1);
		} catch (NumberFormatException e) {
			logger.error("Parametro di configurazione malformato! " + e.getMessage());
			System.exit(-1);
		}
	}
}
