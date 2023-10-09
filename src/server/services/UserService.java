package server.services;

import com.google.gson.JsonSyntaxException;
import common.dto.UserScore;
import server.entity.User;
import common.enums.ResponseCodeEnum;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.lang.reflect.Type;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class UserService {
	private static UserService instance = null;
	private static final String USERS_DATA_PATH = "data/users.json";
	private List<User> users = new ArrayList<>();
	private List<UserScore> rank;

	private UserService() {
		System.out.println("Avvio servizio utenti...");
		this.loadUsers();
		this.updateRank();
	}

	/**
	 * Singleton, necessario perche' piu thread utilizzano questo metodo, si evitano istante inutili
	 * @return
	 */
	public static synchronized UserService getInstance() {
		if (instance == null) {
			instance = new UserService();
		}
		return instance;
	}

	/**
	 * Carica gli utenti dal file user.json se esiste. Se non esiste lo crea
	 */
	private void loadUsers() {
		Type ListOfUserType = new TypeToken<List<User>>(){}.getType();
		try {
			this.users = (List<User>) JsonService.readJson(USERS_DATA_PATH, ListOfUserType);
			System.out.println("Caricato/i correttamente " + this.users.size() + " utente/i da file json");
		} catch (IOException | JsonSyntaxException e) {
			System.out.println("Errore lettura file user.json, creazione nuovo array");
			this.users = new ArrayList<>();
		}
	}

	public void saveUsers() {

		try {
			JsonService.writeJson(USERS_DATA_PATH, this.users);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Aggiunge un nuovo utente
	 * @param user
	 * @throws IllegalArgumentException
	 */
	public synchronized void addUser(User user) throws IllegalArgumentException {

		// Controlla se esiste gia' un utente con lo stesso username
		for(User u:this.users) {
			if (u.getUsername().equals(user.getUsername())) {
				throw new IllegalArgumentException(ResponseCodeEnum.USERNAME_ALREADY_USED.name());
			}
		}

		// Se sono arrivato qui l'utente non esiste, posso aggiungerlo
		this.users.add(user);
		System.out.println("Nuovo utente aggiunto! "+user.getUsername());
	}

	/**
	 * Cerca l'utente con lo username specificato
	 * @param username
	 * @return
	 */
	public User getUser(String username) {
		for(User user: this.users) {
			if (user.getUsername().equals(username)) {
				return user;
			}
		}
		return null;
	}

	/**
	 * Effettua il login di un utente
	 * @param username
	 * @param password
	 * @return
	 */
	public synchronized boolean login(String username, String password) {

		User user = getUser(username);
		if (user == null) {
			return false;
		}

		try {
			return user.verifyPassword(password);
		} catch (InvalidKeySpecException | NoSuchAlgorithmException e) {
			System.out.println("Errore verifica password: "+e.getMessage());
			return false;
		}
	}

	/**
	 * Logout the current user
	 * @param username
	 * @return
	 */
	public synchronized boolean logout(String username) {
		User user = getUser(username);
		// La disiscrizione dell utente dalle notifiche di rank viene fatta client side con RMI callback
		return user != null;
	}

	/**
	 * Aggiorna la classifica di gioco
	 * @return
	 */
	public void updateRank() {

		List<UserScore> rank = new ArrayList<>();
		for(User user: this.users) {
			rank.add(new UserScore(user.getUsername(), user.getScore()));
		}

		rank.sort(Comparator.comparing(UserScore::getScore));
		this.rank = rank;
	}

	/**
	 * Ritorna una copia della lista che contiene la classifica degli utenti
	 * @return
	 */
	public List<UserScore> getRank() {
		return new ArrayList<>(this.rank);
	}

	/**
	 * Ordina la lista di utenti in base alla media di tentativi per indovinare una parola
	 */
	public void sortUsers() {
		this.users.sort((o1, o2) -> o1.averageAttempts() - o2.averageAttempts());
	}
}
