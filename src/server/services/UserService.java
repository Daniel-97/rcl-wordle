package server.services;

import server.entity.User;
import server.enums.ErrorCodeEnum;
import server.exceptions.WordleException;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.lang.reflect.Type;
import java.util.ArrayList;

public class UserService {
	private static final String USERS_DATA_PATH = "persistance/users.json";
	private ArrayList<User> users;
	public UserService() {
		System.out.println("Avvio servizio utenti...");
		users = new ArrayList<>();
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
				throw new WordleException(ErrorCodeEnum.USERNAME_ALREADY_USED);
			}
		}

		// Se sono arrivato qui l'utente non esiste, posso aggiungerlo
		this.users.add(user);
	}

	/**
	 * Ordina la lista di utenti in base alla media di tentativi per indovinare una parola
	 */
	public void sortUsers() {
		this.users.sort((o1, o2) -> o1.averageAttempts() - o2.averageAttempts());
	}
}
