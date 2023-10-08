package common.entity;

import common.dto.LetterDTO;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Questa classe rappresenta una partita giocata da un utente
 */
public class WordleGame {

	private static final int MAX_ATTEMPTS = 12;
	public int gameNumber;
	public boolean won;
	public boolean finished;
	public int attempts;
	public String word;
	public String username;
	private List<LetterDTO[]> guess;

	public WordleGame(){}
	public WordleGame(String word, String username, int gameNumber) {
		this.won = false;
		this.finished = false;
		this.attempts = 0;
		this.word = word;
		this.guess = new ArrayList<>();
		this.username = username;
		this.gameNumber = gameNumber;
	}

	public int getRemainingAttempts() {
		return MAX_ATTEMPTS - attempts;
	}

	public void addGuess(LetterDTO[] guess) {
		attempts++;
		this.guess.add(guess);
	}

	public LetterDTO[][] getGuess() {
		return this.guess.toArray(new LetterDTO[attempts][]);
	}
}
