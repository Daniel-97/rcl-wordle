package server.services;

import common.dto.LetterDTO;
import common.dto.MyMemoryResponse;
import common.dto.UserScore;
import common.utils.WordleLogger;
import server.entity.WordleGameState;
import server.tasks.WordExtractorTask;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class WordleGameService {
	private static final WordleLogger logger = new WordleLogger(WordleGameService.class.getName());
	public static final Lock wordLock = new ReentrantLock();
	private static final String WORDLE_STATE_PATH = "data/wordle.json";
	private static final String DICTIONARY_PATH = "src/main/java/dictionary/words.txt";
	private static WordleGameService instance = null;
	public static final int WORD_LENGHT = 10;
	private WordleGameState state; // Contiene lo stato attuale del gioco
	private final ArrayList<String> dictionary = new ArrayList<>(); //Dizionario delle parole, non deve essere salvato sul json


	public synchronized static WordleGameService getInstance() {
		if(instance == null) {
			instance = new WordleGameService();
		}
		return instance;
	}

	private WordleGameService() {

		logger.info("Avvio servizio wordle game...");
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
			logger.error("Impossibile leggere dizionario parole. " + e.getMessage());
			throw new RuntimeException(e);
		}

		logger.info("Caricato dizionario di " + this.dictionary.size() + " parole");

		/*  Carico file wordle.json che contiene le configurazioni dell ultimo gioco
			Permette di mantenere lo stato del server in caso di riavvio o crash
		 */
		try {
			this.state = (WordleGameState) JsonService.readJson(WORDLE_STATE_PATH, WordleGameState.class);
		}catch (IOException e) {
			String word = this.extractRandomWord();
			this.state = new WordleGameState(word, this.translateWord(word));
			logger.info("Primo avvio del gioco, nuova parola estratta: " + state.word + ", traduzione: " + state.translation);
		}

	}

	public void saveState() {

		try {
			JsonService.writeJson(WORDLE_STATE_PATH, this.state);
		} catch (IOException e) {
			logger.error("Errore! Impossibile salvare il corrente stato del gioco!");
		}
	}

	/**
	 * Estrae in modo casuale una parola dal dizionario
	 * @return
	 */
	public String extractRandomWord() {
		return this.dictionary.get(new Random().nextInt(this.dictionary.size()));
	}

	/**
	 * Ritorna la parola attuale del gioco, prima tenta di prendere la lock, altrimenti significa che un altro
	 * thread ci sta lavorando sopra (get oppure aggiornamento)
	 * @return
	 */
	public String getGameWord() {
		//this.updateWord();
		String word;

		try {
			wordLock.lock();
			word = this.state.word;
		} finally {
			wordLock.unlock();
		}

		return word;
	}

	public int getGameNumber() {
		return this.state.gameNumber;
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
			char correctLetter = this.state.word.toCharArray()[i];

			result[i] = new LetterDTO();
			result[i].letter = word.toCharArray()[i];
			if (guessedLetter == correctLetter) {
				result[i].guessStatus = '+';
			} else if (this.state.word.contains(String.valueOf(guessedLetter))) {
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

	public String getWordTranslation() {
		return this.state.translation;
	}

	/**
	 * Ritorna true se ci sono differenze nelle prime tre posizioni tra la vecchia e la nuova classifica
	 * @param oldRank
	 * @param newRank
	 * @return
	 */
	public boolean isRankChanged(List<UserScore> oldRank, List<UserScore> newRank) {

		for(int i = 0; i < 3; i++) {
			UserScore oldScore = oldRank.size() < (i+1) ? null : oldRank.get(i);
			UserScore newScore = newRank.size() < (i+1) ? null : newRank.get(i);
			if (
				(oldScore != null && newScore == null) ||
				(oldScore == null && newScore != null) ||
				(oldScore != null && newScore != null && newScore.score != oldScore.score)
			) {
				logger.info("Classifica utenti cambiata nei primi 3 posti! Trasmetto aggiornamento ai client");
				return true;
			}
		}

		return false;
	}

	/**
	 * Questo metodo contatta le API di mymemory per tradurre una parola da inglese a italiano
	 * @param word
	 * @return
	 */
	public String translateWord(String word) {

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
			logger.error("Errore durante traduzione parola "+word+": "+e);
		}

		return null;
	}

	public WordleGameState getState() {
		return state;
	}

	public void setState(WordleGameState state) {
		this.state = state;
	}
}
