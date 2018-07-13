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
	
	public void saveRawContractToDB(String channel, String cid, Contract contract) {
		
		final DBObject key = new BasicDBObject()
				.append("channelId", channel)
				.append("contractId", cid);
		
		// build obj being saved
		final DBObject contractObj = new BasicDBObject()
				.append("contract", contract.getRawRepresentation())
				.append("signature", contract.getSignature());
		
		// save to db (upsert)
		contractCollection.update(
				new BasicDBObject().append("_id", key), 
				contractObj, 
				true, 
				false
		);
	}
	
	public Contract getContract(String channel, String cid, X509Certificate clientCrt) throws ProposalException, InvalidArgumentException, ExecutionException, TimeoutException {
		
		// try to first load it from db - quicker
		Contract contract = loadContractFromDb(channel, cid);
		if (contract != null)
			return contract;
		
		// it wasn't in db! try to retrieve it from blockchain - slower
		contract = loadContractFromBlockchain(channel, cid, clientCrt);
		
		// save contract to db for future times
		if (contract != null)
			saveRawContractToDB(channel, cid, contract);
		return contract;
	}
	
	public boolean signContract(String channel, String cid, X509Certificate clientCrt, String signature) throws ProposalException, InvalidArgumentException, ExecutionException, TimeoutException {
		
		// get again the contract the client "supposedly" signed
    	Contract contract = getContract(channel, cid, clientCrt);
    	
    	// verify sig
    	boolean isCorrectlySigned = false;
    	try {
        	// remove 64 enconding
        	byte[] signatureBytes = Base64.getDecoder().decode(signature.getBytes("UTF-8"));
    		isCorrectlySigned = verifySignature(contract, clientCrt, signatureBytes);
    		
    		if (isCorrectlySigned) {
    			
    			// call sign fn
    			ChaincodeResult cr = executeContractFunction(
					Dispatcher.CHAINCODE_INVOKE_OPERATION, 
        			channel, 
        			cid,
        			"signContract", 
        			clientCrt,
        			new String[] {
    					signature
        			}
    			);
    			
    			// save to db
    			if (cr.getStatus() == ChaincodeResult.CHAINCODE_SUCCESS) {
    				contract.sign(signature);
    				saveRawContractToDB(channel, cid, contract);
    			}
    			return true;
    		}
    		
		} catch (InvalidKeyException | NoSuchAlgorithmException | SignatureException | UnsupportedEncodingException e) {
			e.printStackTrace();
		}
    	

    	
		return false;
	}
	
	private boolean verifySignature(Contract contract, X509Certificate signerCrt, byte[] signature) throws NoSuchAlgorithmException, InvalidKeyException, SignatureException {
		
		// produce an hash of the contract
		MessageDigest md = MessageDigest.getInstance("SHA-256");
		md.update(contract.getRawRepresentation().getBytes());
		byte[] contractHashBytes = Base64.getEncoder().encode(md.digest());
		
    	Signature sig = Signature.getInstance(signerCrt.getSigAlgName());
		sig.initVerify(signerCrt.getPublicKey());
		
    	sig.update(contractHashBytes, 0, contractHashBytes.length);
    	return sig.verify(signature);
	}
	
	private Contract loadContractFromDb(String channel, String cid) {
		
		// recreate key
		final DBObject key = new BasicDBObject()
				.append("_id", new BasicDBObject()
					.append("channelId", channel)
					.append("contractId", cid));
		
		// search and return contract if we have it in db
		DBCursor cursor = contractCollection.find(key);
		
		if (cursor.count() == 0) 
			return null;
		
		DBObject result = cursor.one();

		String contract = (String) result.get("contract");
		String signature = (String) result.get("signature");
		
		if (contract == null)
			return null;
		
		Contract contractObj = new Contract(contract);
		contractObj.sign(signature);
		
		return contractObj;
	}
	
	private Contract loadContractFromBlockchain(String channel, String cid, X509Certificate clientCrt) throws ProposalException, InvalidArgumentException, ExecutionException, TimeoutException {
		
    	// query the chaincode
    	ChaincodeResult cr1 = executeContractFunction(
			Dispatcher.CHAINCODE_QUERY_OPERATION, 
			channel,
			cid, 
			"getContractDefinition", 
			clientCrt,
			new String[] {}								// empty args
		);
    	
    	ChaincodeResult cr2 = executeContractFunction(
			Dispatcher.CHAINCODE_QUERY_OPERATION, 
			channel,
			cid,
			"getContractSignature", 
			clientCrt,
			new String[] {}								// empty args
		);
    	
    	if (cr1.getStatus() == ChaincodeResult.CHAINCODE_SUCCESS && cr2.getStatus() == ChaincodeResult.CHAINCODE_SUCCESS) {
    		return new Contract(cr1.getContent(), cr2.getContent());
    	}
    	
    	// shouldn't get here
    	return null;
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
					args,
					1
			);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		return null;
	}

}
