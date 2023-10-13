package client.services;

import client.entity.CLICommand;
import client.entity.ClientConfig;
import client.enums.UserCommandEnum;
import common.dto.GuessDistributionItem;
import common.dto.LetterDTO;
import common.dto.UserScore;
import common.dto.UserStat;
import common.entity.WordleGame;

import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

public class CLIHelper {
	public static final String RESET = "\033[0m";
	public static final String YELLOW_BACKGROUND = "\033[43m";
	public static final String GRAY_BACKGROUND = "\033[100m";
	public static final String GREEN_BACKGROUND = "\033[42m";
	public static final String BLACK = "\033[0;30m";
	public static final String BLACK_BOLD = "\033[1;30m";
	public static final String GREEN = "\033[0;32m";
	public static final String YELLOW = "\033[0;33m";
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

	public static void printCursor(String username) {
		System.out.print(username != null ? (username + "@wordle-client>") : "guest@wordle-client>");
	}

	/**
	 * Funzione che fa il paring dell input e ritorna il comando con i relativi argomenti
	 * @return
	 * @throws IllegalArgumentException
	 */
	public static CLICommand waitForInput(String username, boolean parseCommand) {
		String input;
		// Continuo a ciclare fino a che non ottengo un input valido
		do{
			printCursor(username);
			input = cliScanner.nextLine();
		}
		while (input == null || input.isEmpty() || input.equals("\n"));

		String[] cmdSplit = input.split(" ", -1);
		UserCommandEnum cmd = null;
		if(parseCommand) {
			try {
				cmd = UserCommandEnum.valueOf(cmdSplit[0].replace(":", "").toUpperCase());
			} catch (IllegalArgumentException ignored) {}
		}
		String[] args = Arrays.copyOfRange(cmdSplit, cmd != null ? 1 : 0, cmdSplit.length);
		return new CLICommand(cmd, args);
	}

	public static void printServerWord(LetterDTO[][] userGuess, boolean printLetter) {

		if (userGuess == null || userGuess.length == 0) {
			return;
		}
		System.out.println();
		System.out.format("+-----------------------------------------+%n");
		System.out.format("+                 WORDLE                  +%n");
		System.out.format("+-----------------------------------------+%n");
		System.out.format("+                                         +%n");

		for(LetterDTO[] guess: userGuess) {

			if (guess == null) {
				continue;
			}

			String letterColor;
			System.out.print("+");
			for (LetterDTO letter : guess) {
				String color;
				switch (letter.guessStatus) {
					case '+':
						color = GREEN_BACKGROUND;
						letterColor = printLetter ? BLACK : GREEN;
						break;
					case '?':
						color = YELLOW_BACKGROUND;
						letterColor = printLetter ? BLACK : YELLOW;
						break;
					default:
						color = GRAY_BACKGROUND;
						letterColor = printLetter ? BLACK : BLACK_BOLD;
				}
				System.out.print("|"+ letterColor + color + " " + Character.toUpperCase(letter.letter) + " " + RESET);
			}
			System.out.println("|+");
			System.out.println("+                                         +");
		}
		System.out.println("+-----------------------------------------+\n");

	}

	public static void printUserStats(UserStat stat) {

		if (stat == null) {
			System.out.println("Nessuna statistica presente!");
			return;
		}

		System.out.println("Ecco le tue statistiche:");
		System.out.println("- Partite giocate: "+stat.playedGames);
		System.out.println("- Percentuale partite vinte: "+stat.wonGamesPercentage+"%");
		System.out.println("- Media tentativi partite vinte: "+stat.avgAttemptsWonGames);
		System.out.println("- Ultima serie partite vinte di fila: "+stat.lastStreakWonGames);
		System.out.println("- Migliore serie partite vinte di fila: "+stat.bestStreakWonGames);

		if (stat.guessDistribution != null) {
			System.out.println("Distribuzione probabilita' tentativi su partite vinte:");
			for(GuessDistributionItem item: stat.guessDistribution) {
				if(item.percentage > 0)
					System.out.println("- "+item.attemptNumber+" tentativo/i: "+item.percentage+"%");
			}
		}
	}

	public static void printRank(List<UserScore> rank) {

		if (rank == null || rank.size() == 0){
			System.out.println("Nessuna classifica presente al momento!");
			return;
		}

		//System.out.println("CLASSIFICA GIOCATORI");
		System.out.format("+----------+---------------+-------+%n");
		System.out.format("+       CLASSIFICA DI GIOCO        +%n");
		System.out.format("+----------+---------------+-------+%n");
		System.out.format("| Position | Username      | Score |%n");
		System.out.format("+----------+---------------+-------+%n");
		for(int i = 0; i < rank.size(); i++) {
			System.out.format("| %-8d | %-13s | %-5d |%n", (i+1), rank.get(i).username, rank.get(i).score);
		}
		System.out.format("+----------+---------------+-------+%n");
	}

	/**
	 * Mostra a video tutte le partite giocate dai vari giocatori
	 * @param games
	 */
	public static void printUsersGames(List<WordleGame> games) {
		System.out.println("Elenco partite condivise dagli altri giocatori\n");
		for(int i = 0; i < games.size(); i++) {
			WordleGame game = games.get(i);
				System.out.print((i+1) + ") Wordle "+game.gameNumber+": " +
						game.attempts + "/" + ClientConfig.WORDLE_MAX_ATTEMPTS + " ("+game.username+")");
				printServerWord(game.getUserHint(), false);
		}
	}

}
