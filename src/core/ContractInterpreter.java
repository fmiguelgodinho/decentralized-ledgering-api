package core;

import java.net.UnknownHostException;

import com.mongodb.MongoClient;

public class ContractInterpreter {
	
//	private Map<String, Contract> contracts;	// list of contracts loaded in memory (CID -> contract)
												// if a contract isn't here, this interpreter will fetch it from the BC
	
	private MongoClient db;
	
	public ContractInterpreter(MongoClient db) throws UnknownHostException {
//		contracts = new HashMap<String, Contract>();
		this.db = db;
	}

}
