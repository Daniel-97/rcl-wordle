package client.daemon;

import client.entity.ClientConfig;
import com.google.gson.JsonSyntaxException;
import common.entity.SharedGame;
import common.utils.WordleLogger;
import server.services.JsonService;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.util.List;

/**
 * Worker che rimane in ascolto di nuovi messaggi sul gruppo di multicast
 */
public class MulticastDaemon extends Thread {
	private MulticastSocket multicastSocket;
	private final List<SharedGame> userGames;
	private final WordleLogger logger = new WordleLogger(MulticastDaemon.class.getName());

	public MulticastDaemon(List<SharedGame> userGames) {

		// Imposto il thread come demone
		setDaemon(true);
		this.userGames = userGames;

		// Inizializza multicast socket
		try {
			multicastSocket = new MulticastSocket(ClientConfig.MULTICAST_PORT);
			InetAddress multicastAddress = InetAddress.getByName(ClientConfig.MULTICAST_IP);
			multicastSocket.joinGroup(multicastAddress);
			logger.debug("Join a gruppo multicast " + ClientConfig.MULTICAST_IP + " avvenuta con successo!");
		} catch (IOException e) {
			logger.error("Errore durante inizializzazione multicast, non sara' possibile ricevere condivisioni di gioco! " + e.getMessage());
			// Termino il thread corrente in caso di errore di join al gruppo multicast
			Thread.currentThread().interrupt();
		}

		// Aggiungo uno shutdown hook, chiamato appena il main thread viene interrotto
		Runtime.getRuntime().addShutdownHook(new Thread(){
			@Override
			public void run() {
				logger.debug("Shutdown daemon...");
				try {
					// Abbandono il gruppo multicast
					multicastSocket.leaveGroup(InetAddress.getByName(ClientConfig.MULTICAST_IP));
				} catch (IOException ignore) {}
			}
		});
	}

	@Override
	public void run() {

		logger.debug("Multicast daemon in ascolto...");
		final int BUFFER_SIZE = 8192;
		byte[] buffer = new byte[BUFFER_SIZE];
		DatagramPacket dp = new DatagramPacket(buffer, buffer.length);

		while (!Thread.currentThread().isInterrupted()) {

			try {
				this.multicastSocket.receive(dp);
				logger.debug("Ricevuto nuovo gioco condiviso!");
				String json = new String(dp.getData(), 0, dp.getLength());

				try {
					SharedGame wordleGame = JsonService.fromJson(json, SharedGame.class);
					this.userGames.add(wordleGame);
				} catch (JsonSyntaxException e) {
					logger.error("Errore parsing gioco condiviso da altro utente: "+e.getMessage());
				}

			} catch (IOException e) {
				logger.error("Errore durante ricezione messaggio multicast!" + e.getMessage());
			}
		}
	}
}
