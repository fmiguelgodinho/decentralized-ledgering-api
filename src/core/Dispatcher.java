package core;

import java.io.File;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.commons.configuration2.Configuration;
import org.apache.log4j.Logger;
import org.hyperledger.fabric.sdk.BlockEvent.TransactionEvent;
import org.hyperledger.fabric.sdk.ChaincodeID;
import org.hyperledger.fabric.sdk.Channel;
import org.hyperledger.fabric.sdk.EventHub;
import org.hyperledger.fabric.sdk.HFClient;
import org.hyperledger.fabric.sdk.Orderer;
import org.hyperledger.fabric.sdk.Peer;
import org.hyperledger.fabric.sdk.ProposalResponse;
import org.hyperledger.fabric.sdk.QueryByChaincodeRequest;
import org.hyperledger.fabric.sdk.TransactionProposalRequest;
import org.hyperledger.fabric.sdk.exception.CryptoException;
import org.hyperledger.fabric.sdk.exception.InvalidArgumentException;
import org.hyperledger.fabric.sdk.exception.ProposalException;
import org.hyperledger.fabric.sdk.exception.TransactionException;
import org.hyperledger.fabric.sdk.security.CryptoSuite;

import com.google.protobuf.ByteString;

import core.dto.ChaincodeResult;
import core.dto.HLFUser;
import util.NodeConnection;

public class Dispatcher {
	
	
	public static final int CHAINCODE_QUERY_OPERATION = 0;
	public static final int CHAINCODE_INVOKE_OPERATION = 1;
	
	
    private static final Logger log = Logger.getLogger(Dispatcher.class);
    
    private HFClient client;
    private ConcurrentMap<String, Collection<Peer>> signers; // per contract
    private ConcurrentMap<String, Collection<Orderer>> orderers; // per contract
    private Configuration cfg;
    
    public Dispatcher(Configuration cfg, NodeConnection[] bootstrapNodes) throws Exception {
    	
    	this.cfg = cfg;
    	
        // register and enroll new user
        HLFUser appUser = getUser(
    		cfg.getString("hlf.client.crtPath"), 
    		cfg.getString("hlf.client.keyPath"),
        	cfg.getString("hlf.client.username"), 
        	cfg.getString("hlf.client.mspid"), 
        	cfg.getString("hlf.client.org")
         );
        log.info(appUser);
       
        // get HFC client instance
        client = getHfClient();
        // set user context
        client.setUserContext(appUser);
        // set threshsig group key for when needed
        client.getCryptoSuite().setThreshSigGroupKey(cfg.getString("crypto.threshsig.groupKey").getBytes());
        
        // init contract nodes maps
        signers = new ConcurrentHashMap<String, Collection<Peer>>();
        orderers = new ConcurrentHashMap<String, Collection<Orderer>>();
        
        // create the config channel
        createChannel(
    		cfg.getString("hlf.channelName"),
    		bootstrapNodes
        );
    }
    
    // use HLFJavaClient.CHAINCODE_QUERY_OPERATION or HLFJavaClient.CHAINCODE_INVOKE_OPERATION
    public ChaincodeResult callChaincodeFunction(int op, String channelName, String chaincodeId, String chaincodeFn, String[] chaincodeArgs) throws InterruptedException, ProposalException, InvalidArgumentException, ExecutionException, TimeoutException {
    	
    	ChaincodeResult cr = new ChaincodeResult(ChaincodeResult.CHAINCODE_FAILURE);
    	
		// set correct channel
		Channel channel = changeChannel(channelName);
    	
    	try {
    		// call corresponding chaincode operation
    		switch (op) {
    		
	    		case CHAINCODE_QUERY_OPERATION:
	    			cr = query(channel, chaincodeId, chaincodeFn, chaincodeArgs);
	    			break;
	    			
	    		case CHAINCODE_INVOKE_OPERATION:
	    			cr = invoke(channel, chaincodeId, chaincodeFn, chaincodeArgs);
	    			break;
	    			
				default:
	    			throw new IllegalArgumentException("Unrecognized operation: " + op);
    		}
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		} finally {
//			Thread.sleep(cfg.getLong("hlf.chaincode.callInterval"));
		}
    	
    	return cr;
    }
    
    public Channel changeChannel(String channelName) throws IllegalArgumentException {
    	
    	Channel c = client.getChannel(channelName);
    	if (c == null) {
    		throw new IllegalArgumentException("No such channel exists: " + channelName);
    	}
    	
    	// ret current channel
    	return c;
    	
    }
    
