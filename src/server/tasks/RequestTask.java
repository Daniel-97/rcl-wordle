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

			switch (request.command) {

				case LOGIN: {
					login(request);
					break;
				}

				case LOGOUT: {
					logout(request);
					break;
				}

				case PLAY_WORDLE: {
					playWordle(request);
					break;
				}

				case VERIFY_WORD: {
					verifyWord(request);
					break;
				}

				case STAT: {
					stat(request);
					break;
				}

				case SHARE: {
					share(request);
					break;
				}
			}

		}catch (IOException e) {
			System.out.println("Request task IO exception: "+e);
		}

	}

	private void login(TcpClientRequestDTO request) {

		if (request.arguments == null || request.arguments.length < 2) {
			key.attach(new TcpServerResponseDTO(BAD_REQUEST));
			return;
		}

		boolean success = this.userService.login(request.arguments[0], request.arguments[1]);
		key.attach(new TcpServerResponseDTO(success ? ResponseCodeEnum.OK : INVALID_USERNAME_PASSWORD));
	}

	private void logout(TcpClientRequestDTO request) {

		if (request.arguments == null || request.arguments.length < 1) {
			key.attach(new TcpServerResponseDTO(BAD_REQUEST));
			return;
		}

		boolean success = this.userService.logout(request.arguments[0]);
		key.attach(new TcpServerResponseDTO(success ? OK : INVALID_USERNAME));
	}

	private void playWordle(TcpClientRequestDTO request) {

		if (request.arguments == null || request.arguments.length < 1) {
			key.attach(new TcpServerResponseDTO(BAD_REQUEST));
			return;
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

		key.attach(response);
	}

	private void verifyWord(TcpClientRequestDTO request) {

		if (request.arguments == null || request.arguments.length < 1) {
			key.attach(new TcpServerResponseDTO(BAD_REQUEST));
			return;
		}

		String username = request.arguments[0];
		String clientWord = request.arguments[1];
		User user = this.userService.getUser(username);
		WordleGame lastGame = user.getLastGame();

		TcpServerResponseDTO res = new TcpServerResponseDTO();
		res.remainingAttempts = lastGame.getRemainingAttempts();

		// Ultimo gioco dell'utente e' diverso dalla parola attualmente estratta
		if (!lastGame.word.equals(wordleGameService.getGameWord())) {
			key.attach(new TcpServerResponseDTO(NEED_TO_START_GAME));
			return;
		}

		// Ultimo gioco dell'utente corrisponde alla parola attuale ed ha gia' completato il gioco
		else if (lastGame.finished) {
			key.attach(new TcpServerResponseDTO(GAME_ALREADY_PLAYED));
			return;
		}

		// Utente ha inviato parola di lunghezza errata
		else if (clientWord.length() > WordleGameService.WORD_LENGHT || clientWord.length() < WordleGameService.WORD_LENGHT) {
			res.code = INVALID_WORD_LENGHT;
			key.attach(res);
			return;
		}

		// Utente ha mandato parola che non si trova nel dizionario
		else if (!wordleGameService.isWordInDict(clientWord)) {
			res.code = WORD_NOT_IN_DICTIONARY;
			key.attach(res);
			return;
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
			if(wordleGameService.isRankChanged(oldRank, newRank)) {
				ServerMain.notifyRankToClient(userService.getRank());
			}
			return;
		}

		res.userGuess = lastGame.getGuess();
		key.attach(res);
	}

	private void stat(TcpClientRequestDTO request) {
		if (request.arguments == null || request.arguments.length < 1) {
			key.attach(new TcpServerResponseDTO(BAD_REQUEST));
			return;
		}

		String username = request.arguments[0];
		User user = this.userService.getUser(username);

		if (user == null) {
			key.attach(new TcpServerResponseDTO(INVALID_USERNAME));
			return;
		}

		UserStat stat = user.getStat();
		TcpServerResponseDTO response = new TcpServerResponseDTO();
		response.stat = stat;
		key.attach(response);
	}

	private void share(TcpClientRequestDTO request) throws IOException {
		if (request.arguments == null || request.arguments.length < 1) {
			key.attach(new TcpServerResponseDTO(BAD_REQUEST));
			return;
		}

		String username = request.arguments[0];
		User user = this.userService.getUser(username);

		if (user == null) {
			key.attach(new TcpServerResponseDTO(INVALID_USERNAME));
			return;
		}

		WordleGame lastGame = user.getLastGame();
		if (lastGame == null) {
			key.attach(new TcpServerResponseDTO(NO_GAME_TO_SHARE));
			return;
		}

		// Invio ultima partita dell'utente su gruppo multicast
		System.out.println("Invio ultima partita dell'utente " + username + " sul gruppo sociale...");
		ServerMain.sendMulticastMessage(JsonService.toJson(lastGame));
		key.attach(new TcpServerResponseDTO(OK));
	}

}
