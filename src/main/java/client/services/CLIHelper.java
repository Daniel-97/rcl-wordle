package client.services;

import client.entity.CLICommand;
import client.entity.ClientConfig;
import client.enums.UserCommandEnum;
import common.dto.GuessDistributionItem;
import common.dto.LetterDTO;
import common.dto.UserScore;
import common.dto.UserStat;
import common.entity.SharedGame;
import common.enums.AnsiColor;

import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

import static common.enums.AnsiColor.*;

public class CLIHelper {
	private static final Scanner cliScanner = new Scanner(System.in);

	public static void entryMenu() {
		System.out.format("⦁ "+WHITE_BOLD+"login    <username> <password>"+RESET+" -> Effettua il login %n");
		System.out.format("⦁ "+WHITE_BOLD+"register <username> <password>"+RESET+" -> Condividi ultima partita %n");
		System.out.format("⦁ "+WHITE_BOLD+"help"+RESET+"                           -> Aiuto %n");
		System.out.format("⦁ "+WHITE_BOLD+"quit"+RESET+"                           -> Esci da Wordle %n%n");
	}

	public static void mainMenu() {
		System.out.format("⦁ "+WHITE_BOLD+"play"+RESET+"   -> Gioca a Wordle %n");
		System.out.format("⦁ "+WHITE_BOLD+"share"+RESET+"  -> Condividi ultima partita %n");
		System.out.format("⦁ "+WHITE_BOLD+"social"+RESET+" -> Mostra condivisioni utenti %n");
		System.out.format("⦁ "+WHITE_BOLD+"stat"+RESET+"   -> Mostra le statistiche personali %n");
		System.out.format("⦁ "+WHITE_BOLD+"rank"+RESET+"   -> Mostra la classifica di gioco %n");
		System.out.format("⦁ "+WHITE_BOLD+"logout"+RESET+" -> Logout da Wordle %n");
		System.out.format("⦁ "+WHITE_BOLD+"quit"+RESET+"   -> Esci da Wordle %n");
		System.out.format("⦁ "+WHITE_BOLD+"help"+RESET+"   -> Aiuto %n%n");
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
				cmd = UserCommandEnum.valueOf(cmdSplit[0].toUpperCase());
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
						color = AnsiColor.GREEN_BACKGROUND;
						letterColor = printLetter ? AnsiColor.BLACK : AnsiColor.GREEN;
						break;
					case '?':
						color = AnsiColor.YELLOW_BACKGROUND;
						letterColor = printLetter ? AnsiColor.BLACK : AnsiColor.YELLOW;
						break;
					default:
						color = AnsiColor.GRAY_BACKGROUND;
						letterColor = printLetter ? AnsiColor.BLACK : AnsiColor.BLACK_BOLD;
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
		System.out.format("+-----------------------------------------+%n");
		System.out.format("+               STATISTICHE               +%n");
		System.out.format("+-----------------------------------------+%n");
		System.out.format("| - Partite giocate: %-20d |%n", stat.playedGames);
		System.out.format("| - Partite vinte:   %-20s |%n", stat.wonGamesPercentage + "%");
		System.out.format("| - Media tentativi: %-20f |%n", stat.avgAttemptsWonGames);
		System.out.format("| - Ultima serie:    %-20d |%n", stat.lastStreakWonGames);
		System.out.format("| - Migliore serie:  %-20d |%n", stat.bestStreakWonGames);
		System.out.format("+-----------------------------------------+%n");

		if (stat.guessDistribution == null) {
			return;
		}

		System.out.format("+        DISTRIBUZIONE PROBABILITA'       +%n");
		System.out.format("+----------------------+------------------+%n");
		System.out.format("| N. tentativo         |   Percentuale    |%n");
		System.out.format("+----------------------+------------------+%n");
		for(GuessDistributionItem item: stat.guessDistribution) {
			System.out.format("|          %-2d          |       %-3d%%       |%n", item.attemptNumber, item.percentage);
			System.out.format("+----------------------+------------------+%n");
		}
	}

	public static void printRank(List<UserScore> rank) {

		if (rank == null || rank.size() == 0){
			System.out.println("Nessuna classifica presente al momento!");
			return;
		}

		//System.out.println("CLASSIFICA GIOCATORI");
		System.out.format("+----------------------------------+%n");
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
	public static void printUsersGames(List<SharedGame> games) {

		if (games == null || games.size() == 0) {
			System.out.println("Nessuna condivisione presente");
			return;
		}

		for(int i = 0; i < games.size(); i++) {
			SharedGame game = games.get(i);
				System.out.print((i+1) + ") Wordle "+game.gameNumber+": " +
						game.hints.length + "/" + ClientConfig.WORDLE_MAX_ATTEMPTS + " ("+game.username+")");
				printServerWord(game.hints, false);
				System.out.println();
		}
	}

}
