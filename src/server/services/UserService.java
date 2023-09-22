package server.services;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonReader;
import server.User;
import server.enums.ErrorCodeEnum;
import server.exceptions.WordleException;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;

public class UserService {
	private static final String USERS_DATA_PATH = "persistence/users.json";
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

		Path usersPath = Paths.get(USERS_DATA_PATH);
		Gson gson = new GsonBuilder().setPrettyPrinting().create();

		if (Files.exists(usersPath)) {
			// Se il file esiste lo vado a leggere un po alla volta
			try (BufferedReader br = Files.newBufferedReader(usersPath)) {
				Type ListOfUserType = new TypeToken<ArrayList<User>>(){}.getType();
				this.users = gson.fromJson(br, ListOfUserType);
			} catch (FileNotFoundException e) {
				throw new RuntimeException(e);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}

			System.out.println("Caricati correttamente " + this.users.size() + " utenti da file json");
		}
	}

	public void saveUsers() {

		Path usersPath = Paths.get(USERS_DATA_PATH);
		//TODO migliorare questo try
		if (!Files.exists(usersPath)) {
			System.out.println(USERS_DATA_PATH + " file non esiste, lo creo");
			try {
				System.out.println(Paths.get(".").toAbsolutePath());
				Files.createDirectory(usersPath.getParent());
				Files.createFile(usersPath);
			} catch (IOException e) {
				System.out.println("IO exception: " + e);
			}
		}

		// Scrivo il file
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		try (BufferedWriter bw = Files.newBufferedWriter(usersPath)){
			System.out.println("Salvataggio di users.json in corso");
			bw.write(gson.toJson(this.users));
		}catch (IOException e) {
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

		//Todo salvare utente su file
	}
}
