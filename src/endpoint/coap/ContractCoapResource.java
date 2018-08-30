package endpoint.coap;

import com.mbed.coap.exception.CoapCodeException;
import com.mbed.coap.packet.Code;
import com.mbed.coap.server.CoapExchange;
import com.mbed.coap.utils.CoapResource;

public class ContractCoapResource extends CoapResource {

	// hlf query
	@Override
	public void get(CoapExchange ex) throws CoapCodeException {
		ex.setResponseBody("Hello World");
		ex.setResponseCode(Code.C205_CONTENT);
		ex.sendResponse();
	}

	// hlf invoke
	@Override
	public void put(CoapExchange ex) throws CoapCodeException {
		String body = ex.getRequestBodyString();
		ex.setResponseCode(Code.C204_CHANGED);
		ex.sendResponse();
	}

}
