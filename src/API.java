import static j2html.TagCreator.body;
import static j2html.TagCreator.br;
import static j2html.TagCreator.button;
import static j2html.TagCreator.div;
import static j2html.TagCreator.form;
import static j2html.TagCreator.h3;
import static j2html.TagCreator.input;
import static j2html.TagCreator.p;
import static j2html.TagCreator.span;
import static j2html.TagCreator.textarea;
import static spark.Spark.get;
import static spark.Spark.path;
import static spark.Spark.port;
import static spark.Spark.post;
import static spark.Spark.secure;
import static spark.Spark.threadPool;
import static spark.Spark.before;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.Security;
import java.util.Set;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.builder.fluent.Configurations;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;

import core.ContractInterpreter;
import core.RBEngine;
import integration.Dispatcher;
import spark.Request;
import spark.Response;
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
	private static Dispatcher dpt;
	private static ContractInterpreter ci;
	private static RBEngine rbe;
	
	public static void main(String[] args) throws Exception {
		
		// set up API configurations
		setUpConfigurations();
		
		// start internal modules of the API
		startInternalModules();
		
		// start the REST service itself
		startRESTServer();
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
        dpt = new Dispatcher(
        	cfg,
    		HLF_INTEGRATION_CHANNEL_NODES
    	);     

        // initialize mongo client
		MongoClient dbClient = new MongoClient(new MongoClientURI(cfg.getString("mongo.address")));
		
		// initialize contract interpreter and representative broker engine
		rbe = new RBEngine(dbClient);
		ci = new ContractInterpreter(cfg, dbClient, dpt);
	}
	
	private static void startRESTServer() {
		// set port, https and threadpool config
        port(
        	cfg.getInt("api.port")
    	);
        threadPool(
    		cfg.getInt("api.threadPool.max"), 
    		cfg.getInt("api.threadPool.min"), 
    		cfg.getInt("api.threadPool.timeout")
        );
        secure(
    		cfg.getString("api.ssl.keystorePath"), 
    		cfg.getString("api.ssl.keystorePw"), 
    		cfg.getString("api.ssl.truststorePath"),
    		cfg.getString("api.ssl.truststorePw"),
    		cfg.getBoolean("api.ssl.muthualAuth")
    	);
        
         

        // setup routing		
        path("/api", () -> {
//        	before((req, rsp) -> {
//        	    boolean authenticated;
//        	    // ... check if authenticated
//        	    if (!authenticated) {
//        	        halt(401, "You are not welcome here");
//        	    }
//        		// log something
//        		log....
//        	});
        	
        	get("/", (request, response) -> "Blockchain-supported Ledgering API for Decentralized Applications - v1.0");
        	
            path("/:channel", () -> {
            	
            	// functions for deploying contracts (should only be available to providers)
                get("/deploy", (req, rsp) -> getDeployContract(req, rsp));
                post("/deploy", (req, rsp) -> {
                	String newKey = postDeployContract(req, rsp);
                	rsp.redirect("contract/" + newKey);
                	return null;
                });
                
                path("/contract", () -> {
                
	                // get contract specification
	                get("/:cid", (req, rsp) -> getContract(req, rsp));
	                
	                // get records stored in contract
	                get("/:cid/records", (req, rsp) -> getRecordsList(req, rsp));
	                
	                // get specific record
	                get("/:cid/records/:key", (req, rsp) -> getRecordDetails(req, rsp));
	                
	                // invoke contract operation
	                get("/:cid/invoke", (req, rsp) -> getInvokeOperation(req, rsp));
	                post("/:cid/invoke", (req, rsp) -> {
	                	String newKey = postInvokeOperation(req, rsp);
	                	rsp.redirect("records/" + newKey);
	                	return null;
	                });
                });
            });
        });
	}
	
	/* ---------------------- API METHODS --------------------- */

	private static String getContract(Request req, Response rsp) {
		
		// parse parameters
		String channel = req.params(":channel");
    	String cid = req.params(":cid");
    	
    	// delegate get contract to interpreter
    	String result = ci.getContractRaw(channel, cid);
    	
    	// if not found
    	if (result == null) {
    		
    		rsp.status(404);
    		rsp.type("text/html");
    		return body().with(
    			h3("Couldn't find the contract you specified!"),
    			div().with(
    				p("Make sure you've typed its ID correctly and that it exists.")
    			)
    		).render();
    	}
    	
    	// found. return json in pretty print
		try {
			ObjectMapper mapper = new ObjectMapper();
			JsonNode resultObject = mapper.readTree(result);
	    	result = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(resultObject);
		} catch (IOException e) {
			e.printStackTrace();
		}

		// return html
    	rsp.status(200);
    	rsp.type("text/html");
		return body().with(
		    h3("Contract ID: " + cid),
		    div().with(
		    	p("Below is all metadata related with the contract:")
		    ),
		    div(result)
		).render();
	}
	
	private static String getRecordsList(Request req, Response rsp) {

		String channel = req.params(":channel");
    	String cid = req.params(":cid");
		// execute action
    	//TODO
		
		// return html
    	rsp.status(200);
    	rsp.type("text/html");
		return body().with(
		    h3("Contract ID: " + cid),
		    div().with(
		    	p("Below is a list of records related with the contract:")

//		          model.getAllPosts().stream().map((post) ->
//		                div().with(
//		                        h2(post.getTitle()),
//		                        p(post.getContent()),
//		                        ul().with(post.getCategories().stream().map((category) ->
//		                              li(category)).collect(Collectors.toList())
//		                        )
//		                )
//		          ).collect(Collectors.toList())
//	    )
		    )
		).render();
	}
	
	private static String getRecordDetails(Request req, Response rsp) {
		
		String channel = req.params(":channel");
    	String cid = req.params(":cid");
    	String key = req.params(":key");
		// execute action
    	//TODO
		
		// return html
    	rsp.status(200);
    	rsp.type("text/html");
		return body().with(
		    h3("Record Key: " + key),
		    div().with(
		    	p("Below is all data related with the specified record:")
		    )
		).render();
	}
	
	private static String getInvokeOperation(Request req, Response rsp) {
		
		String channel = req.params(":channel");
		String cid = req.params(":cid");
		// execute action
		// TODO
		
		// return html
    	rsp.status(200);
    	rsp.type("text/html");
		return body().with(
		    h3("Contract ID: " + cid),
		    div().with(
		    	p("Fill in invocation details. View contract details if you need to know what operations are supported:"),
		    	br(),
		    	form()
		    	.withMethod("POST")
		    	.withAction("invoke")
		    	.with(
				    	// operation id
				    	span("Operation to execute: "),
				    	input()
				    	.withType("text")
				    	.withId("operationId")
				    	.withName("operationId")
				    	.withPlaceholder("e.g. BuyNewCar")
				    	.isRequired(),
				    	br(),
				    	
				    	// transaction / data details
				    	div("Transactional data to be used by the operation (JSON format): "),
				    	textarea()
				    	.attr("rows", 20)
				    	.attr("cols", 70)
				    	.withId("transactionData")
				    	.withName("transactionData")
				    	.withPlaceholder("e.g. \n\n{\n\tbrand: 'Fiat',\n\tmodel: '500', \n\tunits: 1"
				    			+ "\n\tcar-id: ['u8d923-da8313-28mc3-km093i'], \n\tpayment-details: {\n\t\tamount-to-be-payed: '30000',"
				    			+ "\n\t\tcurrency: 'euro', \n\t\tamount-paying: '30000', \n\t\tpayment-method: 'credit-card',"
				    			+ "\n\t\tpayment-policy: '100%'\n\t}\n}")
				    	.isRequired(),
				    	
				    	// submit
				    	br(),
				    	button("Invoke")
				    	.withType("submit")
		    	)
		    )
		).render();
	}
	
	private static String postInvokeOperation(Request req, Response rsp) {

		String channel = req.params(":channel");
    	String cid = req.params(":cid");
    	String oid = req.queryParams("operationId");
    	String tx = req.queryParams("transactionData");
    	
    	// TODO
    	String key = "3";// REMOVE
    	
    	return key;
	}
	
	
	private static String getDeployContract(Request req, Response rsp) {
		
		String channel = req.params(":channel");
		// execute action
		// TODO
		
		// return html
    	rsp.status(200);
    	rsp.type("text/html");
		return body().with(
		    h3("Deploy Contract"),
		    div().with(
		    	p("Fill in deployment details:"),
		    	br(),
		    	form()
		    	.withMethod("POST")
		    	.withAction("deploy")
		    	.with(
				    	// contract id
				    	span("Contract ID: "),
				    	input()
				    	.withType("text")
				    	.withId("contractId")
				    	.withName("contractId")
				    	.withPlaceholder("e.g. examplecc")
				    	.isRequired(),
				    	br(),
				    	
				    	// contract id
				    	span("Contract version: "),
				    	input()
				    	.withType("text")
				    	.withId("contractVersion")
				    	.withName("contractVersion")
				    	.withPlaceholder("e.g. 1 (or > 1 if you're upgrading an existing contract)")
				    	.isRequired(),
				    	br(),
				    	
				    	// contract chaincode (in Golang)
				    	span("Contract source file: "),
				    	input()
				    	.withType("file")
				    	.withId("contractFile")
				    	.withName("contractFile")
				    	.isRequired(),
				    	br(),
				    	
				    	// contract specs
				    	div("Contract specification to instantiate the contract (has to comply with standard): "),
				    	textarea()
				    	.attr("rows", 20)
				    	.attr("cols", 70)
				    	.withId("contractSpecs")
				    	.withName("contractSpecs")
				    	.withPlaceholder("e.g. \n\n{\n\t\"extended-contract-properties\" : { \n\t\t\"consensus-nodes\" : [ ], \n\t\t\"consensus-type\" : \"bft\", \n\t\t\"signature-type\" : \"multisig\", \n\t\t\"signing-nodes\" : [ ] \n\t}, \n\t\"application-specific-properties\" : { \n\t\t\"max-records\" : 100, \n\t\t\"total-records\" : 0 \n\t} \n}")
				    	.isRequired(),
				    	
				    	// submit
				    	br(),
				    	button("Deploy")
				    	.withType("submit")
		    	)
		    )
		).render();
	}
	
	private static String postDeployContract(Request req, Response rsp) {
		
		String channel = req.params(":channel");
    	String cid = req.queryParams("contractId");
    	String cver = req.queryParams("contractVersion");
    	String cfile = req.queryParams("contractFile");
    	String cspecs = req.queryParams("contractSpecs");
    	
    	// TODO
    	String key = "xcc";// REMOVE
    	
    	return key;
	}
	
	private static boolean shouldReturnHtml(Request request) {
	    String accept = request.headers("Accept");
	    return accept != null && accept.contains("text/html");
	}


}
