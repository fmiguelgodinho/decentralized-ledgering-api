package endpoint.coap;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;

import org.hyperledger.fabric.sdk.exception.InvalidArgumentException;
import org.hyperledger.fabric.sdk.exception.ProposalException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mbed.coap.exception.CoapCodeException;
import com.mbed.coap.packet.Code;
import com.mbed.coap.server.CoapExchange;
import com.mbed.coap.utils.CoapResource;

import core.ContractInterpreter;
import core.Dispatcher;
import core.dto.ChaincodeResult;
import core.exception.InvalidContractPropertyException;
import core.exception.NonConformantContractException;
import endpoint.EntityType;

public class SmartContractCoAPResource extends CoapResource {

	private ContractInterpreter ci;
	private ExecutorService threadExecutor;

	public SmartContractCoAPResource(ContractInterpreter ci, int threadPoolSize) {
		super();
		this.ci = ci;
		this.threadExecutor = Executors.newFixedThreadPool(threadPoolSize);
	}

	// hlf query
	@Override
	public void get(CoapExchange ex) throws CoapCodeException {

		threadExecutor.submit(() -> {
			// get body and parse parameters
			String body = ex.getRequestBodyString();
			RequestParameters rp = parseParametersFromBody(body);

			// execute the function
			ChaincodeResult result = null;
			try {
				result = ci.verifyAndExecuteContract(EntityType.ENTITY_TYPE_IOT_DEVICE,
						Dispatcher.CHAINCODE_QUERY_OPERATION, rp.channel, rp.cid, rp.oid, null, // TODO: ignore this
																								// when CoAP!
						rp.args);
			} catch (ProposalException | InvalidArgumentException | ExecutionException | TimeoutException
					| NonConformantContractException | InvalidContractPropertyException e) {
				e.printStackTrace();
				ex.setResponseBody("Chaincode failure when performing query");
				ex.setResponseCode(Code.C500_INTERNAL_SERVER_ERROR);
				ex.sendResponse();
			}
			// check for chain code failure
			if (result == null || result.getStatus() == ChaincodeResult.CHAINCODE_FAILURE) {
				ex.setResponseBody("Chaincode failure when performing query");
				ex.setResponseCode(Code.C400_BAD_REQUEST);
				ex.sendResponse();
			}

			// this is overkill for CoAP, just return result
//			// parse signatures and put them into a usable format
//			List<String> signatures = result.getSignatures();
//			ArrayNode jsonArray = om.valueToTree(signatures);
//			jsonResult.putArray("signatures").addAll(jsonArray);

			// finalize response
			ObjectMapper om = new ObjectMapper();
			ObjectNode jsonResult = om.createObjectNode();
			jsonResult.put("result", result.getContent());

			// send it
			String responseBody = null;
			try {
				responseBody = om.writer().writeValueAsString(jsonResult);
			} catch (JsonProcessingException e) {
				e.printStackTrace();
				ex.setResponseBody("Failure when return reply to query");
				ex.setResponseCode(Code.C500_INTERNAL_SERVER_ERROR);
				ex.sendResponse();
			}

			ex.setResponseBody(responseBody);
			ex.setResponseCode(Code.C205_CONTENT);
			ex.sendResponse();

		});

	}

	// hlf invoke
	@Override
	public void put(CoapExchange ex) throws CoapCodeException {

		threadExecutor.submit(() -> {
			// get body and parse parameters
			String body = ex.getRequestBodyString();
			RequestParameters rp = parseParametersFromBody(body);

			// execute the function
			ChaincodeResult result = null;
			try {
				result = ci.verifyAndExecuteContract(EntityType.ENTITY_TYPE_IOT_DEVICE,
						Dispatcher.CHAINCODE_INVOKE_OPERATION, rp.channel, rp.cid, rp.oid, null, // TODO: ignore
																									// this when
																									// CoAP!
						rp.args);
			} catch (ProposalException | InvalidArgumentException | ExecutionException | TimeoutException
					| NonConformantContractException | InvalidContractPropertyException e) {
				e.printStackTrace();
				ex.setResponseBody("Chaincode failure when performing query");
				ex.setResponseCode(Code.C500_INTERNAL_SERVER_ERROR);
				ex.sendResponse();
			}
			// check for chain code failure
			if (result == null || result.getStatus() == ChaincodeResult.CHAINCODE_FAILURE) {
				ex.setResponseBody("Chaincode failure when performing query");
				ex.setResponseCode(Code.C400_BAD_REQUEST);
				ex.sendResponse();
			}

			// this is overkill for CoAP, just return an ok and timestamp
//			// parse signatures and put them into a usable format
//			List<String> signatures = result.getSignatures();
//			ArrayNode jsonArray = om.valueToTree(signatures);
//			jsonResult.putArray("signatures").addAll(jsonArray);
			
			// get timestamp
			DateFormat df = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
			String timestamp = null;
			if (result.getTimestamp() != null) {
				timestamp = df.format(result.getTimestamp());
			}

			// finalize response
			ObjectMapper om = new ObjectMapper();
			ObjectNode jsonResult = om.createObjectNode();
			// invoke just needs timestamp for transaction confirmal
			jsonResult.put("timestamp", timestamp);
			jsonResult.put("result", "OK");

			// send it
			String responseBody = null;
			try {
				responseBody = om.writer().writeValueAsString(jsonResult);
			} catch (JsonProcessingException e) {
				e.printStackTrace();
				ex.setResponseBody("Failure when returning reply to invoke");
				ex.setResponseCode(Code.C500_INTERNAL_SERVER_ERROR);
				ex.sendResponse();
			}

			ex.setResponseBody(responseBody);
			ex.setResponseCode(Code.C205_CONTENT);
			ex.sendResponse();

		});
	}

	/**
	 * AUX FNS
	 * -----------------------------------------------------------------------------------------------
	 **/

	// helper class
	class RequestParameters {
		String channel, cid, oid;
		String[] args;

		public RequestParameters() {
			this.channel = null;
			this.cid = null;
			this.oid = null;
			this.args = new String[] {};
		}
	}

	private RequestParameters parseParametersFromBody(String body) {

		// break params and sanitize string
		String[] params = body.trim().split("&");
		if (params.length == 0)
			throw new IllegalArgumentException("No parameters found on CoAP request body");

		RequestParameters rp = new RequestParameters();

		// read each param
		for (String param : params) {
			String[] argval = param.split("=");
			assert (argval.length == 2);

			if (argval[1] == null || argval[1].isEmpty())
				continue;

			switch (argval[0]) {
			case "channel":
				rp.channel = argval[1];
				break;
			case "contract":
				rp.cid = argval[1];
				break;
			case "operation":
				rp.oid = argval[1];
				break;
			case "args":
				// get the json arguments in array form
				ObjectMapper om = new ObjectMapper();
				try {
					rp.args = om.readValue(argval[1], String[].class);
				} catch (IOException e) {
					e.printStackTrace();
				}
				break;
			}
		}

		if (rp.channel == null)
			throw new IllegalArgumentException("Missing channel parameter from CoAP request body");
		if (rp.cid == null)
			throw new IllegalArgumentException("Missing contract parameter from CoAP request body");
		if (rp.oid == null)
			throw new IllegalArgumentException("Missing operation parameter from CoAP request body");

		return rp;
	}
}
