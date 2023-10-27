package server.services;

import com.google.gson.JsonSyntaxException;
import common.dto.LetterDTO;
import common.dto.MyMemoryResponse;
import common.dto.UserScore;
import common.enums.AnsiColor;
import common.utils.WordleLogger;
import server.entity.ServerConfig;
import server.entity.WordleGameState;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.TimeoutException;
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
		}catch (IOException | JsonSyntaxException e) {
			logger.warn("Impossibile leggere file wordle.json, resetto WordleGameService " + e);
			//String word = this.extractRandomWord();
			this.state = new WordleGameState();
		}

		//logger.info("Parola attuale: " + AnsiColor.WHITE_BOLD + state.word + AnsiColor.RESET + ", traduzione: " + state.translation);

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
	 * Ritorna i suggerimenti per la parola passata rispetto a right word sotto forma di array di oggetti
	 * @param word
	 * @return
	 */
	public LetterDTO[] hintWord(String word, String rightWord) {

		LetterDTO[] result = new LetterDTO[word.length()];

		for (int i = 0; i < word.length(); i++) {
			char guessedLetter = word.toCharArray()[i];
			char correctLetter = rightWord.toCharArray()[i];

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
	 * Costruisce l'array dei suggerimenti a partire dai tentativi dell'utente
	 * @param userGuess
	 * @return
	 */
	public LetterDTO[][] buildUserHint(List<String> userGuess, String rightWord) {
		LetterDTO[][] hints = new LetterDTO[userGuess.size()][];
		for(int i = 0; i < userGuess.size(); i++) {
			hints[i] = this.hintWord(userGuess.get(i), rightWord);
		}
		return hints;
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

		final int CONNECTION_TIMEOUT = 1000 * 5;
		try {
			URL url = new URL("https://api.mymemory.translated.net/get?q="+word+"&langpair=en|it");
			URLConnection connection = url.openConnection();
			connection.setConnectTimeout(CONNECTION_TIMEOUT);
			BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
			String inputLine;
			StringBuilder json = new StringBuilder();

			while ((inputLine = in.readLine()) != null) {
				json.append(inputLine);
			}

			MyMemoryResponse response = JsonService.fromJson(json.toString(), MyMemoryResponse.class);
			if (response != null && response.responseData != null && response.responseData.translatedText != null) {
				return response.responseData.translatedText;
			}

		} catch (SocketTimeoutException e) {
			logger.warn("Timeout raggiunto per traduzione parola!");
		}
		catch (IOException e) {
			logger.error("Errore durante traduzione parola "+word+": "+e);
		}

		// Arrivato a questo punto ci sono stati degli errori durante la traduzione della parola, best effort
		// ritorno la parola richiesta invece che null
		return word;
	}

	public WordleGameState getState() {
		return state;
	}

	public void setState(WordleGameState state) {
		this.state = state;
	}

	/**
	 * Ritorna i minuti che rimangono alla parola prima della
	 * @return
	 */
	public long getWordRemainingMinutes() {

		if(state.extractedAt == null) {
			return 0;
		}

		long diff = new Date().getTime() - state.extractedAt.getTime();
		long minutes = diff / 1000 / 60;

		// Parola estratta nel corrente minuto
		if (minutes == 0) {
			return ServerConfig.WORD_TIME_MINUTES;
		}
		// Parola scaduta
		else if (minutes > ServerConfig.WORD_TIME_MINUTES) {
			return 0;
		}
		// Parola non ancora scaduta
		else {
			return minutes;
		}

	}
}
