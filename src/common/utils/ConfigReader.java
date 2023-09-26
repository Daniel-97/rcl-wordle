package common.utils;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Properties;

public class ConfigReader {

	/**
	 * Legge il file di configurazione dal path indicato
	 * @param path
	 */
	public static Properties readConfig(String path) {

		System.out.println("Tentativo di leggere file di configurazione " + path + " in corso...");
		//System.out.println(Paths.get(".").toAbsolutePath());

		Properties properties = new Properties();
		try {
			FileInputStream file = new FileInputStream(path);
			properties.load(file);
		} catch (FileNotFoundException e) {
			System.out.println("File di configurazione " + path + " non trovato");
		} catch (IOException e) {
			System.out.println("Errore caricamento file di configurazione");
		}

		return properties;
	}
}
