import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.List;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.builder.fluent.Configurations;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;

import core.ContractInterpreter;
import core.Dispatcher;
import core.dto.ChaincodeResult;
import core.dto.Contract;
import server.ApiTLSServer;
import server.Envelope;
import util.NodeConnection;

public class API {
	
	
	public static final String CONFIG_FILEPATH = "conf/config.properties";
	
	// TODO: nodes can be specified on contract
	public static NodeConnection[] HLF_INTEGRATION_CHANNEL_NODES = new NodeConnection[] {
		new NodeConnection(NodeConnection.PEER_TYPE, "peer0.blockchain-a.com", "localhost", 7051, "../../bootstrap/crypto-config/peerOrganizations/blockchain-a.com/tlsca/tlsca.blockchain-a.com-cert.pem"),
		new NodeConnection(NodeConnection.PEER_TYPE, "peer0.blockchain-b.com", "localhost", 10051, "../../bootstrap/crypto-config/peerOrganizations/blockchain-b.com/tlsca/tlsca.blockchain-b.com-cert.pem"),
		new NodeConnection(NodeConnection.PEER_TYPE, "peer0.blockchain-c.com", "localhost", 8051, "../../bootstrap/crypto-config/peerOrganizations/blockchain-c.com/tlsca/tlsca.blockchain-c.com-cert.pem"),
		new NodeConnection(NodeConnection.PEER_TYPE, "peer0.blockchain-d.com", "localhost", 9051, "../../bootstrap/crypto-config/peerOrganizations/blockchain-d.com/tlsca/tlsca.blockchain-d.com-cert.pem"),
		new NodeConnection(NodeConnection.PEER_TYPE, "peer0.blockchain-e.com", "localhost", 11051, "../../bootstrap/crypto-config/peerOrganizations/blockchain-e.com/tlsca/tlsca.blockchain-e.com-cert.pem"),
		new NodeConnection(NodeConnection.PEER_TYPE, "peer0.blockchain-f.com", "localhost", 12051, "../../bootstrap/crypto-config/peerOrganizations/blockchain-f.com/tlsca/tlsca.blockchain-f.com-cert.pem"),
		new NodeConnection(NodeConnection.ORDERER_TYPE, "orderer0.consensus.com", "localhost", 7050, "../../bootstrap/crypto-config/ordererOrganizations/consensus.com/tlsca/tlsca.consensus.com-cert.pem")
	};
	
	private static Configuration cfg;
	private static ContractInterpreter ci;
	
	public static void main(String[] args) throws Exception {
		
		// set up API configurations
		setUpConfigurations();
		
		// start internal modules of the API
		startInternalModules();
		
		// start the service itself
		startDTLSServer();
	}
	
	private static void setUpConfigurations() throws FileNotFoundException, IOException, ConfigurationException {
		
		// load config file
		Configurations cfggen = new Configurations();
		cfg = cfggen.properties(new File(CONFIG_FILEPATH));
		
		// restrict logging
		ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);
	    root.setLevel(ch.qos.logback.classic.Level.INFO);
		
		// set the security provider...
		Security.addProvider(new BouncyCastleProvider());
		
	}
	
	private static void startInternalModules() throws Exception {
		
        // initialize dispatcher
        Dispatcher dpt = new Dispatcher(
        	cfg,
    		HLF_INTEGRATION_CHANNEL_NODES
    	);     

        // initialize mongo client
		MongoClient dbClient = new MongoClient(new MongoClientURI(cfg.getString("mongo.address")));
		
		// initialize contract interpreter 
		ci = new ContractInterpreter(cfg, dbClient, dpt);
	}
	
	private static void startDTLSServer() throws IOException, NoSuchAlgorithmException, NoSuchProviderException {
		
//		// set socket and dtls config
//		DTLSServerProtocol srvProtocol = new DTLSServerProtocol(new SecureRandom());
		DatagramSocket socket = new DatagramSocket(cfg.getInt("api.port"));
		ApiTLSServer tlsSrv = new ApiTLSServer();

		int mtu = cfg.getInt("api.mtu");
		while (true) {
			
			// get datagram msg for connection handshake
			byte[] requestBuffer = new byte[mtu];
			DatagramPacket request = new DatagramPacket(requestBuffer, requestBuffer.length);
			socket.receive(request);
			
			// TODO: Create a thread here
			try {
				Envelope recvEnv = Envelope.fromBytes(request.getData());
				Envelope respEnv = null;
				// parse operation to respond
				switch (recvEnv.getOpCode()) {
					case Envelope.OP_GET_CONTRACT:
						respEnv = getContract(recvEnv, null);
						break;
					case Envelope.OP_SIGN_CONTRACT:
						respEnv = signContract(recvEnv, null);
						break;
					case Envelope.OP_QUERY:
						respEnv = queryOperation(recvEnv, null);
						break;
					case Envelope.OP_INVOKE:
						respEnv = invokeOperation(recvEnv, null);
						break;
				}
				byte[] responseBuffer = null;
				
				// respond to client
				InetAddress clientAddress = request.getAddress();
				int clientPort = request.getPort();
		 
	            DatagramPacket response = new DatagramPacket(responseBuffer, responseBuffer.length, clientAddress, clientPort);
	            socket.send(response);
			
			} catch (SocketTimeoutException e) {
				
			}
			
			// connect
//			System.out.println("Accepting connection from " + request.getAddress().getHostAddress() + ":" + request.getPort());
//			socket.connect(request.getAddress(), request.getPort());
//			
//			// create the server
//			DatagramTransport transport = new UDPTransport(socket, mtu);
//			DTLSTransport dtlsSrv = srvProtocol.accept(tlsSrv, transport);
//			
//			while (!socket.isClosed()) {
//				try {
//					
//				} catch (SocketTimeoutException e) {
//					
//				}
//			}
			

		}
	}
	
	/* ---------------------- API METHODS --------------------- */

	private static Envelope getContract(Envelope env, X509Certificate clientCrt) {
		
		// get parameters
		String channel = env.getChannelId();
    	String cid = env.getContractId();
    	
		try {
	    	// delegate get contract to interpreter
			Contract contract = ci.getContract(channel, cid, clientCrt);
			
			// produce an hash of the contract
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			md.update(contract.getRawRepresentation().getBytes());
			final String contractHash = new String(Base64.getEncoder().encode(md.digest()));
			
			// create json payload
			ObjectMapper om = new ObjectMapper();
			ObjectNode jsonResult = om.createObjectNode();
			jsonResult.put("contract", contract.getRawRepresentation());
			jsonResult.put("is-signed", contract.getSignature() != null && !contract.getSignature().isEmpty());
			jsonResult.put("hash", contractHash);
			final byte[] payload = om.writer().writeValueAsBytes(jsonResult);
			
			// create envelope
			return new Envelope(Envelope.RSP_GET_CONTRACT, channel, cid, payload);
			
		} catch (Exception e) {
			return throwErrorEnvelope(e, channel, cid);
		}
	}
	
