package server.entity;

import java.util.Date;

/**
 * Questa classe rappresenta una partita giocata da un utente
 */
public class WordleGame {
	private final Date startedAt;
	private final int id; // Id univoco del gioco relativo all utente
	private boolean won;
	private int attempts;
	private String word;

	public WordleGame(String word, int id) {
		this.startedAt = new Date();
		this.won = false;
		this.attempts = 0;
		this.word = word;
		this.id = id;
	}

	public int getId() {
		return id;
	}

	public int getAttempts() {
		return attempts;
	}

	public boolean wonGame() {
		return this.won;
	}

	public void incrementAttempts() {
		this.attempts++;
	}
}
