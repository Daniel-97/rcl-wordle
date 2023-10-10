package client.entity;

import common.utils.ConfigReader;

import java.util.Properties;

public class ClientConfig {
	public static String STUB_NAME = "WORDLE-SERVER";
	public static int TCP_PORT;
	public static int RMI_PORT;
	public static String SERVER_IP;
	public static String MULTICAST_IP;
	public static int MULTICAST_PORT;
	public static int WORDLE_MAX_ATTEMPTS = 12;

	public static void loadConfig() {
		// Leggo file di configurazione
		Properties properties = ConfigReader.readConfig();
		try {
			RMI_PORT = Integer.parseInt(ConfigReader.readProperty(properties, "app.rmi.port"));
			TCP_PORT = Integer.parseInt(ConfigReader.readProperty(properties, "app.tcp.port"));
			SERVER_IP = ConfigReader.readProperty(properties, "app.tcp.ip");
			MULTICAST_IP = ConfigReader.readProperty(properties, "app.multicast.ip");
			MULTICAST_PORT = Integer.parseInt(ConfigReader.readProperty(properties, "app.multicast.port"));
		} catch (NoSuchFieldException e) {
			System.out.println("Parametro di configurazione non trovato! " + e.getMessage());
			System.exit(-1);
		} catch (NumberFormatException e) {
			System.out.println("Parametro di configurazione malformato! " + e.getMessage());
			System.exit(-1);
		}
	}
}
