package server.services;

import server.entity.WordleGameState;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Date;
import java.util.Random;

public class WordleGameService {

	private static final String WORDLE_STATE_PATH = "persistance/wordle.json";
	private static final String DICTIONARY_PATH = "src/dictionary/words.txt";
	private WordleGameState state; // Contiene lo stato attuale del gioco
	//Dizionario delle parole, non deve essere salvato sul json
	private final transient ArrayList<String> dictionary = new ArrayList<>();

	public WordleGameService() {

		System.out.println("Avvio servizio wordle game...");
		// Carico il dizionario delle parole in memoria
		// TODO possibile ottimizzazione? Caricarare solo la parola che serve a runtime
		Path dictionaryPath = Paths.get(DICTIONARY_PATH);
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
			this.state = (WordleGameState) JsonService.readJson(WORDLE_STATE_PATH, WordleGameState.class);
		}catch (IOException e) {
			System.out.println("Errore lettura wordle.json, reset gioco");
			this.state = new WordleGameState();
		}

		// Devo estrarre una nuova parola
		//TODO controlare anche se la parola e' scaduta
		if (this.state.actualWord == null) {
			this.extractWord();
		}

	}

	public void saveState() {

		try {
			JsonService.writeJson(WORDLE_STATE_PATH, this.state);
		} catch (IOException e) {
			System.out.println("Errore! Impossibile salvare il corrente stato del gioco!");
		}
	}

	/**
	 * Estrae in modo casuale una parola dal dizionario
	 * @return
	 */
	public String extractWord() {
		String word = this.dictionary.get(new Random().nextInt(this.dictionary.size()));

		// Estrai una nuova parola se e' identica a quella precedente
		if (this.state.actualWord != null && this.state.actualWord.equals(word)) {
			return this.extractWord();
		}

		this.state.actualWord = word;
		this.state.extractedAt = new Date();
		System.out.println("Nuova parola estratta: " + word);

		return word;
	}

	public String translateWord(String word) {
		// TODO chiamare https://mymemory.translated.net/doc/spec.php
		return word;
	}
}
