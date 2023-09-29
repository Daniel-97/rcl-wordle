package server.services;

import server.entity.User;
import common.enums.ResponseCodeEnum;
import server.exceptions.WordleException;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.lang.reflect.Type;
import java.util.ArrayList;

public class UserService {
	private static final String USERS_DATA_PATH = "persistance/users.json";
	private ArrayList<User> users = new ArrayList<>();
	public UserService() {
		System.out.println("Avvio servizio utenti...");
		this.loadUsers();
	}

	/**
	 * Carica gli utenti dal file user.json se esiste. Se non esiste lo crea
	 */
	private void loadUsers() {
		Type ListOfUserType = new TypeToken<ArrayList<User>>(){}.getType();
		try {
			this.users = (ArrayList<User>) JsonService.readJson(USERS_DATA_PATH, ListOfUserType);
			System.out.println("Caricato/i correttamente " + this.users.size() + " utente/i da file json");
		} catch (IOException e) {
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
	 * @throws WordleException
	 */
	public void addUser(User user) throws WordleException {

		// Controlla se esiste gia' un utente con lo stesso username
		for(User u:this.users) {
			if (u.getUsername().equals(user.getUsername())) {
				throw new WordleException(ResponseCodeEnum.USERNAME_ALREADY_USED);
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
	public boolean login(String username, String password) {

		User user = getUser(username);
		if (user == null) {
			return false;
		}

		if(user.verifyPassword(password)) {
			user.online = true;
			return true;
		} else {
			return false;
		}
	}

	/**
	 * Logout the current user
	 * @param username
	 * @return
	 */
	public boolean logout(String username) {
		User user = getUser(username);
		if(user == null) {
			return false;
		} else {
			user.online = false;
			return true;
		}
	}

	/**
	 * Ordina la lista di utenti in base alla media di tentativi per indovinare una parola
	 */
	public void sortUsers() {
		this.users.sort((o1, o2) -> o1.averageAttempts() - o2.averageAttempts());
	}
}
