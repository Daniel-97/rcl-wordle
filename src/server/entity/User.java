package server.entity;

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
	private final String passwordHash; // password hahsata
	private String salt; // Password salt
	private final Date registeredAt; // Data registrazione utente
	private List<WordleGame> games;
	// Todo tenere aggiornate queste statistiche
	private int lastStreak = 0;
	private int bestStreak = 0;
	public transient boolean online = false; // Indica se utente e' online oppure no, da non salvare sul json

	public User(String username, String password) throws InvalidKeySpecException, NoSuchAlgorithmException {
		this.username = username;
		this.registeredAt = new Date();
		this.salt = generateRandomSalt();
		this.passwordHash = hashPassword(password, Base64.getDecoder().decode(this.salt));
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
		return this.passwordHash.equals(hashPassword(password, Base64.getDecoder().decode(this.salt)));
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
	 * Aggiunge una nuova partita per l utente
	 * @param word
	 * @return
	 */
	public void newGame(String word) {
		WordleGame lastGame = getLastGame();
		WordleGame game = null;
		if (lastGame == null) {
			game = new WordleGame(word, 0);
		} else {
			game = new WordleGame(word, lastGame.id+1);
		}

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
	public int wonGames() {
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
	public int getScore() {
		int wonGames = this.wonGames();
		int avgAttempts = this.averageAttempts();

		return wonGames * avgAttempts;
	}
}
