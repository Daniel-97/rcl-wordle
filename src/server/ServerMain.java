package server;

import server.interfaces.ServerRMI;

import java.rmi.server.RemoteObject;

public class ServerMain extends RemoteObject implements ServerRMI {
	public static void main(String[] argv){
		ServerMain server = new ServerMain();

	}
}