package server;

import java.util.Date;
import java.util.Objects;

public class User {

	private final String username;
	private final String passwordHash; // password hahsata
	private final Date registeredAt; // Data registrazione utente

	public User(String username, String password) {
		this.username = username;
		this.passwordHash = this.hashPassword(password);
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
}
