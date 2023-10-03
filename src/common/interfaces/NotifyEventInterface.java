package common.interfaces;

import common.dto.UserScore;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

public interface NotifyEventInterface extends Remote {


	/**
	 * Notifica gli utenti quando avviene un cambiamento nella classifica del gioco
	 * @throws RemoteException
	 */
	void notifyUsersRank(List<UserScore> newRank) throws RemoteException;

}
