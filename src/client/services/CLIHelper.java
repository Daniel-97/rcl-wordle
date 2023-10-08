package client.services;

import client.enums.UserCommand;
import common.dto.LetterDTO;
import common.dto.UserScore;
import common.dto.UserStat;
import common.entity.WordleGame;
import javafx.util.Pair;

import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

public class CLIHelper {

	private static final Scanner cliScanner = new Scanner(System.in);
	private static  final String ENTRY_MENU =
			"ENTRY MENU:\n"+
			"- :login <username> <password>      -> login\n"+
			"- :register <username>	<password>  -> registrati a Wordle\n"+
			"- :help                             -> aiuto\n"+
			"- :quit                             -> esci da Wordle\n";

	private static final String MAIN_MENU =
			"MAIN MENU:\n"+
			"- :play    -> gioca a wordle\n"+
			"- :share   -> condividi i risultati sul gruppo sociale\n"+
			"- :social  -> mostra i risultati pubblicati sul gruppo sociale\n"+
			"- :stat    -> mostra le tue statistiche\n"+
			"- :rank    -> mostra la classifica di gioco\n" +
			"- :logout  -> logout da Wordle\n"+
			"- :quit    -> esci da Wordle\n"+
			"- :help    -> aiuto\n";

	public static void entryMenu() {
		System.out.println(ENTRY_MENU);
	}
	public static void mainMenu() {
		System.out.println(MAIN_MENU);
	}


	public static void pause() {
		System.out.println("Premere un tasto per continuare...");
		cliScanner.nextLine();
		cls();
	}

	public static void cls() {
		for (int i = 0; i<= 100; i++) {
			System.out.println();
		}
	}

	public static void printCursor() {
		System.out.print("WORDLE-CLIENT>");
	}

	/**
	 * Funzione che fa il paring dell input e ritorna il comando con i relativi argomenti
	 * @return
	 * @throws IllegalArgumentException
	 */
	public static Pair<UserCommand, String[]> waitForCommand() {
		String input;
		// Continuo a ciclare fino a che non ottengo un input valido
		do{
			printCursor();
			input = cliScanner.nextLine();
		}
		while (input == null || input.isEmpty() || input.equals("\n"));

		String[] cmdSplit = input.split(" ", -1);
		UserCommand cmd = null;
		try {
			cmd = UserCommand.valueOf(cmdSplit[0].replace(":","").toUpperCase());
		} catch (IllegalArgumentException ignored) {}
		String[] args = Arrays.copyOfRange(cmdSplit, cmd != null ? 1 : 0, cmdSplit.length);

		return new Pair<>(cmd, args);
	}

	public static void printServerWord(LetterDTO[][] userGuess) {

		System.out.println("--------------------");
		for(LetterDTO[] guess: userGuess) {
			if (guess == null) {
				continue;
			}

			for (LetterDTO letter : guess) {
				System.out.print(Character.toUpperCase(letter.letter) + " ");
			}
			System.out.println();
			for (LetterDTO letter : guess) {
				System.out.print(letter.guessStatus + " ");
			}
			System.out.println("\n--------------------");
		}

	}

	public static void printUserStats(UserStat stat) {
		System.out.println("Ecco le tue statistiche:");
		System.out.println("- Partite giocate: "+stat.playedGames);
		System.out.println("- Percentuale partite vinte: "+stat.wonGamesPercentage+"%");
		System.out.println("- Ultima serie partite vinte di fila: "+stat.lastStreakWonGames);
		System.out.println("- Migliore serie partite vinte di fila: "+stat.bestStreakWonGames);
	}

	public static void printRank(List<UserScore> rank) {
		if (rank == null || rank.size() == 0){
			System.out.println("Nessuna classifica presente al momento!");
			return;
		}

		System.out.println("CLASSIFICA GIOCATORI");
		for(int i = 0; i < rank.size(); i++) {
			System.out.println((i+1) + ")	username: "+rank.get(i).username + ", score: " + rank.get(i).score);
		}
	}

	/**
	 * Mostra a video tutte le partite giocate dai vari gioactori
	 * @param games
	 */
	public static void printUsersGames(List<WordleGame> games) {
		System.out.println("Elenco partite condivise dagli altri giocatori:");
		for(int i = 0; i < games.size(); i++) {
			WordleGame game = games.get(i);
			System.out.print((i+1) + ") utente "+game.username+" inizio: " + game.startedAt.toString());
			System.out.println(", vittoria: "+(game.won?"si":"no")+", tentativi: " + game.attempts);
		}
	}

}
