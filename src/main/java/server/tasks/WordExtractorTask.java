package server.tasks;

import common.dto.MyMemoryResponse;
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

		logger.debug("Estraggo una nuova parola...");

		try {
			// Provo a prendere la lock su word
			WordleGameService.wordLock.lock();

			WordleGameState state = wordleGameService.getState();
			logger.debug("Vecchia parola estratta a " + state.extractedAt);

			state.word = wordleGameService.extractRandomWord();
			state.translation = wordleGameService.translateWord(state.word);
			logger.info("Nuova parola estratta, vecchia scaduta: " + state.word + ", traduzione: " + state.translation);
			state.extractedAt = new Date();
			state.gameNumber++;

		} catch (Exception e){
			logger.error(e.toString());
		}
		finally {
			WordleGameService.wordLock.unlock();
		}

	}
}