//	private static X509Certificate extractClientCrt(Request req) {
//		X509Certificate[] crtList = (X509Certificate[]) req.raw().getAttribute("javax.servlet.request.X509Certificate");
//		return crtList[0];
//	}
	
	private static Envelope signContract(Envelope env, X509Certificate clientCrt) {

		
		String channel = env.getChannelId();
		String cid = env.getContractId();
    	
		// get client crt first
    	try {
        	String clientSig = new String(env.getPayload());
    		boolean result = ci.signContract(channel, cid, clientCrt, clientSig);

    		ObjectMapper om = new ObjectMapper();
			ObjectNode jsonResult = om.createObjectNode();
			jsonResult.put("result", result);
			final byte[] payload = om.writer().writeValueAsBytes(jsonResult);
			
			// create envelope
			return new Envelope(Envelope.RSP_SIGN_CONTRACT, channel, cid, payload);
		} catch (Exception e) {
			return throwErrorEnvelope(e, channel, cid);
		}
	}
	
	private static Envelope queryOperation(Envelope env, X509Certificate clientCrt) {
    	return postOperation(env, clientCrt, Dispatcher.CHAINCODE_QUERY_OPERATION);
	}
	
	
	private static Envelope invokeOperation(Envelope env, X509Certificate clientCrt) {
    	return postOperation(env, clientCrt, Dispatcher.CHAINCODE_INVOKE_OPERATION);
	}
	
	private static Envelope postOperation(Envelope env, X509Certificate clientCrt, int type) {

		
		String channel = env.getChannelId();
    	String cid = env.getContractId();
    	String oid = env.getFunction();
    	
    	try {
    		// get the payload arguments
			ObjectMapper om = new ObjectMapper();
    		String[] oargs = om.readValue(new String(env.getPayload()), String[].class);
    		
    		// execute the function
    		ChaincodeResult result = ci.verifyAndExecuteContract(type, channel, cid, oid, clientCrt, oargs);
    		// check for chain code failure
    		if (result == null || result.getStatus() == ChaincodeResult.CHAINCODE_FAILURE) {
				return throwErrorEnvelope(new Exception("Chaincode failure when performing " 
						+ (type == Dispatcher.CHAINCODE_QUERY_OPERATION? "query" : "invocation")), channel, cid);
    		}
    		
    		
			// parse signatures and put them into a usable format
        	List<String> signatures = result.getSignatures();
        	// put arrays into json array and finalize object
        	ArrayNode jsonArray = om.valueToTree(signatures);
        	ObjectNode jsonResult = om.createObjectNode();
        	jsonResult.putArray("signatures").addAll(jsonArray);
    		short opcode = -1;
    		
			switch (type) {
				case Dispatcher.CHAINCODE_INVOKE_OPERATION:
					// invoke just needs timestamp for transaction confirmal
		        	DateFormat df = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
		        	jsonResult.put("timestamp", df.format(result.getTimestamp()));
					opcode = Envelope.RSP_INVOKE;
					break;
	            	
				case Dispatcher.CHAINCODE_QUERY_OPERATION:
					// query needs query results
					jsonResult.put("result", result.getContent());
					opcode = Envelope.RSP_QUERY;
					break;
					
				default:
					throwErrorEnvelope(new Exception("Unknown chaincode response"), channel, cid);
			}
			
			byte[] payload = om.writer().writeValueAsBytes(jsonResult);
        	return new Envelope(opcode, channel, cid, payload);
    		
    	} catch (Exception e) {
			return throwErrorEnvelope(e, channel, cid);
    	}
    	
	}
	
	private static Envelope throwErrorEnvelope(Exception e, String channel, String cid) {

		try {			
			ObjectMapper om = new ObjectMapper();
			
			// put exception details into json
			ObjectNode jsonResult = om.createObjectNode();
			jsonResult.put("error", e.toString().getBytes());
			byte[] payload = om.writer().writeValueAsBytes(jsonResult);
			return new Envelope(Envelope.ERR, channel, cid, payload);			
		
		} catch (JsonProcessingException jpe) {
			jpe.printStackTrace();
		}
		return null;
	}


}
