import java.security.Security;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import static spark.Spark.get;
import static spark.Spark.port;
import static spark.Spark.post;
import static spark.Spark.put;
import static spark.Spark.delete;

public class API {
	

	public static final int API_PORT = 8080;

	public static void main(String[] args) {
		
		// set the security provider...
		Security.addProvider(new BouncyCastleProvider());
		
		// start embedded server at this port
        port(API_PORT);
 
        // set landing page
        get("/", (request, response) -> "Blockchain-supported Ledgering API for Decentralized Applications - v1.0");
		
		
	}

}
