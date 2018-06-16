package core;

import java.util.HashMap;
import java.util.Map;

import core.dto.Contract;

public class ContractInterpreter {
	
	private Map<String, Contract> contracts;	// list of contracts loaded in memory (CID -> contract)
												// if a contract isn't here, this interpreter will fetch it from the BC
	
	
	public ContractInterpreter() {
		contracts = new HashMap<String, Contract>();
	}

}
