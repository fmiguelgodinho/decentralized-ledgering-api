import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.Security;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.builder.fluent.Configurations;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import com.mbed.coap.server.CoapHandler;
import com.mbed.coap.server.CoapServer;
import com.mbed.coap.utils.ReadOnlyCoapResource;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;

import core.ContractInterpreter;
import core.Dispatcher;
import endpoint.coap.CoAPServer;
import endpoint.rest.RESTServer;
import util.NodeConnection;

public class API {
	
	
	public static final String CONFIG_FILEPATH = "conf/config.properties";
	
	// TODO: nodes can be specified on contract
	public static NodeConnection[] HLF_INTEGRATION_BOOTSTRAP_NODES;
	
	private static Configuration cfg;
	private static ContractInterpreter ci;
	
	public static void main(String[] args) throws Exception {
		
		// set up API configurations
		setUpConfigurations();
		
		// start internal modules of the API
		startInternalModules();
		
		// start the REST service
		new RESTServer(cfg, ci).start();
		
		// start the CoAP service
		new CoAPServer(cfg, ci).start();
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
		
		// set up bootstrap nodes
		String trustedCaPath = cfg.getString("hlf.trustedCasPath");
		String[] bootstrapNodes = cfg.getStringArray("hlf.bootstrapNodes");
		HLF_INTEGRATION_BOOTSTRAP_NODES = new NodeConnection[bootstrapNodes.length];
		for (int i = 0; i < bootstrapNodes.length; i++) {
			String cfgNode = bootstrapNodes[i];
			String cfgNodeName = cfg.getString("bootstrapNode." + cfgNode + ".name");
			String cfgNodeDomain = cfgNodeName.substring(cfgNodeName.indexOf(".")+1);
			
			int nodeType = cfg.getInt("bootstrapNode." + cfgNode + ".type");
			HLF_INTEGRATION_BOOTSTRAP_NODES[i] = new NodeConnection(
				nodeType,
				cfg.getString("bootstrapNode." + cfgNode + ".name"),
				cfg.getString("bootstrapNode." + cfgNode + ".host"),
				cfg.getInt("bootstrapNode." + cfgNode + ".port"),
				nodeType == NodeConnection.PEER_TYPE ? 
						cfg.getInt("bootstrapNode." + cfgNode + ".eventHubPort") : -1,
				trustedCaPath + "/tlsca." + cfgNodeDomain + "-cert.pem"
			);
			

		}
		
	}
	
	private static void startInternalModules() throws Exception {
		
        // initialize dispatcher
        Dispatcher dpt = new Dispatcher(
        	cfg,
    		HLF_INTEGRATION_BOOTSTRAP_NODES
    	);     

        // initialize mongo client
		MongoClient dbClient = new MongoClient(new MongoClientURI(cfg.getString("mongo.address")));
		
		// initialize contract interpreter 
		ci = new ContractInterpreter(cfg, dbClient, dpt);
	}



}
