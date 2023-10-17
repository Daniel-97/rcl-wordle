package common.entity;

import client.entity.ClientConfig;
import common.dto.LetterDTO;

import java.util.ArrayList;
import java.util.List;

/**
 * Questa classe rappresenta una partita giocata da un utente
 */
public class WordleGame {

	public final String word;
	public final String username;
	public final int gameNumber;
	public boolean won;
	public boolean finished;
	public int attempts; // numero di tentativi fatti
	private final List<LetterDTO[]> userHint;
	private final List<String> userGuess;

	public WordleGame(String word, String username, int gameNumber) {
		this.won = false;
		this.finished = false;
		this.attempts = 0;
		this.word = word;
		this.userHint = new ArrayList<>();
		this.username = username;
		this.gameNumber = gameNumber;
		this.userGuess = new ArrayList<>();
	}

	public int getRemainingAttempts() {
		return ClientConfig.WORDLE_MAX_ATTEMPTS - attempts;
	}

	public void addGuess(String guess) {
		this.userGuess.add(guess);
	}

	public List<String> getUserGuess() {
		return userGuess;
	}
}
