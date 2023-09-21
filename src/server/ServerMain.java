package server;

import server.enums.ErrorCodeEnum;
import server.exceptions.WordleException;
import server.interfaces.NotifyEvent;
import server.interfaces.ServerRMI;
import server.services.UserService;
import utils.ConfigReader;

import java.rmi.RemoteException;
import java.rmi.server.RemoteObject;
import java.util.Properties;

public class ServerMain extends RemoteObject implements ServerRMI {

	private final int tcpPort;
	private final int rmiPort;
	// Services
	private final UserService userService;

	public static void main(String[] argv) {

		// Inizializza il server
		ServerMain server = new ServerMain(null); //TODO prendere path file di configurazione dagli argomenti

	}

	public ServerMain(String configPath) {

		System.out.println("Avvio Wordle game server...");

		if (configPath == null || configPath.isEmpty()) {
			System.out.println("Nessun file di configurazione trovato, uso file di configurazione di default");
			configPath = "./src/server/app.config";
		}

		// Leggi le configurazioni dal file
		Properties properties = ConfigReader.readConfig(configPath);
		this.tcpPort = Integer.parseInt(properties.getProperty("app.tcp.port"));
		this.rmiPort = Integer.parseInt(properties.getProperty("app.rmi.port"));

		this.userService = new UserService();
	}

	@Override
	public int register(String username, String password) throws RemoteException, WordleException {

		// Controllo parametri
		if (username.isEmpty()) {
			throw new IllegalArgumentException(ErrorCodeEnum.USERNAME_REQUIRED.name());
		}

		if (password.isEmpty()) {
			throw new IllegalArgumentException(ErrorCodeEnum.PASSWORD_REQUIRED.name());
		}

		// Aggiungo nuovo utente al sistema
		User user = new User(username, password);
		this.userService.addUser(user);

		return 0;
	}

	@Override
	public void subscribeClientToEvent(String username, NotifyEvent event) throws RemoteException {

	}

	@Override
	public void unsubscribeClientToEvent(String username, NotifyEvent event) throws RemoteException {

	}
}