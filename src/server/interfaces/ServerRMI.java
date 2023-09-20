package server.interfaces;

import server.exceptions.WordleException;

import java.rmi.Remote;

public interface ServerRMI extends Remote {

	int register(String username, String password) throws WordleException;
}
