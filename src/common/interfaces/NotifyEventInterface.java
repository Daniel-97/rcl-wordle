package common.interfaces;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface NotifyEventInterface extends Remote {


	/**
	 * Notifica gli utenti quando avviene un cambiamento nella classifica del gioco
	 * @throws RemoteException
	 */
	void notifyUsersRank() throws RemoteException;

}
