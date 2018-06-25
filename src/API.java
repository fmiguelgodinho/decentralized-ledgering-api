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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.Security;
import java.util.Iterator;
import java.util.List;

import javax.servlet.MultipartConfigElement;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.builder.fluent.Configurations;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.InvalidFileNameException;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.io.FilenameUtils;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.hyperledger.fabric_ca.sdk.exception.InvalidArgumentException;

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
import util.Pair;

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
	private static DiskFileItemFactory dfif;
	
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
		
		// create a factory for disk-based uploaded file items
		dfif = new DiskFileItemFactory();
		dfif.setRepository(new File(cfg.getString("api.fileupload.repositoryPath")));
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
                	Pair<String,Exception> result = postDeployContract(req, rsp);
                	
                	Exception ex = result.getRight();
                	
                	if (ex != null) {
                		if (ex instanceof FileUploadException) {
                	    	rsp.status(500);
                	    	rsp.type("text/html");
                    		return body().with(
                    			h3("Couldn't obtain contract file!")
                    		).render();
                		} else if (ex instanceof InvalidFileNameException) {
	            	    	rsp.status(400);
	            	    	rsp.type("text/html");
	                		return body().with(
	                			h3(ex.getMessage())
	                		).render();
                		}
                	}
                	
                	rsp.redirect("contract/" + result.getLeft());
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
	                	
	                	Pair<String,Exception> result = postInvokeOperation(req, rsp);
	                	
	                	Exception ex = result.getRight();
	                	
	                	// TODO
	                	
	                	rsp.redirect("records/" + result.getLeft());
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
    	
    	String result = null;
    	//TODO maybe? use the contract interpreter for this
    	// dynamically using logic of contract
    	try {
    		dpt.changeChannel(channel);
			result = dpt.callChaincodeFunction(
					Dispatcher.CHAINCODE_QUERY_OPERATION, 
					cid, 
					"queryAll", 
					new String[] {}
			);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
    	
		
		// return html
    	rsp.status(200);
    	rsp.type("text/html");
		return body().with(
		    h3("Contract ID: " + cid),
		    div().with(
		    	p("Below is a list of records related with the contract:")
		    ),
		    div(result)
		).render();
	}
	
	private static String getRecordDetails(Request req, Response rsp) {
		
		String channel = req.params(":channel");
    	String cid = req.params(":cid");
    	String key = req.params(":key");
		// execute action
    	
    	String result = null;
    	//TODO maybe? use the contract interpreter for this
    	// dynamically using logic of contract
    	try {
    		dpt.changeChannel(channel);
			result = dpt.callChaincodeFunction(
					Dispatcher.CHAINCODE_QUERY_OPERATION, 
					cid, 
					"query", 
					new String[] {
							key, "true" // view history
					}
			);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
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
		    	.withId("invokeOperationForm")
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
				    	div("Arguments to be fed to the operation (separate arguments by a '|'): "),
				    	textarea()
				    	.attr("form", "invokeOperationForm")
				    	.attr("rows", 20)
				    	.attr("cols", 70)
				    	.withId("operationArgs")
				    	.withName("operationArgs")
				    	.withPlaceholder("e.g. \n\n39128340614;\nexample-string;\n{\n\tbrand: 'Fiat',\n\tmodel: '500', \n\tunits: 1"
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
	
	private static Pair<String,Exception> postInvokeOperation(Request req, Response rsp) {

		Exception exc = null;
		
		String channel = req.params(":channel");
    	String cid = req.params(":cid");
    	String oid = req.queryParams("operationId");
    	String oargs = req.queryParams("operationArgs");
    	
    	String result = null;
    	//TODO maybe? use the contract interpreter for this
    	// dynamically using logic of contract
    	// 280f06d6-2c1d-48fc-a5ba-3bfacc42ba08 | { "foo": 123, "bar": "abc" }
    	
    	String[] args = oargs.split("\\|");
    	for (int i = 0; i < args.length; i++) {
    		args[i] = args[i].trim();
    	}
    	
		try {
	    	if (args.length == 0)
	    		throw new InvalidArgumentException("No arguments were able to be parsed! Please validate your input!");
		
    		dpt.changeChannel(channel);
			result = dpt.callChaincodeFunction(
					Dispatcher.CHAINCODE_INVOKE_OPERATION, 
					cid, 
					oid, 
					args
			);
		} catch (InvalidArgumentException i) {
			exc = i;
		} catch (InterruptedException e) {
			e.printStackTrace();
		} 

    	
    	// TODO
    	String key = "3";// REMOVE
    	
    	return new Pair<String,Exception>(key, exc);
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
		    	.withId("deployContractForm")
		    	.withMethod("POST")
		    	.withAction("deploy")
		    	.attr("enctype", "multipart/form-data")
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
				    	
				    	// contract specs
				    	div("Contract specification to instantiate the contract (has to comply with standard): "),
				    	textarea()
				    	.attr("rows", 20)
				    	.attr("cols", 70)
				    	.attr("form", "deployContractForm")
				    	.withId("contractSpecs")
				    	.withName("contractSpecs")
				    	.withPlaceholder("e.g. \n\n{\n\t\"extended-contract-properties\" : { \n\t\t\"consensus-nodes\" : [ ], \n\t\t\"consensus-type\" : \"bft\", \n\t\t\"signature-type\" : \"multisig\", \n\t\t\"signing-nodes\" : [ ] \n\t}, \n\t\"application-specific-properties\" : { \n\t\t\"max-records\" : 100, \n\t\t\"total-records\" : 0 \n\t} \n}")
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

				    	// submit
				    	button("Deploy")
				    	.withType("submit")
		    	)
		    )
		).render();
	}
	
	private static Pair<String, Exception> postDeployContract(Request req, Response rsp) {

		Exception exc = null;
		
		// this request includes a file
		req.attribute("org.eclipse.jetty.multipartConfig", new MultipartConfigElement("/tmp"));
		
		String channel = req.params(":channel");

		String chaincodeId = null, chaincodeVersion = null, chaincodeSpecs = null;
		File chaincodeFile = null;
		
		ServletFileUpload upload = new ServletFileUpload(dfif);
		upload.setSizeMax(cfg.getLong("api.fileupload.maxSize"));

		// parse the request
		try {			
			List<FileItem> items = upload.parseRequest(req.raw());
			
			// Process the uploaded items
			Iterator<FileItem> iter = items.iterator();
			while (iter.hasNext()) {
			    FileItem item = iter.next();
			    // parameters
			    if (item.isFormField()) {
			        switch (item.getFieldName()) {
				        case "contractId":
				        	chaincodeId = item.getString();
				        	break;
				        case "contractVersion":
				        	chaincodeVersion = item.getString();
				        	break;
				        case "contractSpecs":
				        	chaincodeSpecs = item.getString();
				        	break;
			        };
			    } else {
			    	// file
			    	String filename = FilenameUtils.getName(item.getName());
			    	String fileext = FilenameUtils.getExtension(filename);
			    	if (fileext == null || !fileext.equals("go")) {
			    		throw new InvalidFileNameException("Invalid file extension", "Invalid file extension for a contract! (Submit a .go file)");
			    	}
			    	chaincodeFile = new File(dfif.getRepository().getAbsolutePath() + "/" + filename);
			        item.write(chaincodeFile);
			    }
			}
			
			// delegate get contract to interpreter (it will store it on the db after successful install)
	    	ci.deployContract(channel, chaincodeId, chaincodeVersion, chaincodeFile, chaincodeSpecs);
	    	
		} catch (FileUploadException e) {
    		exc = e;
		} catch (InvalidFileNameException f) {
    		exc = f;
		} catch (Exception e) {
			e.printStackTrace();
		}
		
    	return new Pair<String, Exception>(chaincodeId, exc);
	}
	
	private static boolean shouldReturnHtml(Request request) {
	    String accept = request.headers("Accept");
	    return accept != null && accept.contains("text/html");
	}


}
