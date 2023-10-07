package server.tasks;

import common.dto.LetterDTO;
import common.dto.TcpClientRequestDTO;
import common.dto.TcpServerResponseDTO;
import common.dto.UserStat;
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
import static common.enums.ResponseCodeEnum.*;

public class CommandTask implements Runnable {
	private final SelectionKey key;
	private final TcpClientRequestDTO request;
	private final UserService userService = UserService.getInstance();
	private final WordleGameService wordleGameService = WordleGameService.getInstance();

	public CommandTask(SelectionKey key, TcpClientRequestDTO request) {
		this.key = key;
		this.request = request;
	}

	@Override
	public void run() {
		try {
			SocketChannel client = (SocketChannel) key.channel();
			SocketAddress clientAddress = client.getRemoteAddress();

			switch (request.command) {

				case LOGIN: {
					// TODO controllare se gli argomenti ci sono o meno
					boolean success = this.userService.login(request.arguments[0], request.arguments[1]);
					key.attach(new TcpServerResponseDTO(ResponseCodeEnum.OK));
					//ServerMain.sendTcpMessage(client, new TcpServerResponseDTO(ResponseCodeEnum.OK));
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
						key.attach(new TcpServerResponseDTO(INVALID_WORD_LENGHT));
						break;
					}

					// Utente ha mandato parola che non si trova nel dizionario
					else if (!wordleGameService.isWordInDict(clientWord)) {
						key.attach(new TcpServerResponseDTO(WORD_NOT_IN_DICTIONARY));
						break;
					}

					// Aggiungo il tentativo effettuato dall'utente
					LetterDTO[] guess = wordleGameService.hintWord(clientWord);
					lastGame.addGuess(guess);
					System.out.println("Aggiunto guess per parola " + clientWord + " dell'utente " + username);
					// Aggiorno lo status del gioco
					lastGame.won = clientWord.equals(lastGame.word);
					lastGame.finished = lastGame.getRemainingAttempts() == 0 || lastGame.won;

					// Se la partita e' finita lo comunico al client
					if (lastGame.finished) {
						TcpServerResponseDTO res = new TcpServerResponseDTO();
						res.code = lastGame.won ? GAME_WON : GAME_LOST;
						res.wordTranslation = wordleGameService.getWordTranslation();
						key.attach(res);
						// TODO notificare questo cambiamento solo se ci sono aggiornamenti nei primi 3 posti della classifica
						ServerMain.notifyRankToClient(userService.getRank());
						break;
					}

					TcpServerResponseDTO res = new TcpServerResponseDTO();
					res.remainingAttempts = lastGame.getRemainingAttempts();
					res.userGuess = lastGame.getGuess();
					key.attach(res);
					break;
				}

				case STAT: {
					String username = request.arguments[0];
					User user = this.userService.getUser(username);
					// Todo controllare che utente esista davvero
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
					ServerMain.sendMulticastMessage(JsonService.toJson(game));
					key.attach(new TcpServerResponseDTO(OK));
					break;
				}
			}

		}catch (IOException e) {

		}

	}
}
