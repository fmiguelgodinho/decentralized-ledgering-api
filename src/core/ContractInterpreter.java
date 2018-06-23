package core;

import com.mongodb.MongoClient;

import core.dto.Contract;
import integration.Dispatcher;

public class ContractInterpreter {
	
//	private Map<String, Contract> contracts;	// list of contracts loaded in memory (CID -> contract)
												// if a contract isn't here, this interpreter will fetch it from the BC
	
	private MongoClient db;
	private Dispatcher dpt;
	
	public ContractInterpreter(MongoClient db, Dispatcher dpt) {
//		contracts = new HashMap<String, Contract>();
		this.db = db;
		this.dpt = dpt;
	}
	
	private Contract loadContractFromDb(String channel, String cid) {
		return null;
	}
	
	private Contract loadContractFromBlockchain(String channel, String cid) {
		
		// set the correct channel
		dpt.changeChannel(channel);
		
    	// query the chaincode
    	String rawJsonContract = null;
    	try {
    		rawJsonContract = dpt.callChaincodeFunction(
				Dispatcher.CHAINCODE_QUERY_OPERATION, 
				cid, 
				"getContractDefinition", 
				new String[] {}								// empty args
			);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
    	
		return new Contract(rawJsonContract);
	}

}
