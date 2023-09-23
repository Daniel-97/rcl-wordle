package server;

import java.util.Date;

public class User {

	private final String username;
	private final String passwordHash; // password hahsata
	private final Date registeredAt; // Data registrazione utente
	private int gamesPlayed; // Numero di partite giocate
	private int gamesWon; // Numero di partite vinte
	private int averageAttempt;
	public User(String username, String password) {
		this.username = username;
		this.passwordHash = this.hashPassword(password);
		this.gamesPlayed = 0;
		this.gamesWon = 0;
		this.averageAttempt = 0;
		this.registeredAt = new Date();
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

	public int getAverageAttempt() {
		return averageAttempt;
	}
}