    public void updateChannelForContract(String channelName, String contractId, NodeConnection[] newNodesOnChannel) throws InvalidArgumentException, TransactionException {
    	
    	// get channel to update and its nodes
    	Channel oldChannel = client.getChannel(channelName);
    	Collection<Peer> existingPeers = oldChannel.getPeers();
    	Collection<Orderer> existingOrderers = oldChannel.getOrderers();
    	// remove it
    	client.removeChannel(oldChannel); // don't ask, it works and preserves old nodes
    	
    	// clear any old refs to signers and orderers we might have
    	signers.remove(contractId);
    	orderers.remove(contractId);

		// create channel and add nodes nodes
    	Channel channel = client.newChannel(channelName);
    	
    	// add new nodes
    	for (int i = 0; i < newNodesOnChannel.length; i++) {
    		
    		File tlsCrt = Paths.get(newNodesOnChannel[i].tlsCrtPath).toFile();
            
            if (!tlsCrt.exists())
            	throw new RuntimeException("Missing TLS cert files");
            
            Properties secPeerProperties = new Properties();
            secPeerProperties.setProperty("hostnameOverride", newNodesOnChannel[i].name);
            secPeerProperties.setProperty("sslProvider", cfg.getString("hlf.communication.sslProvider"));
            secPeerProperties.setProperty("negotiationType", cfg.getString("hlf.communication.negotiationMode"));
            secPeerProperties.setProperty("pemFile", tlsCrt.getAbsolutePath());
            
            if (newNodesOnChannel[i].type == NodeConnection.PEER_TYPE) {
            	// peer name and endpoint in network
	            Peer peer = client.newPeer(newNodesOnChannel[i].name, "grpcs://" + newNodesOnChannel[i].host + ":"  + newNodesOnChannel[i].port, secPeerProperties);
	            channel.addPeer(peer);
            } else if (newNodesOnChannel[i].type == NodeConnection.ORDERER_TYPE) {
            	// orderer name and endpoint in network
                Orderer orderer = client.newOrderer(newNodesOnChannel[i].name, "grpcs://" + newNodesOnChannel[i].host + ":"  + newNodesOnChannel[i].port, secPeerProperties);
                channel.addOrderer(orderer);
            }
    	}

    	
    	// init channel, finally
        channel.initialize();
    }
    
    private void createChannel(String newChannelName, NodeConnection[] nodesOnChannel) throws InvalidArgumentException, TransactionException {
        	
		// create channel and add nodes nodes
    	Channel channel = client.newChannel(newChannelName);
    	
    	for (int i = 0; i < nodesOnChannel.length; i++) {
    		File tlsCrt = Paths.get(nodesOnChannel[i].tlsCrtPath).toFile();
            
            if (!tlsCrt.exists())
            	throw new RuntimeException("Missing TLS cert files");
            
            Properties secPeerProperties = new Properties();
            secPeerProperties.setProperty("hostnameOverride", nodesOnChannel[i].name);
            secPeerProperties.setProperty("sslProvider", cfg.getString("hlf.communication.sslProvider"));
            secPeerProperties.setProperty("negotiationType", cfg.getString("hlf.communication.negotiationMode"));
            secPeerProperties.setProperty("pemFile", tlsCrt.getAbsolutePath());
            
            if (nodesOnChannel[i].type == NodeConnection.PEER_TYPE) {
            	// peer name and endpoint in network
	            Peer peer = client.newPeer(nodesOnChannel[i].name, "grpcs://" + nodesOnChannel[i].host + ":"  + nodesOnChannel[i].port, secPeerProperties);
	            channel.addPeer(peer);
	            if (i == 0) {
	            	// eventhub on peer endpoints
	            	EventHub eventHub = client.newEventHub("eventhub0" + i, "grpcs://" + nodesOnChannel[i].host + ":" + nodesOnChannel[i].eventHubPort, secPeerProperties);
	            	channel.addEventHub(eventHub);
	            }
            } else if (nodesOnChannel[i].type == NodeConnection.ORDERER_TYPE) {
            	// orderer name and endpoint in network
                Orderer orderer = client.newOrderer(nodesOnChannel[i].name, "grpcs://" + nodesOnChannel[i].host + ":"  + nodesOnChannel[i].port, secPeerProperties);
                channel.addOrderer(orderer);
            }
    	}
    	
    	// init channel, finally
        channel.initialize();
    }

    private ChaincodeResult query(Channel channel, String chaincodeId, String chaincodeFn, String[] chaincodeArgs) throws ProposalException, InvalidArgumentException, NoSuchAlgorithmException {
    	

    	Collection<ProposalResponse> successful = new LinkedList<ProposalResponse>();
    	Collection<ProposalResponse> failed = new LinkedList<ProposalResponse>();
        
        // create chaincode request
        QueryByChaincodeRequest qpr = client.newQueryProposalRequest();
        
        // build cc id providing the chaincode name. Version is omitted here.
        ChaincodeID CCId = ChaincodeID.newBuilder().setName(chaincodeId).build();
        qpr.setChaincodeID(CCId);
        
        // check if there are signers for contract (if there aren't, this is a first interaction)
        Collection<Peer> signerNodes = signers.get(chaincodeId);
        if (signerNodes == null || signerNodes.isEmpty()) {
        	// there are not. let us default to the bootstrap nodes on the channel
        	signerNodes = channel.getPeers();
        }
        
        // CC function to be called
        qpr.setFcn(chaincodeFn);
        qpr.setArgs(chaincodeArgs);
        qpr.setProposalWaitTime(cfg.getLong("hlf.proposal.timeout"));
        Collection<ProposalResponse> responses = channel.queryByChaincode(qpr, signerNodes);

        log.info("Sending query request, function '" + chaincodeFn + "' with arguments ['" + String.join("', '", chaincodeArgs) + "'], through chaincode '" + chaincodeId + "'...");
        
        // parse responses
        String responseString = null;
        List<ByteString> signatureStrings = new ArrayList<ByteString>(responses.size());
        for (ProposalResponse rsp : responses) {
        	// if valid
        	if (rsp.isVerified() && rsp.getStatus() == ProposalResponse.Status.SUCCESS) {
        		responseString = rsp.getProposalResponse().getResponse().getPayload().toStringUtf8();
        		signatureStrings.add(rsp.getProposalResponse().getEndorsement().getSignature());
        		successful.add(rsp);
        	} else {
        		failed.add(rsp);
        	}
        }
        
        log.info("Received " + responses.size() + " query proposal responses. Successful: " + successful.size() + " . Failed: " + failed.size());
        log.info("Signature verification is ok!");
        
        // if we obtain a majority of faults => exit error
        if (failed.size() >= successful.size()) {
        	throw new RuntimeException("Too many peers failed the response!");
        }
        
        
        return new ChaincodeResult(ChaincodeResult.CHAINCODE_SUCCESS, responseString, signatureStrings);
    }
    
