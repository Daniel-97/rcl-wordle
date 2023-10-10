package common.utils;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Properties;

public class ConfigReader {

	/**
	 * Legge il file di configurazione dal path specificato nella variabile di ambiente WORDLE_CONFIG
	 */
	public static Properties readConfig() {

		String configPath = System.getenv("WORDLE_CONFIG");
		if (configPath == null) {
			System.out.println("Variabile d'ambiente WORDLE_CONFIG non trovata!");
			System.exit(-1);
		}

		Properties properties = new Properties();
		try {
			FileInputStream file = new FileInputStream(configPath);
			properties.load(file);
		} catch (FileNotFoundException e) {
			System.out.println("File di configurazione " + configPath + " non trovato");
			System.exit(-1);
		} catch (IOException e) {
			System.out.println("Errore caricamento file di configurazione");
			System.exit(-1);
		}

		System.out.println("File di configurazione " + configPath + " letto correttamente!");
		return properties;
	}

	/**
	 * Legge una configurazione dall'oggetto properties
	 * @param propertyName
	 * @return
	 */
	public static String readProperty(Properties properties, String propertyName) throws NoSuchFieldException {
		String property = properties.getProperty(propertyName);
		if(property == null || property.length() == 0) {
			throw new NoSuchFieldException(propertyName);
		} else {
			return property;
		}
	}


}
