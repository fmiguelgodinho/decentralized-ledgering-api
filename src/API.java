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
import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;

import core.ContractInterpreter;
import core.Dispatcher;
import core.dto.ChaincodeResult;
import core.dto.Contract;
import spark.Request;
import spark.Response;
import util.NodeConnection;

public class API {
	
	
	public static final String CONFIG_FILEPATH = "conf/config.properties";
	
	// TODO: nodes can be specified on contract
	public static NodeConnection[] HLF_INTEGRATION_CHANNEL_NODES;
	
	private static Configuration cfg;
	private static ContractInterpreter ci;
	
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
		
		// set up bootstrap nodes
		
		String[] bootstrapNodes = cfg.getStringArray("hlf.bootstrapNodes");
		HLF_INTEGRATION_CHANNEL_NODES = new NodeConnection[bootstrapNodes.length];
		for (int i = 0; i < bootstrapNodes.length; i++) {
			String cfgNode = bootstrapNodes[i];
			int nodeType = cfg.getInt("bootstrapNode." + cfgNode + ".type");
			HLF_INTEGRATION_CHANNEL_NODES[i] = new NodeConnection(
				nodeType,
				cfg.getString("bootstrapNode." + cfgNode + ".name"),
				cfg.getString("bootstrapNode." + cfgNode + ".host"),
				cfg.getInt("bootstrapNode." + cfgNode + ".port"),
				nodeType == NodeConnection.PEER_TYPE ? 
						cfg.getInt("bootstrapNode." + cfgNode + ".eventHubPort") : -1,
				cfg.getString("bootstrapNode." + cfgNode + ".caCrtPath")
			);
		}
		
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
         

//        ObjectMapper jsonMapper = new ObjectMapper();
        // setup routing		
        path("/api", () -> {
        	
        	get("/", (request, response) -> "Blockchain-supported Ledgering API for Decentralized Applications - v1.0");

            path("/:channel", () -> {
                
                path("/contract", () -> {

	                get("/:cid", "text/html", (req, rsp) -> getContract(req, rsp));
	                get("/:cid", "application/json", (req, rsp) -> getContract(req, rsp));
	                
	                post("/:cid/sign", "text/html", (req, rsp) -> postContractSign(req, rsp));
	                post("/:cid/sign", "application/json", (req, rsp) -> postContractSign(req, rsp));
	                
	                get("/:cid/query", "text/html", (req, rsp) -> getQueryOperation(req, rsp));		// HTML only
	                
	                get("/:cid/invoke", "text/html", (req, rsp) -> getInvokeOperation(req, rsp));	// HTML only

	                post("/:cid/query", "text/html", (req, rsp) -> postQueryOperation(req, rsp));
	                post("/:cid/query", "application/json", (req, rsp) -> postQueryOperation(req, rsp));

	                post("/:cid/invoke", "text/html", (req, rsp) -> postInvokeOperation(req, rsp));  // HTML/JSON
	                post("/:cid/invoke", "application/json", (req, rsp) -> postInvokeOperation(req, rsp));  // HTML/JSON
                });
            });
        });
	}
	
	/* ---------------------- API METHODS --------------------- */

	private static String getContract(Request req, Response rsp) {
		

		// parse parameters
		String channel = req.params(":channel");
    	String cid = req.params(":cid");
    	

		try {
			// delegate get contract to interpreter
			Contract contract = ci.getContract(channel, cid, extractClientCrt(req));

			// produce an hash of the contract
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			md.update(contract.getRawRepresentation().getBytes());
			final String contractHash = new String(Base64.getEncoder().encode(md.digest()));


	    	rsp.status(200);
			if (shouldReturnHtml(req)) {
				
				// return html
		    	rsp.type("text/html");
				return body().with(
				    h3("Contract ID: " + cid),
				    div().with(
				    	p("Below is all metadata related with the contract:")
				    ),
				    pre(contract.getPrettyPrintRepresentation()),
				    br(),
		    		contract.getSignature() != null && !contract.getSignature().isEmpty()?
		    				div("SIGNED").withStyle("color:green") :
							div("NOT SIGNED").withStyle("color:red"), 
				    				
					contract.getSignature() != null && !contract.getSignature().isEmpty()?
				    		div("Contract is signed. Below is the SHA256 hash of the contract and your signature."):
				    		div("Please sign the below SHA256 hash of the contract with your private key (using openssl or an utility), copy the signature to the field below (in base64 format) and press accept if you agree with the contract."),
				    br(),
				    form()
			    	.withMethod("POST")
			    	.withAction(cid + "/sign")
			    	.withId("signContractForm")
			    	.with(
			    		div(
			    			textarea(contractHash)
					    	.attr("rows", 4)
					    	.attr("cols", 100)
					    	.attr("readonly")
				    	),
			    		div(
			    			textarea(contract.getSignature() != null && !contract.getSignature().isEmpty()? contract.getSignature() : "")
					    	.attr("form", "signContractForm")
					    	.attr("rows", 10)
					    	.attr("cols", 100)
					    	.withId("signature")
					    	.withName("signature")
					    	.withPlaceholder(
					    			"Paste your signature here."
					    	)
					    	.isRequired()
			    		),
			    		div().with(
				    		span().with(
				    				
				    			button("Accept & sign")
				    			.withType("submit")
				    			.withCondHidden(contract.getSignature() != null && !contract.getSignature().isEmpty()),
				    			
				    			button("Reject")
				    			.withType("cancel")
				    			.withCondHidden(contract.getSignature() != null && !contract.getSignature().isEmpty())
						    )
			    		)
			    	)
				).render();
			} else {

				// create json payload
		    	rsp.type("application/json");
				ObjectMapper om = new ObjectMapper();
				ObjectNode jsonResult = om.createObjectNode();
				jsonResult.put("contract", contract.getRawRepresentation());
				jsonResult.put("is-signed", contract.getSignature() != null && !contract.getSignature().isEmpty());
				jsonResult.put("hash", contractHash);
				return om.writer().writeValueAsString(jsonResult);
			}
			

		} catch (Exception e) {
			return throwError(req, rsp, e);
		}
	}
	


	private static String postContractSign(Request req, Response rsp) {

		// get params
		String channel = req.params(":channel");
		String cid = req.params(":cid");
    	String clientSig = req.queryParams("signature");
		
    	try {
    		
    		// get client crt first
    		boolean result = ci.signContract(channel, cid, extractClientCrt(req), clientSig);
    		
    		rsp.status(200);
    		if (shouldReturnHtml(req)) {

    			// if html just redirect to the contract and obtain it again,
    			// the confirmation will be there
    	    	rsp.type("text/html");
    	    	rsp.redirect("/api/" + channel + "/contract/" + cid);
    		} else {
    	    	rsp.type("application/json");
				ObjectMapper om = new ObjectMapper();
				ObjectNode jsonResult = om.createObjectNode();
				jsonResult.put("result", result);
				return om.writer().writeValueAsString(jsonResult);
    		}
    		
		} catch (Exception e) {
			return throwError(req, rsp, e);
		}
    	return null;
	}
	
	private static String getQueryOperation(Request req, Response rsp) {
		
		String cid = req.params(":cid");
		// execute action
		
		// return html
    	rsp.status(200);
    	rsp.type("text/html");
		return body().with(
		    h3("Contract ID: " + cid),
		    div().with(
		    	p("Fill in querying details. View contract details if you need to know what functions are supported:"),
		    	br(),
		    	form()
		    	.withMethod("POST")
		    	.withAction("query")
		    	.withId("queryOperationForm")
		    	.with(
				    	// operation id
				    	span("Function to query: "),
				    	input()
				    	.withType("text")
				    	.withId("operationId")
				    	.withName("operationId")
				    	.withPlaceholder("e.g. GetCarByLicensePlate")
				    	.isRequired(),
				    	br(),
				    	
				    	// transaction / data details
				    	div("Arguments to be fed to the function (in JSON array format): "),
				    	textarea()
				    	.attr("form", "queryOperationForm")
				    	.attr("rows", 20)
				    	.attr("cols", 70)
				    	.withId("operationArgs")
				    	.withName("operationArgs")
				    	.withPlaceholder("e.g. \n\n[\"64-44-KL\"]"),
				    	
				    	// submit
				    	br(),
				    	button("Query")
				    	.withType("submit")
		    	)
		    )
		).render();
	}
	
	private static String getInvokeOperation(Request req, Response rsp) {
		
		String cid = req.params(":cid");
		// execute action
		
		// return html
    	rsp.status(200);
    	rsp.type("text/html");
		return body().with(
		    h3("Contract ID: " + cid),
		    div().with(
		    	p("Fill in invocation details. View contract details if you need to know what functions are supported:"),
		    	br(),
		    	form()
		    	.withMethod("POST")
		    	.withAction("invoke")
		    	.withId("invokeOperationForm")
		    	.with(
				    	// operation id
				    	span("Function to invoke: "),
				    	input()
				    	.withType("text")
				    	.withId("operationId")
				    	.withName("operationId")
				    	.withPlaceholder("e.g. BuyNewCar")
				    	.isRequired(),
				    	br(),
				    	
				    	// transaction / data details
				    	div("Arguments to be fed to the function (in JSON array format): "),
				    	textarea()
				    	.attr("form", "invokeOperationForm")
				    	.attr("rows", 20)
				    	.attr("cols", 70)
				    	.withId("operationArgs")
				    	.withName("operationArgs")
				    	.withPlaceholder("e.g. \n\n[\n\t\"39128340614\",\n\t\"example-string\",\n\t\"{\n\t\tbrand: 'Fiat',\n\t\tmodel: '500', \n\t\tunits: 1"
				    			+ "\n\t\tcar-id: ['u8d923-da8313-28mc3-km093i'], \n\t\tpayment-details: {\n\t\t\tamount-to-be-payed: '30000',"
				    			+ "\n\t\t\tcurrency: 'euro', \n\t\t\tamount-paying: '30000', \n\t\t\tpayment-method: 'credit-card',"
				    			+ "\n\t\t\tpayment-policy: '100%'\n\t\t}\n\t}\"\n]"),
				    	
				    	// submit
				    	br(),
				    	button("Invoke")
				    	.withType("submit")
		    	)
		    )
		).render();
	}
	
	private static String postQueryOperation(Request req, Response rsp) {
    	return postOperation(req, rsp, Dispatcher.CHAINCODE_QUERY_OPERATION);
	}
	
	
	private static String postInvokeOperation(Request req, Response rsp) {
    	return postOperation(req, rsp, Dispatcher.CHAINCODE_INVOKE_OPERATION);
	}
	
	private static String postOperation(Request req, Response rsp, int type) {
		
		// get params
		String channel = req.params(":channel");
    	String cid = req.params(":cid");
    	String oid = req.queryParams("operationId");
    	String oargs = req.queryParams("operationArgs");
    	

		try {
    	
			// get the json arguments in array form
			ObjectMapper om = new ObjectMapper();
			String[] args = new String[] {};
			if (oargs != null && !oargs.isEmpty()) {
				args = om.readValue(oargs, String[].class);
			}
			
	
			
			// execute the function
			ChaincodeResult result = ci.verifyAndExecuteContract(type, channel, cid, oid, extractClientCrt(req), args);
			// check for chain code failure
			if (result == null || result.getStatus() == ChaincodeResult.CHAINCODE_FAILURE) {

				return throwError(req, rsp, new Exception("Chaincode failure when performing " + (type == Dispatcher.CHAINCODE_QUERY_OPERATION ? "query" : "invocation")));
			}
			
			// parse signatures and put them into a usable format
			List<String> signatures = result.getSignatures();
			DateFormat df = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
			String timestamp = null;
			if (result.getTimestamp() != null) {
				timestamp = df.format(result.getTimestamp());
			}

        	rsp.status(200);
			if (shouldReturnHtml(req)) {

            	rsp.type("text/html");
            	return body().with(
            			h3((type == Dispatcher.CHAINCODE_QUERY_OPERATION ? "Query" : "Invocation") + " result: OK"),
            			type == Dispatcher.CHAINCODE_QUERY_OPERATION ? div() : div("Timestamp: " + timestamp),
    					type == Dispatcher.CHAINCODE_QUERY_OPERATION ? div("Result: " + result.getContent()) : div(),
            			div("Peer endorsement signature(s): "),
            			br(),
            			each(signatures, sig -> div(
        					Base64.getEncoder().encodeToString(sig.getBytes())
            			).with(br(), br()))
            	).render();
	        	
			} else {

				// put arrays into json array and finalize object
            	rsp.type("application/json");
				ArrayNode jsonArray = om.valueToTree(signatures);
				ObjectNode jsonResult = om.createObjectNode();
				jsonResult.putArray("signatures").addAll(jsonArray);

				switch (type) {
					case Dispatcher.CHAINCODE_INVOKE_OPERATION:
						// invoke just needs timestamp for transaction confirmal
						jsonResult.put("timestamp", timestamp);
						break;
			
					case Dispatcher.CHAINCODE_QUERY_OPERATION:
						// query needs query results
						jsonResult.put("result", result.getContent());
						break;
				}
				return om.writer().writeValueAsString(jsonResult);
			}
		} catch (Exception e) {
			return throwError(req, rsp, e);
		}
	}
	
	/** AUX FUNCTIONS **/
		
	private static boolean shouldReturnHtml(Request request) {
	    String accept = request.headers("Accept");
	    return accept != null && accept.contains("text/html");
	}
	

	private static X509Certificate extractClientCrt(Request req) {
		X509Certificate[] crtList = (X509Certificate[]) req.raw().getAttribute("javax.servlet.request.X509Certificate");
		return crtList[0];
	}
	
	private static String throwError(Request req, Response rsp, Exception e)  {
		
		try { 

		// error handler
    	rsp.status(500);
		if (shouldReturnHtml(req)) {
			rsp.type("text/html");
			return body().with(
				    h3("API error while executing operation!"),
				    div().with(
				    	p(e.toString())
				    )
			).render();
		} else {
			rsp.type("application/json");
			ObjectMapper om = new ObjectMapper();
			ObjectNode jsonResult = om.createObjectNode();
			jsonResult.put("error", e.toString());
			return om.writer().writeValueAsString(jsonResult);
		}
		} catch (JsonProcessingException jpe) {
			e.printStackTrace();
		}
		return null;
	}


}
