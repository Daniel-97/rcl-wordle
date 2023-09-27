package client.services;

import java.util.Scanner;

public class CLIHelper {

	private static final Scanner cliScanner = new Scanner(System.in);
	private static  final String ENTRY_MENU =
			"Necessario login prima di giocare a Wordle. Opzioni disponibili:\n"+
			"- :login	 <username> <password>	-> login\n"+
			"- :register <username>	<password>	-> registrati a Wordle\n"+
			"- :help							-> aiuto\n"+
			"- :quit							-> esci da Wordle\n";

	private static final String MAIN_MENU =
			"Opzioni disponibili:\n"+
			"- :play		-> gioca a wordle\n"+
			"- :share		-> condividi i risultati sul gruppo sociale\n"+
			"- :stat		-> aggiorna le statistiche dell'uente\n"+
			"- :logout		-> logout da Wordle\n"+
			"- :help		-> aiuto\n";

	public static void entryMenu() {
		System.out.println(ENTRY_MENU);
		printCursor();
	}
	public static void mainMenu() {
		System.out.println(MAIN_MENU);
		printCursor();
	}

	public static void cls() {
		System.out.println("Premere un tasto per continuare...");
		cliScanner.nextLine();
		for (int i = 0; i<= 100; i++) {
			System.out.println();
		}
	}
	private static void printCursor() {
		System.out.print("WORDLE-CLIENT>");
	}

	public static String[] parseInput() {
		String input = cliScanner.nextLine();
		return input.split(" ", -1);
	}

}
