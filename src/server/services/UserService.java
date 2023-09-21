package server.services;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import server.User;
import server.enums.ErrorCodeEnum;
import server.exceptions.WordleException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;

public class UserService {
	private static final String USERS_DATA_PATH = "persistence/users.json";
	private final ArrayList<User> users;
	public UserService() {
		System.out.println("Avvio servizio utenti...");

		Path usersPath = Paths.get(USERS_DATA_PATH);

		//TODO migliorare questo try
		if(!Files.exists(usersPath)){
			System.out.println("User data path not exits, creating one");
			try {
				System.out.println(Paths.get(".").toAbsolutePath());
				Files.createDirectory(usersPath.getParent());
				Files.createFile(usersPath);
			} catch (IOException e) {
				System.out.println("IO exception: " + e);
			}
		}

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

	public String toJson() {
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		return gson.toJson(this);
	}
}
