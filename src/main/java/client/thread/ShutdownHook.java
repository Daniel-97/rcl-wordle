package client.thread;

import client.ClientMain;
import common.interfaces.ServerRmiInterface;
import common.utils.WordleLogger;

import java.io.IOException;
import java.net.Socket;
import java.rmi.RemoteException;

public class ShutdownHook extends Thread {

	private final WordleLogger logger = new WordleLogger(ShutdownHook.class.getName());
	private final ClientMain client;
	private final ServerRmiInterface serverRMI;
	private final Socket socket;

	public ShutdownHook(ClientMain client, ServerRmiInterface serverRMI, Socket socket) {
		this.client = client;
		this.serverRMI = serverRMI;
		this.socket = socket;
	}

	@Override
	public void run() {

		logger.debug("Shutdown Wordle client...");

		try {
			// Disiscrivo client da eventi del server
			if (this.client.username != null) {
				this.serverRMI.unsubscribeClientFromEvent(this.client.username);
				// Effetto il logout
				this.client.logout();
			}
		} catch (RemoteException e) {
			logger.error("Errore chiamata RMI unsubscribeClientFromEvent()");
		}

		try {
			// Chiudo il socket TCP con il server
			this.socket.close();
		} catch (IOException e) {
			logger.error("Errore durante chiusura socket TCP");
		}

		System.out.println("Grazie per aver usato Wordle client! Torna presto!");
	}
}
