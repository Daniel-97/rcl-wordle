package server.tasks;

import common.dto.MyMemoryResponse;
import common.enums.AnsiColor;
import common.utils.WordleLogger;
import server.entity.ServerConfig;
import server.entity.WordleGameState;
import server.services.JsonService;
import server.services.WordleGameService;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

public class WordExtractorTask implements Runnable {
	private final WordleLogger logger = new WordleLogger(WordExtractorTask.class.getName());
	private final WordleGameService wordleGameService = WordleGameService.getInstance();

	@Override
	public void run() {

		WordleGameState state = wordleGameService.getState();

		try {
			// Mantengo la lock sulla parola fino a che non ne ho estratta una nuova e ho salvato la traduzione
			WordleGameService.wordLock.lock();

			state.word = wordleGameService.extractRandomWord();
			state.translation = wordleGameService.translateWord(state.word);
			logger.info("Parola scaduta, nuova parola estratta: " + AnsiColor.WHITE_BOLD + state.word + AnsiColor.RESET + ", traduzione: " + state.translation);
			state.extractedAt = new Date();
			state.gameNumber++;

		} catch (Exception e){
			// In caso di eccezione, ripristino la vecchia parola, in modo da far proseguire il gioco e non generare errori
			// per possibile inconsistenza dei dati
			wordleGameService.setState(state);
			logger.error("Errore estrazione nuova parola, ripristinata vecchia parola!" + e);
		}
		finally {
			WordleGameService.wordLock.unlock();
		}

	}
}
