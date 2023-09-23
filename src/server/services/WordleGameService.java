package server.services;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Date;
import java.util.Objects;
import java.util.Random;

public class WordleGameService {

	private String actualWord; // Parola attuale del gioco
	private Date extractedAt; // Indica quando e' stata estratta l'ultima parola
	//Dizionario delle parole, non deve essere salvato sul json
	private final transient ArrayList<String> dictionary = new ArrayList<>();

	public WordleGameService() {

		System.out.println("Avvio servizio wordle game...");
		// Carico il dizionario delle parole in memoria
		// TODO possibile ottimizzazione? Caricarare solo la parola che serve a runtime
		Path dictionaryPath = Paths.get("src/dictionary/words.txt");
		try (
				BufferedReader br = new BufferedReader(Files.newBufferedReader(dictionaryPath));
		) {
			String line = br.readLine();
			while (line != null) {
				this.dictionary.add(line);
				line = br.readLine();
			}
		} catch (IOException e) {
			System.out.println("Impossibile leggere dizionario parole. " + e.getMessage());
			throw new RuntimeException(e);
		}

		System.out.println("Caricato dizionario di " + this.dictionary.size() + " parole");

		/*  Carico file wordle.json che contiene le configurazioni dell ultimo gioco
			Permette di mantenere lo stato del server in caso di riavvio o crash
		 */
		try {
			WordleGameService game = (WordleGameService) JsonService.readJson("persistance/wordle.json", WordleGameService.class);
			this.actualWord = game.actualWord;
			this.extractedAt = game.extractedAt;
		}catch (IOException e) {
			System.out.println("Errore lettura wordle.json, reset gioco");
			this.extractedAt = null;
		}

		// Devo estrarre una nuova parola
		if (this.extractedAt == null) {
			this.extractWord();
		}

	}

	public void saveSettings() {
	}
	/**
	 * Estrae in modo casuale una parola dal dizionario
	 * @return
	 */
	public String extractWord() {
		String word = this.dictionary.get(new Random().nextInt(this.dictionary.size()));

		// Estrai una nuova parola se e' identica a quella precedente
		if (this.actualWord != null && this.actualWord.equals(word)) {
			return this.extractWord();
		}

		this.actualWord = word;
		System.out.println("Nuova parola estratta: " + word);

		return word;
	}

	public String translateWord(String word) {
		// TODO chiamare https://mymemory.translated.net/doc/spec.php
		return word;
	}
}
