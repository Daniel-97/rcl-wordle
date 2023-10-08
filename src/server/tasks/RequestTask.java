package server.tasks;

import common.dto.*;
import common.entity.WordleGame;
import common.enums.ResponseCodeEnum;
import server.ServerMain;
import server.entity.User;
import server.services.JsonService;
import server.services.UserService;
import server.services.WordleGameService;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.List;

import static common.enums.ResponseCodeEnum.*;

public class RequestTask implements Runnable {
	private final SelectionKey key;
	private final TcpClientRequestDTO request;
	private final UserService userService = UserService.getInstance();
	private final WordleGameService wordleGameService = WordleGameService.getInstance();

	public RequestTask(SelectionKey key, TcpClientRequestDTO request) {
		this.key = key;
		this.request = request;
	}

	@Override
	public void run() {
		try {
			SocketChannel client = (SocketChannel) key.channel();
			SocketAddress clientAddress = client.getRemoteAddress();
			System.out.println("["+Thread.currentThread().getName()+"] Gestisco richiesta da client " + clientAddress +": "+request);

			if (request == null || request.command == null) {
				key.attach(new TcpServerResponseDTO(INTERNAL_SERVER_ERROR));
				return;
			}

			TcpServerResponseDTO response;
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
					response = new TcpServerResponseDTO(BAD_REQUEST);

			}

			// Copio la risposta nell'allegato della chiave. Verrà usato da Server.main per rispondere al client
			key.attach(response);

		}catch (IOException e) {
			System.out.println("Request task IO exception: "+e);
		}

	}

	private TcpServerResponseDTO login(TcpClientRequestDTO request) {

		if (request.arguments == null || request.arguments.length < 2) {
			return new TcpServerResponseDTO(BAD_REQUEST);
		}

		boolean success = this.userService.login(request.arguments[0], request.arguments[1]);
		return new TcpServerResponseDTO(success ? ResponseCodeEnum.OK : INVALID_USERNAME_PASSWORD);
	}

	private TcpServerResponseDTO logout(TcpClientRequestDTO request) {

		if (request.arguments == null || request.arguments.length < 1) {
			return new TcpServerResponseDTO(BAD_REQUEST);
		}

		boolean success = this.userService.logout(request.arguments[0]);
		return new TcpServerResponseDTO(success ? OK : INVALID_USERNAME);
	}

	private TcpServerResponseDTO playWordle(TcpClientRequestDTO request) {

		if (request.arguments == null || request.arguments.length < 1) {
			return new TcpServerResponseDTO(BAD_REQUEST);
		}

		User user = this.userService.getUser(request.arguments[0]);
		WordleGame lastGame = user.getLastGame();
		TcpServerResponseDTO response = new TcpServerResponseDTO();

		// TODO migliorare questo codice
		// Aggiunto gioco al giocatore attuale
		if (lastGame == null || !lastGame.word.equals(wordleGameService.getGameWord())) {
			user.newGame(wordleGameService.getGameWord(), wordleGameService.getGameNumber());
			response.code = OK;
			response.remainingAttempts = user.getLastGame().getRemainingAttempts();
			response.userGuess = user.getLastGame().getGuess();
		} else if (lastGame.word.equals(wordleGameService.getGameWord()) && !lastGame.finished) {
			response.code = OK;
			response.remainingAttempts = user.getLastGame().getRemainingAttempts();
			response.userGuess = user.getLastGame().getGuess();
		} else {
			response.code = GAME_ALREADY_PLAYED;
		}

		return response;
	}

	private TcpServerResponseDTO verifyWord(TcpClientRequestDTO request) {

		if (request.arguments == null || request.arguments.length < 1) {
			return new TcpServerResponseDTO(BAD_REQUEST);
		}

		String username = request.arguments[0];
		String clientWord = request.arguments[1];
		User user = this.userService.getUser(username);
		WordleGame lastGame = user.getLastGame();

		TcpServerResponseDTO res = new TcpServerResponseDTO();
		res.remainingAttempts = lastGame.getRemainingAttempts();

		// Ultimo gioco dell'utente e' diverso dalla parola attualmente estratta
		if (!lastGame.word.equals(wordleGameService.getGameWord())) {
			return new TcpServerResponseDTO(NEED_TO_START_GAME);
		}

		// Ultimo gioco dell'utente corrisponde alla parola attuale ed ha gia' completato il gioco
		else if (lastGame.finished) {
			return new TcpServerResponseDTO(GAME_ALREADY_PLAYED);
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

		// Aggiungo il tentativo effettuato dall'utente
		LetterDTO[] guess = wordleGameService.hintWord(clientWord);
		lastGame.addGuess(guess);

		List<UserScore> oldRank = userService.getRank();
		// Aggiorno lo status del gioco
		userService.updateRank();
		lastGame.won = clientWord.equals(lastGame.word);
		lastGame.finished = lastGame.getRemainingAttempts() == 0 || lastGame.won;
		List<UserScore> newRank = userService.getRank();

		// Se la partita e' finita lo comunico al client
		if (lastGame.finished) {
			res.code = lastGame.won ? GAME_WON : GAME_LOST;
			res.wordTranslation = wordleGameService.getWordTranslation();
			key.attach(res);
			//TODO sistemare non funziona
			if(wordleGameService.isRankChanged(oldRank, newRank)) {
				ServerMain.notifyRankToClient(userService.getRank());
			}
			return res;
		}

		res.userGuess = lastGame.getGuess();
		return res;
	}

	private TcpServerResponseDTO stat(TcpClientRequestDTO request) {

		if (request.arguments == null || request.arguments.length < 1) {
			return new TcpServerResponseDTO(BAD_REQUEST);
		}

		String username = request.arguments[0];
		User user = this.userService.getUser(username);

		if (user == null) {
			return new TcpServerResponseDTO(INVALID_USERNAME);
		}

		UserStat stat = user.getStat();
		TcpServerResponseDTO response = new TcpServerResponseDTO();
		response.stat = stat;
		return response;
	}

	private TcpServerResponseDTO share(TcpClientRequestDTO request) throws IOException {

		if (request.arguments == null || request.arguments.length < 1) {
			return new TcpServerResponseDTO(BAD_REQUEST);
		}

		String username = request.arguments[0];
		User user = this.userService.getUser(username);

		if (user == null) {
			return new TcpServerResponseDTO(INVALID_USERNAME);
		}

		WordleGame lastGame = user.getLastGame();
		if (lastGame == null) {
			return new TcpServerResponseDTO(NO_GAME_TO_SHARE);
		}

		// Invio ultima partita dell'utente su gruppo multicast
		System.out.println("Invio ultima partita dell'utente " + username + " sul gruppo sociale...");
		ServerMain.sendMulticastMessage(JsonService.toJson(lastGame));
		return new TcpServerResponseDTO(OK);
	}

}
