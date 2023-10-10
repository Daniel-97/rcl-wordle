package server.entity;

import client.entity.ClientConfig;
import common.dto.GuessDistributionItem;
import common.dto.UserStat;
import common.entity.WordleGame;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.util.*;

public class User {

	private final String username;
	private final String password; // password hash
	private final String salt; // Password salt
	private List<WordleGame> games;
	public transient boolean online;
	private int lastStreak = 0;
	private int bestStreak = 0;

	public User(String username, String password) throws InvalidKeySpecException, NoSuchAlgorithmException {
		this.username = username;
		this.salt = generateRandomSalt();
		this.password = hashPassword(password, Base64.getDecoder().decode(this.salt));
		this.online = false;
	}

	/**
	 * Effettua un hashing della password con algoritmo PBKDF2
	 * @param password
	 * @param salt
	 * @return
	 */
	private static String hashPassword(String password, byte[] salt) throws InvalidKeySpecException, NoSuchAlgorithmException {

		KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, 65536, 128);
		SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
		byte[] hash = factory.generateSecret(spec).getEncoded();
		return Base64.getEncoder().encodeToString(hash);
	}

	/**
	 * Crea un salt causale
	 * @return
	 */
	private static String generateRandomSalt() {
		// Genero un salt casuale per la password
		SecureRandom secureRandom = new SecureRandom();
		byte[] salt = new byte[16];
		secureRandom.nextBytes(salt);

		return Base64.getEncoder().encodeToString(salt);
	}

	/**
	 * Verifica la password dell utente usando il salt salvato
	 * @param password
	 * @return
	 */
	public boolean verifyPassword(String password) throws InvalidKeySpecException, NoSuchAlgorithmException {
		return this.password.equals(hashPassword(password, Base64.getDecoder().decode(this.salt)));
	}

	public String getUsername() {
		return this.username;
	}

	/**
	 * Ritorna ultimo game giocato da utente
	 * @return
	 */
	public WordleGame getLastGame() {
		if(this.games == null || this.games.size() == 0) {
			return null;
		}
		return this.games.get(this.games.size() - 1);
	}

	/**
	 * Aggiunge una nuova partita per l utente usando ultima parola uscita
	 * @param word
	 * @return
	 */
	public void newGame(String word, int gameNumber) {
		WordleGame game = new WordleGame(word, this.username, gameNumber);

		if (this.games == null) {
			this.games = new ArrayList<>();
		}

		this.games.add(game);
	}

	/**
	 * Ritorna la media del numero di tenatativi fatti tra tutte le partite giocate
	 * @return
	 */
	public int averageAttempts() {
		int avg = 0;
		if (this.games == null) {
			return 0;
		}
		for (WordleGame game: this.games) {
			avg += game.attempts;
		}
		return avg / this.games.size();
	}

	/**
	 * Ritorna il numero di vittorie dell'utente
	 * @return
	 */
	private int wonGames() {
		int wonGames = 0;
		if (this.games == null) {
			return 0;
		}
		for (WordleGame game: this.games) {
			wonGames += game.won ? 1 : 0;
		}

		return  wonGames;
	}

	/**
	 * Ritorna il numero di tentativi fatti in tutte le partite vinte dall'utente
	 * @return
	 */
	private int attemptsWonGames() {
		int attempts = 0;
		for(WordleGame game: this.games) {
			if (game.finished && game.won) {
				attempts += game.attempts;
			}
		}

		return attempts;
	}

	/**
	 * Ritorna il numero di partite vinte con un certo numero di tentativi
	 * @param attempts
	 * @return
	 */
	private int wonGamesByAttempt(int attempts) {
		int count = 0;
		for (WordleGame game: this.games) {
			count = game.won && game.attempts == attempts ? count + 1 : count;
		}
		return count;
	}

	/**
	 * Calcola la distribuzione di probabilita' dei tentativi fatti dall'utente nelle partite vinte
	 * @return
	 */
	private GuessDistributionItem[] getGuessDistribution() {
		GuessDistributionItem[] guessDistribution = new GuessDistributionItem[ClientConfig.WORDLE_MAX_ATTEMPTS];
		int wonGames = wonGames();

		for(int i = 0; i < ClientConfig.WORDLE_MAX_ATTEMPTS; i++) {
			guessDistribution[i] = new GuessDistributionItem();
			guessDistribution[i].attemptNumber = i+1;
			guessDistribution[i].percentage = wonGamesByAttempt(i+1) * 100 / wonGames;
		}

		return guessDistribution;
	}

	/**
	 * Ritorna le statistiche dell'utente
	 * @return
	 */
	public UserStat getStat() {
		UserStat stat = new UserStat();
		stat.playedGames = games.size();
		stat.wonGamesPercentage = wonGames() * 100 / games.size();
		stat.avgAttemptsWonGames = (float) attemptsWonGames() / wonGames();
		stat.lastStreakWonGames = lastStreak;
		stat.bestStreakWonGames = bestStreak;
		stat.guessDistribution = this.getGuessDistribution();
		return stat;
	}

	/**
	 * Calcola il punteggio di un utente
	 * @return
	 */
	public int getScore() {
		int wonGames = this.wonGames();
		int avgAttempts = this.averageAttempts();

		return wonGames * avgAttempts;
	}

	/**
	 * Aggiunge un tentativo di indovinare la parola all'ultimo gioco dell'utente
	 */
	public synchronized void addGuessLastGame(String word) {
		WordleGame lastGame = getLastGame();
		lastGame.attempts++;
		lastGame.won = word.equals(lastGame.word);
		lastGame.finished = lastGame.getRemainingAttempts() == 0 || lastGame.won;

		// Se il gioco e' finito aggiorno le statistiche
		if(lastGame.finished) {
			this.lastStreak = lastGame.won ? this.lastStreak+1 : 0;
			this.bestStreak = Math.max(this.lastStreak, this.bestStreak);
		}
	}
}
