package server.entity;

import common.dto.UserStat;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class User {

	private final String username;
	private final String passwordHash; // password hahsata
	private final Date registeredAt; // Data registrazione utente
	private final List<WordleGame> games;
	// Todo tenere aggiornate queste statistiche
	private int lastStreak = 0;
	private int bestStreak = 0;
	public transient boolean online = false; // Indica se utente e' online oppure no, da non salvare sul json

	public User(String username, String password) {
		this.username = username;
		this.passwordHash = this.hashPassword(password);
		this.registeredAt = new Date();
		this.games = new ArrayList<>();
	}

	/**
	 * Effettua un hashing della password per questioni di sicurezza
	 * @param password
	 * @return
	 */
	private String hashPassword(String password) {
		//TODO implement the password hash with modern algotithm
		return password;
	}

	/**
	 * Verifica la password dell utente
	 * @param password
	 * @return
	 */
	public boolean verifyPassword(String password) {
		return this.passwordHash.equals(this.hashPassword(password));
	}

	public String getUsername() {
		return this.username;
	}

	/**
	 * Ritorna ultimo game giocato da utente
	 * @return
	 */
	public WordleGame getLastGame() {
		if(this.games.size() == 0) {
			return null;
		}
		return this.games.get(this.games.size() - 1);
	}

	/**
	 * Aggiunge una nuova partita per l utente
	 * @param word
	 * @return
	 */
	public WordleGame newGame(String word) {
		WordleGame lastGame = getLastGame();
		WordleGame game = null;
		if (lastGame == null) {
			game = new WordleGame(word, 0);
		} else {
			game = new WordleGame(word, lastGame.id+1);
		}

		this.games.add(game);
		return game;
	}

	/**
	 * Ritorna la media del numero di tenatativi fatti tra tutte le partite giocate
	 * @return
	 */
	public int averageAttempts() {
		int avg = 0;
		for (WordleGame game: this.games) {
			avg += game.attempts;
		}
		return avg / this.games.size();
	}

	/**
	 * Ritorna il numero di vittorie dell'utente
	 * @return
	 */
	public int wonGames() {
		int wonGames = 0;
		for (WordleGame game: this.games) {
			wonGames += game.won ? 1 : 0;
		}

		return  wonGames;
	}

	/**
	 * Ritorna le statistiche dell'utente
	 * @return
	 */
	public UserStat getStat() {
		UserStat stat = new UserStat();
		stat.playedGames = games.size();
		stat.wonGamesPercentage = wonGames() * 100 / games.size();
		stat.lastStreakWonGames = lastStreak;
		stat.bestStreakWonGames = bestStreak;

		return stat;
	}

	/**
	 * Calcola il punteggio di un utente
	 * @return
	 */
	public int getRank() {
		int wonGames = this.wonGames();
		int avgAttempts = this.averageAttempts();

		return wonGames * avgAttempts;
	}
}
