import static j2html.TagCreator.body;
import static j2html.TagCreator.br;
import static j2html.TagCreator.button;
import static j2html.TagCreator.div;
import static j2html.TagCreator.each;
import static j2html.TagCreator.form;
import static j2html.TagCreator.h3;
import static j2html.TagCreator.input;
import static j2html.TagCreator.p;
import static j2html.TagCreator.pre;
import static j2html.TagCreator.span;
import static j2html.TagCreator.textarea;
import static spark.Spark.get;
import static spark.Spark.path;
import static spark.Spark.port;
import static spark.Spark.post;
import static spark.Spark.secure;
import static spark.Spark.threadPool;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.MessageDigest;
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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mbed.coap.server.CoapServer;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;

import core.ContractInterpreter;
import core.Dispatcher;
import core.dto.ChaincodeResult;
import core.dto.Contract;
import core.rest.RestServer;
import spark.Request;
import spark.Response;
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
		startRESTServer();
		
		// start the CoAP service
		startCoAPServer();
	}
	
	private static void startRESTServer() {
		// setup the rest server
		RestServer srv = new RestServer(cfg, ci);
		srv.start();
	}

	private static void startCoAPServer() throws IllegalStateException, IOException {
		CoapServer server = CoapServer.builder().transport(
	        	cfg.getInt("api.coap.port")
	    ).build();
		
		server.start();
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
