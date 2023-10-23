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
	private final MulticastSocket multicastSocket;
	private final List<SharedGame> userGames;
	private final WordleLogger logger = new WordleLogger(MulticastDaemon.class.getName());

	public MulticastDaemon(MulticastSocket ms, List<SharedGame> userGames) {

		this.multicastSocket = ms;
		this.userGames = userGames;

		// Imposto il thread come demone
		setDaemon(true);
		//TODO migliorare questa parte
		Runtime.getRuntime().addShutdownHook(new Thread(){
			@Override
			public void run() {
				logger.info("Shutdown daemon...");
				try {
					ms.leaveGroup(InetAddress.getByName(ClientConfig.MULTICAST_IP));
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			}
		});
	}

	@Override
	public void run() {

		logger.info("Multicast daemon in ascolto...");
		final int BUFFER_SIZE = 8192;
		byte[] buffer = new byte[BUFFER_SIZE];
		DatagramPacket dp = new DatagramPacket(buffer, buffer.length);

		while (true) {

			try {
				this.multicastSocket.receive(dp);
				logger.info("Ricevuto nuovo gioco condiviso!");
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
		/*
		try {
			logger.info("Abbandono il gruppo di multicast...");
			multicastSocket.leaveGroup(InetAddress.getByName(ClientConfig.MULTICAST_IP));
		} catch (IOException e) {
			logger.error("Impossibile abbandonare gruppo di multicast! "+e.getMessage());
		}
		 */
	}
}
