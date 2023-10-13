package common.utils;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Properties;

public class ConfigReader {
	private static WordleLogger logger = new WordleLogger(ConfigReader.class.getName());

	/**
	 * Legge il file di configurazione dal path specificato nella variabile di ambiente WORDLE_CONFIG
	 */
	public static Properties readConfig() {

		String configPath = System.getenv("WORDLE_CONFIG");
		if (configPath == null) {
			logger.error("Variabile d'ambiente WORDLE_CONFIG non trovata!");
			System.exit(-1);
		}

		Properties properties = new Properties();
		try {
			FileInputStream file = new FileInputStream(configPath);
			properties.load(file);
		} catch (FileNotFoundException e) {
			logger.error("File di configurazione " + configPath + " non trovato");
			System.exit(-1);
		} catch (IOException e) {
			logger.error("Errore caricamento file di configurazione");
			System.exit(-1);
		}

		logger.debug("File di configurazione " + configPath + " letto correttamente!");
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
