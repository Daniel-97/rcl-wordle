package common.entity;

import common.dto.LetterDTO;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Questa classe rappresenta una partita giocata da un utente
 */
public class WordleGame {

	private static int MAX_ATTEMPTS = 12;
	public Date startedAt;
	public int id; // Id univoco del gioco relativo all utente
	public boolean won;
	public boolean finished;
	public int attempts;
	public String word;
	private List<LetterDTO[]> guess;

	public WordleGame(){}
	public WordleGame(String word, int id) {
		this.startedAt = new Date();
		this.won = false;
		this.finished = false;
		this.attempts = 0;
		this.word = word;
		this.id = id;
		this.guess = new ArrayList<>();
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
