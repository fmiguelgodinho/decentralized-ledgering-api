package endpoint.coap;

import java.io.IOException;

import org.apache.commons.configuration2.Configuration;

import com.mbed.coap.server.CoapHandler;
import com.mbed.coap.server.CoapServer;

import core.ContractInterpreter;

public class CoAPServer {
	
	private Configuration cfg;
	private ContractInterpreter ci;
	
	public CoAPServer(Configuration cfg, ContractInterpreter ci) {
		this.cfg = cfg;
		this.ci = ci;
	}
	
	public void start() throws IllegalStateException, IOException {
		CoapServer server = CoapServer.builder().transport(
	        	cfg.getInt("api.coap.port")
	    ).build();

		// setup handler for contract query/invoke
		CoapHandler handler = new SmartContractCoAPResource(ci);
		server.addRequestHandler("/contract", handler);
		server.start();
	}

}
