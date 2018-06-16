import java.security.Security;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import static spark.Spark.*;
import spark.Request;

import static j2html.TagCreator.*;

public class API {
	

	public static final int API_PORT = 8080;

	public static void main(String[] args) {
		
		// set the security provider...
		Security.addProvider(new BouncyCastleProvider());
		
		// start embedded server at this port
        port(API_PORT);
		
        path("/api", () -> {
//        	before((request, response) -> {
//        	    boolean authenticated;
//        	    // ... check if authenticated
//        	    if (!authenticated) {
//        	        halt(401, "You are not welcome here");
//        	    }
//        		// log something
//        		log....
//        	});
        	
        	get("/", (request, response) -> "Blockchain-supported Ledgering API for Decentralized Applications - v1.0");
        	
            path("/contract", () -> {
                get("/:cid", (request, response) -> {
                	response.status(200);
                	response.type("text/html");
                	String cid = request.params(":cid");
                	return viewContract(cid);
                });
                get("/:cid/query-all-tx", (request, response) -> {
                	response.status(200);
                	response.type("text/html");
                	String cid = request.params(":cid");
                	return viewTransactionList(cid);
                });
                get("/:cid/query-tx/:txid", (request, response) -> {
                	response.status(200);
                	response.type("text/html");
                	String cid = request.params(":cid");
                	String txid = request.params(":txid");
                	return viewTransactionDetails(cid, txid);
                });
                get("/:cid/invoke-operation", (request, response) -> {
                	response.status(200);
                	response.type("text/html");
                	String cid = request.params(":cid");
                	return formInvokeOperation(cid);
                });
            });
        });
        
	}
	
	/* ---------------------- API METHODS --------------------- */
	
	private static String viewContract(String cid) {
		
		// execute action
		
		// return html
		return body().with(
		    h3("Contract ID: " + cid),
		    div().with(
		    	p("Below is all metadata related with the contract:")
		    )
//			      div().with(
//			          model.getAllPosts().stream().map((post) ->
//			                div().with(
//			                        h2(post.getTitle()),
//			                        p(post.getContent()),
//			                        ul().with(post.getCategories().stream().map((category) ->
//			                              li(category)).collect(Collectors.toList())
//			                        )
//			                )
//			          ).collect(Collectors.toList())
//			      )
		).render();
	}
	
	private static String viewTransactionList(String cid) {
		
		// execute action
		
		// return html
		return body().with(
		    h3("Contract ID: " + cid),
		    div().with(
		    	p("Below is a list of transactions/operations related with the contract:")
		    )
		).render();
	}
	
	private static String viewTransactionDetails(String cid, String txid) {
		
		// execute action
		
		// return html
		return body().with(
		    h3("Transaction ID: " + txid),
		    div().with(
		    	p("Below is all data related with the specified transaction:")
		    )
		).render();
	}
	
	private static String formInvokeOperation(String cid) {
		
		// execute action
		
		// return html
		return body().with(
		    h3("Contract ID: " + cid),
		    div().with(
		    	p("Fill in invocation details. View contract details if you need to know what operations are supported:"),
		    	br(),
		    	form()
		    	.withMethod("PUT")
		    	.withAction("//TODO")
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
	
	private static boolean shouldReturnHtml(Request request) {
	    String accept = request.headers("Accept");
	    return accept != null && accept.contains("text/html");
	}


}
