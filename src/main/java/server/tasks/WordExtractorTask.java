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
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

public class WordExtractorTask implements Runnable {
	private final WordleLogger logger = new WordleLogger(WordExtractorTask.class.getName());
	private final WordleGameService wordleGameService = WordleGameService.getInstance();

	@Override
	public void run() {
		logger.debug("Avvio WordExtractor task...");

		while (!Thread.currentThread().isInterrupted()) {

			try {
				// Provo a prendere la lock su word
				WordleGameService.wordLock.lock();

				WordleGameState state = wordleGameService.getState();
				Calendar cal = GregorianCalendar.getInstance();
				cal.setTime(state.extractedAt);
				cal.add(GregorianCalendar.MINUTE, ServerConfig.WORD_TIME_MINUTES);

				if (cal.getTime().getTime() < new Date().getTime()) {
					state.word = wordleGameService.extractRandomWord();
					state.translation = wordleGameService.translateWord(state.word);
					logger.info("Nuova parola estratta, vecchia scaduta: " + state.word + ", traduzione: " + state.translation);
					state.extractedAt = new Date();
					state.gameNumber++;

				} else {
					logger.debug("Parola sempre valida!");
				}

			} finally {
				WordleGameService.wordLock.unlock();
			}

			// Addormento il thread per un numero di minuti pari alla durata di vita della parola
			try {
				// Busy waiting
				Thread.sleep((long) 1000 * 60 * ServerConfig.WORD_TIME_MINUTES);
			} catch (InterruptedException ignored) {}
		}

		logger.debug("Interruzione WordExtractorTask");

	}
}
