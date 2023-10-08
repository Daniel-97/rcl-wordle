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
					// TODO controllare se gli argomenti ci sono o meno
					boolean success = this.userService.login(request.arguments[0], request.arguments[1]);
					key.attach(new TcpServerResponseDTO(success ? ResponseCodeEnum.OK : INVALID_USERNAME_PASSWORD));
					break;
				}

				case LOGOUT: {
					boolean success = this.userService.logout(request.arguments[0]);
					key.attach(new TcpServerResponseDTO(success ? OK : INVALID_USERNAME));
					break;
				}

				case PLAY_WORDLE: {
					User user = this.userService.getUser(request.arguments[0]);
					WordleGame lastGame = user.getLastGame();
					TcpServerResponseDTO response = new TcpServerResponseDTO();

					// TODO migliorare questo codice
					// Aggiunto gioco al giocatore attuale
					if (lastGame == null || !lastGame.word.equals(wordleGameService.getGameWord())) {
						user.newGame(wordleGameService.getGameWord());
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
					break;
				}

				case VERIFY_WORD: {
					String username = request.arguments[0];
					String clientWord = request.arguments[1];
					User user = this.userService.getUser(username);
					WordleGame lastGame = user.getLastGame();

					TcpServerResponseDTO res = new TcpServerResponseDTO();
					res.remainingAttempts = lastGame.getRemainingAttempts();

					// Ultimo gioco dell'utente e' diverso dalla parola attualmente estratta
					if (!lastGame.word.equals(wordleGameService.getGameWord())) {
						key.attach(new TcpServerResponseDTO(NEED_TO_START_GAME));
						break;
					}

					// Ultimo gioco dell'utente corrisponde alla parola attuale ed ha gia' completato il gioco
					else if (lastGame.finished) {
						key.attach(new TcpServerResponseDTO(GAME_ALREADY_PLAYED));
						break;
					}

					// Utente ha inviato parola di lunghezza errata
					else if (clientWord.length() > WordleGameService.WORD_LENGHT || clientWord.length() < WordleGameService.WORD_LENGHT) {
						res.code = INVALID_WORD_LENGHT;
						key.attach(res);
						break;
					}

					// Utente ha mandato parola che non si trova nel dizionario
					else if (!wordleGameService.isWordInDict(clientWord)) {
						res.code = WORD_NOT_IN_DICTIONARY;
						key.attach(res);
						break;
					}

					// Aggiungo il tentativo effettuato dall'utente
					LetterDTO[] guess = wordleGameService.hintWord(clientWord);
					lastGame.addGuess(guess);

					List<UserScore> oldRank = userService.getRank();
					// Aggiorno lo status del gioco
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
						break;
					}

					res.userGuess = lastGame.getGuess();
					key.attach(res);
					break;
				}

				case STAT: {
					String username = request.arguments[0];
					User user = this.userService.getUser(username);

					if (user == null) {
						key.attach(new TcpServerResponseDTO(INVALID_USERNAME));
						break;
					}

					UserStat stat = user.getStat();
					TcpServerResponseDTO response = new TcpServerResponseDTO();
					response.stat = stat;
					key.attach(response);
					break;
				}

				case SHARE: {
					String username = request.arguments[0];
					User user = this.userService.getUser(username);

					if (user == null) {
						key.attach(new TcpServerResponseDTO(INVALID_USERNAME));
						break;
					}

					WordleGame lastGame = user.getLastGame();
					if (lastGame == null) {
						key.attach(new TcpServerResponseDTO(NO_GAME_TO_SHARE));
						break;
					}

					// Invio ultima partita dell'utente su gruppo multicast
					System.out.println("Invio ultima partita dell'utente " + username + " sul gruppo sociale...");
					// Invio solamente le informazioni che mi interessano non tutto l'oggetto
					WordleGame game = new WordleGame();
					game.attempts = lastGame.attempts;
					game.startedAt = lastGame.startedAt;
					game.won = lastGame.won;
					game.username = lastGame.username;
					ServerMain.sendMulticastMessage(JsonService.toJson(game));
					key.attach(new TcpServerResponseDTO(OK));
					break;
				}
			}

		}catch (IOException e) {
			System.out.println("Request task IO exception: "+e);
		}

	}
}
