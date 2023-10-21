package server.tasks;

import common.dto.*;
import common.entity.SharedGame;
import common.entity.WordleGame;
import common.enums.ResponseCodeEnum;
import common.utils.WordleLogger;
import server.ServerMain;
import server.entity.User;
import server.services.JsonService;
import server.services.UserService;
import server.services.WordleGameService;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

import static common.enums.ResponseCodeEnum.*;

public class RequestTask implements Runnable {
	private final static WordleLogger logger = new WordleLogger(RequestTask.class.getName());
	private final SelectionKey key;
	private final TcpRequest request;
	private final SocketChannel client;
	private final UserService userService = UserService.getInstance();
	private final WordleGameService wordleGameService = WordleGameService.getInstance();

	public RequestTask(SelectionKey key, TcpRequest request) {
		this.key = key;
		this.request = request;
		this.client = (SocketChannel) key.channel();
	}

	/**
	 * Metodo principale task, eseguito dalla threadpool. Si limita a fare uno switch sul comando ricevuto dall'utente
	 * e a smistarlo alla corretta funzione. La risposta viene salvata nell'attachment della SelectionKey.
	 */
	@Override
	public void run() {

		TcpResponse response;
		try {
			SocketAddress clientAddress = client.getRemoteAddress();
			logger.debug("Gestisco richiesta da client " + clientAddress +": "+request.command);

			if (request.command == null) {
				key.attach(new TcpResponse(BAD_REQUEST));
				return;
			}

			// Gestisco la richiesta
			switch (request.command) {

				case LOGIN:
					response = login(request);
					break;

				case LOGOUT:
					response = logout(request);
					break;

				case PLAY_WORDLE:
					response = playWordle(request);
					break;

				case VERIFY_WORD:
					response = verifyWord(request);
					break;

				case STAT:
					response = stat(request);
					break;

				case SHARE:
					response = share(request);
					break;

				default:
					response = new TcpResponse(BAD_REQUEST);

			}

		} catch (Exception e) {
			// Se casco qui dentro il thread ha incontrato un errore inaspettato durante la gestione della richiesta
			// Devo ritornare internal server error altrimenti il client rimane in attesa all'infinito.
			// Essendo il task Runnable non propaga le eccezioni al chiamante
			logger.error("Request task IO exception: "+e);
			e.printStackTrace();
			response = new TcpResponse(INTERNAL_SERVER_ERROR);
		}
		// Copio la risposta nell'allegato della chiave. Verrà usato da Server.main per rispondere al client (NIO)
		key.attach(response);
	}

	/**
	 * Metodo per effettuare login dell'utente.
	 * @param request
	 * @return
	 * @throws IOException
	 */
	private synchronized TcpResponse login(TcpRequest request) throws IOException {

		if (request.arguments == null || request.arguments.length < 2) {
			return new TcpResponse(BAD_REQUEST);
		}

		User user = this.userService.getUser(request.arguments[0]);

		if (user == null) {
			return new TcpResponse(INVALID_USERNAME_PASSWORD);
		}

		if (user.online) {
			return new TcpResponse(ALREADY_LOGGED_IN);
		}

		// Mi memorizzo hash code di indirizzo ip:porta del client, mi permette di fare logout di utente quando effettua una disconnessione forzata
		int clientHashCode = this.client.getRemoteAddress().hashCode();
		boolean success = this.userService.login(request.arguments[0], request.arguments[1], clientHashCode);
		return new TcpResponse(success ? ResponseCodeEnum.OK : INVALID_USERNAME_PASSWORD);
	}

	/**
	 * Permette di effettuare il logout di un utente
	 * @param request
	 * @return
	 */
	private synchronized TcpResponse logout(TcpRequest request) {

		if (request.arguments == null || request.arguments.length < 1) {
			return new TcpResponse(BAD_REQUEST);
		}

		boolean success = this.userService.logout(request.arguments[0]);
		return new TcpResponse(success ? OK : INVALID_USERNAME);
	}

	/**
	 * Verifica se l'utente puo' giocare alla parola attualmente estratta. In caso contrario ritorna codici di errore
	 * specifici.
	 * @param request
	 * @return
	 */
	private TcpResponse playWordle(TcpRequest request) {

		if (request.arguments == null || request.arguments.length < 1) {
			return new TcpResponse(BAD_REQUEST);
		}

		User user = this.userService.getUser(request.arguments[0]);
		WordleGame lastGame = user.getLastGame();
		TcpResponse response = new TcpResponse();
		// Prendo la parola attuale in modo sicuro
		String actualWord = wordleGameService.getGameWord();

		// Aggiunto gioco al giocatore attuale
		if (lastGame == null || !lastGame.word.equals(actualWord)) {
			user.newGame(actualWord, wordleGameService.getGameNumber());
			response.code = OK;
			response.remainingAttempts = user.getLastGame().getRemainingAttempts();
			response.userGuess = wordleGameService.buildUserHint(user.getLastGame().getUserGuess(), actualWord);
		} else if (lastGame.word.equals(actualWord) && !lastGame.finished) {
			response.code = OK;
			response.remainingAttempts = user.getLastGame().getRemainingAttempts();
			response.userGuess = wordleGameService.buildUserHint(user.getLastGame().getUserGuess(), actualWord);
		} else {
			response.code = GAME_ALREADY_PLAYED;
		}

		return response;
	}

