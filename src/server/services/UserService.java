package server.services;

import server.User;
import server.enums.ErrorCodeEnum;
import server.exceptions.WordleException;

import java.util.ArrayList;

public class UserService {
	private final ArrayList<User> users;

	public UserService() {
		System.out.println("Avvio servizio utenti...");
		// Todo caricare utenti da file json per persistance
		users = new ArrayList<>();
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

		//Todo salvare utente su file
	}
}
