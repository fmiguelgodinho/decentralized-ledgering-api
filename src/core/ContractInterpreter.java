package core;

import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Signature;
import java.security.SignatureException;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.lang3.ArrayUtils;
import org.hyperledger.fabric.sdk.exception.InvalidArgumentException;
import org.hyperledger.fabric.sdk.exception.ProposalException;

import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;

import core.dto.ChaincodeResult;
import core.dto.Contract;
import integration.Dispatcher;

public class ContractInterpreter {
		
	
	// TODO: update contracts in db if updated in bc?
	private DBCollection contractCollection;
	private Dispatcher dpt;
	
	public ContractInterpreter(Configuration cfg, MongoClient dbClient, Dispatcher dpt) {
		DB db = dbClient.getDB(cfg.getString("mongo.database"));
		contractCollection = db.getCollection(cfg.getString("mongo.contractCollection"));
		this.dpt = dpt;
	}
	
	public void saveRawContractToDB(String channel, String cid, String rawContract) {
		
		// build obj being saved
		final DBObject contractObj = new BasicDBObject()
				.append("key", new BasicDBObject()
					.append("channelId", channel)
					.append("contractId", cid))
				.append("contract", rawContract);
		
		// save to db
		contractCollection.insert(contractObj);
	}
	
	public Contract getContract(String channel, String cid, X509Certificate clientCrt) throws ProposalException, InvalidArgumentException, ExecutionException, TimeoutException {
		// encapsulate in Contract object
		String rawContract = getContractRaw(channel, cid, clientCrt);
		return new Contract(rawContract);
	}
	
	public String getContractRaw(String channel, String cid, X509Certificate clientCrt) throws ProposalException, InvalidArgumentException, ExecutionException, TimeoutException {
		
		// try to first load it from db - quicker
		String rawContract = loadContractFromDb(channel, cid);
		if (rawContract != null)
			return rawContract;
		
		// it wasn't in db! try to retrieve it from blockchain - slower
		ChaincodeResult cr = loadContractFromBlockchain(channel, cid, clientCrt);
		if (cr.getStatus() == ChaincodeResult.CHAINCODE_SUCCESS) {
			rawContract = cr.getContent();
		}
		
		// save contract to db for future times
		if (rawContract != null)
			saveRawContractToDB(channel, cid, rawContract);
		return rawContract;
	}
	
	public boolean signContract(String channel, String cid, X509Certificate clientCrt, String signature) throws ProposalException, InvalidArgumentException, ExecutionException, TimeoutException {
		
		// get again the contract the client "supposedly" signed
    	String contract = getContractRaw(channel, cid, clientCrt);

		
    	// verify sig
    	boolean isCorrectlySigned = false;
    	try {
        	// remove 64 enconding
        	byte[] signatureBytes = Base64.getDecoder().decode(signature.getBytes("UTF-8"));
    		isCorrectlySigned = verifySignature(contract, clientCrt, signatureBytes);
    		
    		if (isCorrectlySigned) {
    			// call sign fn

    			byte[] pubKey = Base64.getEncoder().encode(clientCrt.getPublicKey().getEncoded());
    			
    			executeContractFunction(
    					Dispatcher.CHAINCODE_INVOKE_OPERATION, 
	        			channel, 
	        			cid,
	        			"signContract", 
	        			clientCrt,
	        			new String[] {
	    					signature
	        			}
    			);
    		}
    		
		} catch (InvalidKeyException | NoSuchAlgorithmException | SignatureException | UnsupportedEncodingException e) {
			e.printStackTrace();
		}
    	

    	
		return false;
	}
	
	private boolean verifySignature(String contract, X509Certificate signerCrt, byte[] signature) throws NoSuchAlgorithmException, InvalidKeyException, SignatureException {
		
		// produce an hash of the contract
		MessageDigest md = MessageDigest.getInstance("SHA-256");
		md.update(contract.getBytes());
		byte[] contractHashBytes = Base64.getEncoder().encode(md.digest());
		
    	Signature sig = Signature.getInstance(signerCrt.getSigAlgName());
		sig.initVerify(signerCrt.getPublicKey());
		
    	sig.update(contractHashBytes, 0, contractHashBytes.length);
    	return sig.verify(signature);
	}
	
	private String loadContractFromDb(String channel, String cid) {
		
		// recreate key
		final DBObject key = new BasicDBObject()
				.append("key", new BasicDBObject()
					.append("channelId", channel)
					.append("contractId", cid));
		
		// search and return contract if we have it in db
		DBCursor cursor = contractCollection.find(key);
		
		if (cursor.count() == 0) 
			return null;
		
		return (String) cursor.one().get("contract");
	}
	
	private ChaincodeResult loadContractFromBlockchain(String channel, String cid, X509Certificate clientCrt) throws ProposalException, InvalidArgumentException, ExecutionException, TimeoutException {
		
    	// query the chaincode
    	return executeContractFunction(
			Dispatcher.CHAINCODE_QUERY_OPERATION, 
			channel,
			cid, 
			"getContractDefinition", 
			clientCrt,
			new String[] {}								// empty args
		);
	}
	
	public ChaincodeResult executeContractFunction(int op, String channelName, String cid, String function, X509Certificate clientCrt, String[] args) throws ProposalException, InvalidArgumentException, ExecutionException, TimeoutException {
		try {

			byte[] pubKey = Base64.getEncoder().encode(clientCrt.getPublicKey().getEncoded());

			// put signature on first argument!
			args = ArrayUtils.insert(0, args, new String(pubKey));
			
			return dpt.callChaincodeFunction(
					op, 
					channelName,
					cid, 
					function, 
					args
			);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		return null;
	}

	public ChaincodeResult getContractSignature(String channel, String cid, X509Certificate clientCrt) throws ProposalException, InvalidArgumentException, ExecutionException, TimeoutException {
    	// query the chaincode
    	return executeContractFunction(
			Dispatcher.CHAINCODE_QUERY_OPERATION, 
			channel,
			cid, 
			"getContractSignature", 
			clientCrt,
			new String[] {}								// empty args
		);
	}
	
//	public boolean deployContract(String cid, String cver, File csfolder, String cpath, String cspecs) throws InvalidContractException {
//		
//		// set the correct channel
////		dpt.changeChannel(channel);
//		
//		Contract contract = new Contract(cspecs);
//		if (!contract.conformsToStandard())
//			throw new InvalidContractException("Contract doesn't conform to standard!");
//		
//		// install chaincode
//		try {
//			dpt.install(cid, cver, csfolder, cpath, new String[] { cspecs }, contract.getExtendedContractProperties());
//		} catch (InvalidArgumentException | ProposalException | IOException e) {
//			e.printStackTrace();
//		}
//		
//		// TODO: save to db after successful installation and instantiation
//		
//		return true;
//	}

}
