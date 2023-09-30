package common.utils;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Properties;

public class ConfigReader {

	/**
	 * Legge il file di configurazione dal path specificato nella variabile di ambiente WORDLE_CONFIG
	 * @param path
	 */
	public static Properties readConfig() {

		String configPath = System.getenv("WORDLE_CONFIG");
		if (configPath == null) {
			System.out.println("Variabile d'ambiente WORDLE_CONFIG non trovata!");
			System.exit(-1);
		}

		System.out.println("Tentativo di leggere file di configurazione " + configPath + " in corso...");

		Properties properties = new Properties();
		try {
			FileInputStream file = new FileInputStream(configPath);
			properties.load(file);
		} catch (FileNotFoundException e) {
			System.out.println("File di configurazione " + configPath + " non trovato");
		} catch (IOException e) {
			System.out.println("Errore caricamento file di configurazione");
		}

		return properties;
	}
}
