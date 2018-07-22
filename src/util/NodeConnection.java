package util;

public class NodeConnection {
	
	public static final int PEER_TYPE = 0;
	public static final int ORDERER_TYPE = 1;
	
	public String host;
	public String name;
	public int port;
	public int eventHubPort;
	public String tlsCrtPath;
	public int type;
	
	public NodeConnection(int type, String name, String host, int port, int eventHubPort, String tlsCrtPath) {
		this.name = name;
		this.host = host;
		this.port = port;
		this.eventHubPort = eventHubPort;
		this.tlsCrtPath = tlsCrtPath;
		this.type = type;
	}
}