    private ChaincodeResult invoke(Channel channel, String chaincodeId, String chaincodeFn, String[] chaincodeArgs) throws ProposalException, InvalidArgumentException, InterruptedException, ExecutionException, TimeoutException {
    	

    	Collection<ProposalResponse> successful = new LinkedList<ProposalResponse>();
    	Collection<ProposalResponse> failed = new LinkedList<ProposalResponse>();
        
        // create chaincode request
        TransactionProposalRequest tpr = client.newTransactionProposalRequest();
        
        // build cc id providing the chaincode name. Version is omitted here.
        ChaincodeID CCId = ChaincodeID.newBuilder().setName(chaincodeId).build();
        
        // check if there are signers for contract (if there aren't, this is a first interaction)
        Collection<Peer> signerNodes = signers.get(chaincodeId);
        if (signerNodes == null || signerNodes.isEmpty()) {
        	// there are not. let us default to the bootstrap nodes on the channel
        	signerNodes = channel.getPeers();
        }

        // CC function to be called
        tpr.setChaincodeID(CCId);
        tpr.setFcn(chaincodeFn);
        tpr.setArgs(chaincodeArgs);
        tpr.setProposalWaitTime(cfg.getLong("hlf.proposal.timeout"));
        Collection<ProposalResponse> responses = channel.sendTransactionProposal(tpr, signerNodes);
        
        log.info("Sending transaction proposal, function '" + chaincodeFn + "' with arguments ['" + String.join("', '", chaincodeArgs) + "'], through chaincode '" + chaincodeId + "'...");
       
        // parse responses
        List<ByteString> signatureStrings = new ArrayList<ByteString>(responses.size());
        for (ProposalResponse rsp : responses) {
        	// if valid
        	if (rsp.isVerified() && rsp.getStatus() == ProposalResponse.Status.SUCCESS) {
        		signatureStrings.add(rsp.getProposalResponse().getEndorsement().getSignature());
        		successful.add(rsp);
        	} else {
        		failed.add(rsp);
        	}
        }
        log.info("Signature verification is ok!");
        log.info("Collecting endorsements and sending transaction...");


        // check if there are orderers for contract (if there aren't, this is a first interaction)
        Collection<Orderer> ordererNodes = orderers.get(chaincodeId);
        if (ordererNodes == null || ordererNodes.isEmpty()) {
        	// there are not. let us default to the bootstrap nodes on the channel
        	ordererNodes = channel.getOrderers();
        }
        
        // send transaction with endorsements
        TransactionEvent te = channel.sendTransaction(responses, ordererNodes).get(
			cfg.getLong("hlf.transaction.timeout"), 
			TimeUnit.MILLISECONDS
		);

        log.info("Transaction sent.");
        
        return new ChaincodeResult(ChaincodeResult.CHAINCODE_SUCCESS, te.getTimestamp(), signatureStrings);
    }
    

    /**
     * Create new HLF client
     *
     * @return new HLF client instance. Never null.
     * @throws CryptoException
     * @throws InvalidArgumentException
     */
    static HFClient getHfClient() throws Exception {
        // initialize default cryptosuite
        CryptoSuite cryptoSuite = CryptoSuite.Factory.getCryptoSuite();
        // setup the client
        HFClient client = HFClient.createNewInstance();
        client.setCryptoSuite(cryptoSuite);
        return client;
    }
    
    static HLFUser getUser(String certPath, String keyPath, String userId, String mspId, String org) throws Exception {
        HLFUser appUser = null;//tryDeserialize(userId);
        if (appUser == null) {
            //String enrollmentSecret = caClient.register(rr, registrar);
            //Enrollment enrollment = caClient.enroll(userId, enrollmentSecret);
            appUser = new HLFUser(userId, org, mspId, certPath, keyPath);
            //serialize(appUser);
        }
        return appUser;
    }
    
}