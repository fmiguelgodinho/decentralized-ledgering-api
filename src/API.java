import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Security;
import java.security.cert.CertificateException;
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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import com.wolfssl.WolfSSL;
import com.wolfssl.WolfSSLContext;
import com.wolfssl.WolfSSLException;
import com.wolfssl.WolfSSLJNIException;
import com.wolfssl.WolfSSLSession;

import core.ContractInterpreter;
import core.Dispatcher;
import core.dto.ChaincodeResult;
import core.dto.Contract;
import server.DTLSGenCookieContext;
import server.DTLSIOContext;
import server.DTLSRecvCallback;
import server.DTLSSendCallback;
import server.Envelope;
import util.NodeConnection;

public class API {

	public static final String CONFIG_FILEPATH = "conf/config.properties";

	// TODO: nodes can be specified on contract
	public static NodeConnection[] HLF_INTEGRATION_CHANNEL_NODES = new NodeConnection[] { new NodeConnection(
			NodeConnection.PEER_TYPE, "peer0.blockchain-a.com", "localhost", 7051,
			"../../bootstrap/crypto-config/peerOrganizations/blockchain-a.com/tlsca/tlsca.blockchain-a.com-cert.pem"),
			new NodeConnection(NodeConnection.PEER_TYPE, "peer0.blockchain-b.com", "localhost", 10051,
					"../../bootstrap/crypto-config/peerOrganizations/blockchain-b.com/tlsca/tlsca.blockchain-b.com-cert.pem"),
			new NodeConnection(NodeConnection.PEER_TYPE, "peer0.blockchain-c.com", "localhost", 8051,
					"../../bootstrap/crypto-config/peerOrganizations/blockchain-c.com/tlsca/tlsca.blockchain-c.com-cert.pem"),
			new NodeConnection(NodeConnection.PEER_TYPE, "peer0.blockchain-d.com", "localhost", 9051,
					"../../bootstrap/crypto-config/peerOrganizations/blockchain-d.com/tlsca/tlsca.blockchain-d.com-cert.pem"),
			new NodeConnection(NodeConnection.PEER_TYPE, "peer0.blockchain-e.com", "localhost", 11051,
					"../../bootstrap/crypto-config/peerOrganizations/blockchain-e.com/tlsca/tlsca.blockchain-e.com-cert.pem"),
			new NodeConnection(NodeConnection.PEER_TYPE, "peer0.blockchain-f.com", "localhost", 12051,
					"../../bootstrap/crypto-config/peerOrganizations/blockchain-f.com/tlsca/tlsca.blockchain-f.com-cert.pem"),
			new NodeConnection(NodeConnection.ORDERER_TYPE, "orderer0.consensus.com", "localhost", 7050,
					"../../bootstrap/crypto-config/ordererOrganizations/consensus.com/tlsca/tlsca.consensus.com-cert.pem") };

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
		ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory
				.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);
		root.setLevel(ch.qos.logback.classic.Level.INFO);

		// set the security provider...
		Security.addProvider(new BouncyCastleProvider());

	}

	private static void startInternalModules() throws Exception {

		// initialize dispatcher
		Dispatcher dpt = new Dispatcher(cfg, HLF_INTEGRATION_CHANNEL_NODES);

		// initialize mongo client
		MongoClient dbClient = new MongoClient(new MongoClientURI(cfg.getString("mongo.address")));

		// initialize contract interpreter
		ci = new ContractInterpreter(cfg, dbClient, dpt);
	}

	private static void startDTLSServer() throws IOException, NoSuchAlgorithmException, NoSuchProviderException,
			WolfSSLException, ConfigurationException, IllegalStateException, WolfSSLJNIException {

		// // set socket and dtls config
		WolfSSL.loadLibrary();
		WolfSSL sslLib = new WolfSSL();
		sslLib.debuggingON();

		long dtlsMethod = -1;
		switch (cfg.getString("api.dtls.version")) {

		case "1":
			dtlsMethod = WolfSSL.DTLSv1_ServerMethod();
			break;
		case "1.2":
			dtlsMethod = WolfSSL.DTLSv1_2_ServerMethod();
			break;
		default:
			throw new ConfigurationException("Unknown DTLS version (known are 1 or 1.2)");
		}

		WolfSSLContext sslCtx = new WolfSSLContext(dtlsMethod);
		/* load certificate/key files */
		int ret = sslCtx.useCertificateFile(cfg.getString("api.dtls.serverCrt"), WolfSSL.SSL_FILETYPE_PEM);
		if (ret != WolfSSL.SSL_SUCCESS) {
			System.out.println("failed to load server certificate!");
			System.exit(1);
		}

		ret = sslCtx.usePrivateKeyFile(cfg.getString("api.dtls.serverKey"), WolfSSL.SSL_FILETYPE_PEM);
		if (ret != WolfSSL.SSL_SUCCESS) {
			System.out.println("failed to load server private key!");
			System.exit(1);
		}

		// load verify with trusted CAs
		ret = sslCtx.loadVerifyLocations(cfg.getString("api.dtls.clientCaCrt"), null);
        if (ret != WolfSSL.SSL_SUCCESS) {
            System.out.println("failed to load CA certificates!");
            System.exit(1);
        }
		sslCtx.setVerify(WolfSSL.SSL_VERIFY_PEER, null);

		// set dtls callbacks
		sslCtx.setIORecv(new DTLSRecvCallback());
		sslCtx.setIOSend(new DTLSSendCallback());
		System.out.println("Registered I/O callbacks");

		// register UDP socket
		InetAddress hostAddress = InetAddress.getLocalHost();
		int port = cfg.getInt("api.port");
		System.out.println("Starting DTLS server at " + hostAddress + ", port " + port);

		DataInputStream is = null;
		DataOutputStream os = null;
		int mtu = cfg.getInt("api.mtu");
		while (true) {
			
			// set socket
			DatagramSocket socket = new DatagramSocket(port);
			socket.setSoTimeout(0);
			
			// get datagram msg for connection handshake
			byte[] recvBuffer = new byte[mtu];
			DatagramPacket request = new DatagramPacket(recvBuffer, recvBuffer.length);
			socket.receive(request);
			socket.connect(request.getAddress(), request.getPort());
			System.out.println("Client connection received from " + request.getAddress() + " at port " + request.getPort());

			// setup ssl session and io context
			WolfSSLSession ssl = new WolfSSLSession(sslCtx);

			// setup io context
			DTLSIOContext ioctx = new DTLSIOContext(os, is, socket, hostAddress, port);
			ssl.setIOReadCtx(ioctx);
			ssl.setIOWriteCtx(ioctx);

			// register DTLS cookie generation callback
			DTLSGenCookieContext gctx = new DTLSGenCookieContext(hostAddress, port);
			ssl.setGenCookieCtx(gctx);

			ret = ssl.accept();
			if (ret != WolfSSL.SSL_SUCCESS) {
				int err = ssl.getError(ret);
				String errString = sslLib.getErrorString(err);
				System.out.println("wolfSSL_accept failed. err = " + err + ", " + errString);
				System.exit(1);
			}
            /* read client response, and echo */
			byte[] reqBuffer = new byte[mtu];
            ret = ssl.read(reqBuffer, reqBuffer.length);
            // DO SOMETHING WITH RET: VALIDATION
            /* show peer info */
			showClientCertificate(ssl);
			
			try {
				// get client crt
				long clientCrt = ssl.getPeerCertificate();
				if (clientCrt == 0) {
					throw new CertificateException("Client did not present a valid certificate");
				}
				
				Envelope recvEnv = Envelope.fromBytes(reqBuffer);
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
	
				// create rsp buffer & respond to client
				byte[] respBuffer = Envelope.toBytes(respEnv);
				
	            ret = ssl.write(respBuffer, respBuffer.length);
	            if (ret != respBuffer.length) {
	                System.out.println("ssl.write() failed");
	                System.exit(1);
	            }
			} catch (Exception e) {
				System.err.println(e.toString());
			} finally {
			
				// shutdown ssl and disconnect socket
	            ssl.shutdownSSL();
	            ssl.freeSSL();
	            socket.disconnect();
	            socket.close();
			}

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

	// private static X509Certificate extractClientCrt(Request req) {
	// X509Certificate[] crtList = (X509Certificate[])
	// req.raw().getAttribute("javax.servlet.request.X509Certificate");
	// return crtList[0];
	// }

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
				return throwErrorEnvelope(
						new Exception("Chaincode failure when performing "
								+ (type == Dispatcher.CHAINCODE_QUERY_OPERATION ? "query" : "invocation")),
						channel, cid);
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
	
	private static void showClientCertificate(WolfSSLSession ssl) {

		String altname;
		long peerCrtPtr;

		try {

			peerCrtPtr = ssl.getPeerCertificate();

			if (peerCrtPtr != 0) {

				System.out.println("issuer : " + ssl.getPeerX509Issuer(peerCrtPtr));
				System.out.println("subject : " + ssl.getPeerX509Subject(peerCrtPtr));

				while ((altname = ssl.getPeerX509AltName(peerCrtPtr)) != null)
					System.out.println("altname = " + altname);

			} else {
				System.out.println("peer has no cert!\n");
			}

			System.out.println("SSL version is " + ssl.getVersion());
			System.out.println("SSL cipher suite is " + ssl.cipherGetName());

		} catch (WolfSSLJNIException e) {
			e.printStackTrace();
		}
	}

}
