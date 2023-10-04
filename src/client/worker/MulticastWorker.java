package client.worker;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import common.entity.WordleGame;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.MulticastSocket;
import java.util.ArrayList;
import java.util.List;

/**
 * Worker che rimane in ascolto di nuovi messaggi sul gruppo di multicast
 */
public class MulticastWorker implements Runnable {
	private static final Gson gson = new GsonBuilder().create();

	private final MulticastSocket multicastSocket;
	private final int BUFFER_SIZE = 2048;
	private final List<WordleGame> userGames;

	public MulticastWorker(MulticastSocket ms, List<WordleGame> userGames) {

		this.multicastSocket = ms;
		this.userGames = userGames;
	}

	@Override
	public void run() {

		System.out.println("Multicast worker in ascolto di messaggi multicast...");
		byte[] buffer = new byte[BUFFER_SIZE];
		DatagramPacket dp = new DatagramPacket(buffer, buffer.length);

		while (!Thread.interrupted()) {

			try {
				this.multicastSocket.receive(dp);
				String json = new String(dp.getData());
				System.out.println(json);
				// TODO sistemare, qui da eccezione
				try {
					WordleGame wordleGame = gson.fromJson(json, WordleGame.class);
					this.userGames.add(wordleGame);
				} catch (JsonSyntaxException e) {
					System.out.println("Errore parsing gioco condiviso da altro utente: "+e.getMessage());
				}

			} catch (IOException e) {
				System.out.println("Errore durante ricezione messaggio multicast!" + e.getMessage());
			}
		}
	}
}
