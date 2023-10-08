package server.services;

import common.dto.LetterDTO;
import common.dto.MyMemoryResponse;
import common.dto.UserScore;
import server.entity.WordleGameState;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class WordleGameService {

	private static WordleGameService instance = null;
	private static final String WORDLE_STATE_PATH = "data/wordle.json";
	private static final String DICTIONARY_PATH = "src/dictionary/words.txt";
	public static final int WORD_LENGHT = 10;
	private WordleGameState state; // Contiene lo stato attuale del gioco
	private String wordTranslation; // Traduzione della parola in italiano
	private int wordExpireTimeMinutes;
	private final ArrayList<String> dictionary = new ArrayList<>(); //Dizionario delle parole, non deve essere salvato sul json
	private final ArrayList<UserScore> ranking = new ArrayList<>(); // Contiene la classifica degli utenti


	public synchronized static WordleGameService getInstance() {
		if(instance == null) {
			instance = new WordleGameService();
		}
		return instance;
	}
	public synchronized void init(int wordExpireMinutes) {
		this.wordExpireTimeMinutes = wordExpireMinutes;
	}

	private WordleGameService() {

		System.out.println("Avvio servizio wordle game...");

		// Carico il dizionario delle parole in memoria
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

		this.updateWord();
		System.out.println("Parola del giorno: " + this.state.actualWord + ", traduzione: "+wordTranslation);

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
		wordTranslation = this.translateWord(this.state.actualWord);
		System.out.println("Parola scaduta! Nuova parola estratta: " + word + ", traduzione: "+wordTranslation);

		return word;
	}

	/**
	 * Ritorna la parola attuale del gioco. Prima di farlo controlla se la parola deve essere aggiornata
	 * @return
	 */
	public String getGameWord(){
		this.updateWord();
		return this.state.actualWord;
	}

	/**
	 * Ritorna i suggerimenti per la parola passata sotto fomra di array di oggetti
	 * @param word
	 * @return
	 */
	public LetterDTO[] hintWord(String word) {

		LetterDTO[] result = new LetterDTO[word.length()];

		for (int i = 0; i < word.length(); i++) {
			char guessedLetter = word.toCharArray()[i];
			char correctLetter = this.state.actualWord.toCharArray()[i];

			result[i] = new LetterDTO();
			result[i].letter = word.toCharArray()[i];
			if (guessedLetter == correctLetter) {
				result[i].guessStatus = '+';
			} else if (this.state.actualWord.contains(String.valueOf(guessedLetter))) {
				result[i].guessStatus = '?';
			} else {
				result[i].guessStatus = 'X';
			}
		}

		return result;

	}

	/**
	 * Questa funzione controlla se la parola specificata e' presente nel dizionario delle parole
	 * @param word
	 * @return
	 */
	public boolean isWordInDict(String word) {
		for (int i = 0; i < this.dictionary.size(); i++) {
			if (this.dictionary.get(i).equals(word))
				return true;
		}
		return false;
	}

	/**
	 * Questo metodo contatta le API di mymemory per tradurre una parola da inglese a italiano
	 * @param word
	 * @return
	 */
	private String translateWord(String word) {

		try {
			URL url = new URL("https://api.mymemory.translated.net/get?q="+word+"&langpair=en|it");
			BufferedReader in = new BufferedReader(new InputStreamReader(url.openStream()));
			String inputLine;
			StringBuilder json = new StringBuilder();

			while ((inputLine = in.readLine()) != null) {
				json.append(inputLine);
			}

			MyMemoryResponse response = JsonService.fromJson(json.toString(), MyMemoryResponse.class);
			if(response != null && response.responseData != null && response.responseData.translatedText != null) {
				return response.responseData.translatedText;
			} else {
				return null;
			}

		} catch (IOException e) {
			System.out.println("Errore durante traduzione parola "+word+": "+e.getMessage());
		}

		return null;
	}

	/**
	 * Controlla se la parola attuale e' scaduta e nel caso aggiorna
	 * @return
	 */
	private synchronized void updateWord() {

		if (this.state.actualWord == null) {
			this.extractWord();
		} else {
			Calendar cal = GregorianCalendar.getInstance();
			cal.setTime(this.state.extractedAt);
			cal.add(GregorianCalendar.MINUTE, wordExpireTimeMinutes);

			if (cal.getTime().getTime() < new Date().getTime()) {
				this.extractWord();
			}
		}
	}

	public String getWordTranslation() {
		return wordTranslation;
	}
}