	/**
	 * Metodo principale del gioco, verifica una nuova parola inviata dal client
	 * @param request
	 * @return
	 */
	private TcpResponse verifyWord(TcpRequest request) {

		if (request.arguments == null || request.arguments.length < 1) {
			return new TcpResponse(BAD_REQUEST);
		}

		String username = request.arguments[0];
		String clientWord = request.arguments[1];
		User user = this.userService.getUser(username);
		WordleGame lastGame = user.getLastGame();
		// prendo la parola attuale in modo sicuro
		String actualWord = wordleGameService.getGameWord();
		TcpResponse res = new TcpResponse();
		res.remainingAttempts = lastGame.getRemainingAttempts();

		// Ultimo gioco dell'utente e' diverso dalla parola attualmente estratta
		if (!lastGame.word.equals(actualWord)) {
			// Se ultimo gioco non e' finito, e la parola e' cambiata allora lo elimino.
			if (!lastGame.finished) {
				user.removeLastGame();
			}
			return new TcpResponse(NEED_TO_START_GAME);
		}

		// Ultimo gioco dell'utente corrisponde alla parola attuale ed ha gia' completato il gioco
		else if (lastGame.finished) {
			return new TcpResponse(GAME_ALREADY_PLAYED);
		}

		// Utente ha inviato parola di lunghezza errata
		else if (clientWord.length() > WordleGameService.WORD_LENGHT || clientWord.length() < WordleGameService.WORD_LENGHT) {
			res.code = INVALID_WORD_LENGHT;
			return res;
		}

		// Utente ha mandato parola che non si trova nel dizionario
		else if (!wordleGameService.isWordInDict(clientWord)) {
			res.code = WORD_NOT_IN_DICTIONARY;
			return res;
		}

		List<UserScore> oldRank = userService.getRank();
		user.addGuessLastGame(clientWord);
		userService.updateRank();
		List<UserScore> newRank = userService.getRank();

		// Se la partita e' finita lo comunico al client
		if (lastGame.finished) {
			res.code = lastGame.won ? GAME_WON : GAME_LOST;
			res.wordTranslation = wordleGameService.getWordTranslation();
			if (wordleGameService.isRankChanged(oldRank, newRank)) {
				ServerMain.notifyRankToClient(userService.getRank());
			}
		}

		res.remainingAttempts = lastGame.getRemainingAttempts();
		res.userGuess = wordleGameService.buildUserHint(lastGame.getUserGuess(), actualWord);

		return res;
	}

	/**
	 * Ritorna le statistiche dell'utente
	 * @param request
	 * @return
	 */
	private TcpResponse stat(TcpRequest request) {

		if (request.arguments == null || request.arguments.length < 1) {
			return new TcpResponse(BAD_REQUEST);
		}

		String username = request.arguments[0];
		User user = this.userService.getUser(username);

		if (user == null) {
			return new TcpResponse(INVALID_USERNAME);
		}

		UserStat stat = user.getStat();
		TcpResponse response = new TcpResponse();
		response.stat = stat;
		return response;
	}

	/**
	 * Condivide l'ultimo gioco completato dell'utente sul gruppo sociale (multicast)
	 * @param request
	 * @return TcpResponse
	 * @throws IOException
	 */
	private TcpResponse share(TcpRequest request) throws IOException {

		if (request.arguments == null || request.arguments.length < 1) {
			return new TcpResponse(BAD_REQUEST);
		}

		String username = request.arguments[0];
		User user = this.userService.getUser(username);

		if (user == null) {
			return new TcpResponse(INVALID_USERNAME);
		}

		WordleGame lastGame = user.getLastGame();
		if (lastGame == null) {
			return new TcpResponse(NO_GAME_TO_SHARE);
		}

		// Invio ultima partita dell'utente su gruppo multicast
		logger.debug("Invio ultima partita dell'utente " + username + " sul gruppo sociale. word: "+lastGame.word + ",wordle n."+lastGame.gameNumber);
		System.out.println(lastGame.getUserGuess().toString());
		SharedGame share = new SharedGame(username, lastGame.gameNumber, wordleGameService.buildUserHint(lastGame.getUserGuess(), lastGame.word));
		ServerMain.sendMulticastMessage(JsonService.toJson(share));
		return new TcpResponse(OK);
	}

}
