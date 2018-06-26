package integration;

import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
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
import org.hyperledger.fabric.sdk.InstallProposalRequest;
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
import util.NodeConnection;
import util.Pair;

public class Dispatcher {
	
	
	public static final int CHAINCODE_QUERY_OPERATION = 0;
	public static final int CHAINCODE_INVOKE_OPERATION = 1;
//	public static final int CHAINCODE_INSTALL_OPERATION = 2;
//	public static final int CHAINCODE_INSTANTIATE_OPERATION = 2;
	
	
    private static final Logger log = Logger.getLogger(Dispatcher.class);
    
    private HFClient client;
    private Map<String,Pair<Channel, NodeConnection[]>> channels;
    private String currChannel;
    private Configuration cfg;
    
    public Dispatcher(Configuration cfg, NodeConnection[] nodesOnChannel) throws Exception {
    	
    	this.cfg = cfg;
    	
//      // create fabric-ca client
//      HFCAClient caClient = getHfCaClient("http://localhost:7054", null);
//
// 		// enroll or load admin
//      AppUser admin = getAdmin(caClient);
//      log.info(admin);

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

        // init channel map
        channels = new HashMap<String,Pair<Channel, NodeConnection[]>>();
        this.setChannel(
    		cfg.getString("hlf.channelName"),
    		nodesOnChannel
        );
    }
    
    // use HLFJavaClient.CHAINCODE_QUERY_OPERATION or HLFJavaClient.CHAINCODE_INVOKE_OPERATION
    public ChaincodeResult callChaincodeFunction(int op, String chaincodeId, String chaincodeFn, String[] chaincodeArgs) throws InterruptedException {
    	
    	ChaincodeResult cr = new ChaincodeResult(ChaincodeResult.CHAINCODE_FAILURE);
    	
    	try {
    		// call corresponding chaincode operation
    		switch (op) {
    		
	    		case CHAINCODE_QUERY_OPERATION:
	    			cr = query(chaincodeId, chaincodeFn, chaincodeArgs);
	    			break;
	    			
	    		case CHAINCODE_INVOKE_OPERATION:
	    			cr = invoke(chaincodeId, chaincodeFn, chaincodeArgs);
	    			break;
	    			
				default:
	    			throw new IllegalArgumentException("Unrecognized operation: " + op);
    		}

			
		} catch (ProposalException | InvalidArgumentException | ExecutionException | TimeoutException e) {
			e.printStackTrace();
		} finally {
//			Thread.sleep(cfg.getLong("hlf.chaincode.callInterval"));
		}
    	
    	return cr;
    }
    
    public void changeChannel(String channelName) throws IllegalArgumentException {
    	
    	if (!channels.containsKey(channelName)) {
    		throw new IllegalArgumentException("No such channel exists: " + channelName);
    	}
    	
    	// set current channel
    	currChannel = channelName;
    	
    }
    
    public void setChannel(String newChannelName, NodeConnection[] nodesOnChannel) throws InvalidArgumentException, TransactionException {
    	
    	Pair<Channel,NodeConnection[]> existingChannelCfg = channels.get(newChannelName);
    	
    	// if channel does not exist, initialize new channel on the client side
    	if (existingChannelCfg == null) {
        	Channel newChannel = initChannel(newChannelName, nodesOnChannel);
            channels.put(newChannelName, new Pair(newChannel, nodesOnChannel));
    	} else {
    		// just update peer connections
    		existingChannelCfg.setRight(nodesOnChannel);
            channels.put(newChannelName, existingChannelCfg);
    	}
    	// set current channel
    	currChannel = newChannelName;
    }
    
    // has to be admin
    public void install(String chaincodeId, String chaincodeVersion, File chaincodeFile) throws InvalidArgumentException, IOException, ProposalException {
    	
    	Collection<ProposalResponse> successful = new LinkedList<ProposalResponse>();
    	Collection<ProposalResponse> failed = new LinkedList<ProposalResponse>();
    	
        // get channel instance from client
        Channel channel = channels.get(currChannel).getLeft();
        
        // create install proposal request
    	InstallProposalRequest ipr = client.newInstallProposalRequest();
    	
        // build cc id providing the chaincode name
        ChaincodeID CCId = ChaincodeID.newBuilder().setName(chaincodeId).build();
        ipr.setChaincodeID(CCId);
        ipr.setChaincodeVersion(chaincodeVersion);
        
        // set chaincode input stream
        ipr.setChaincodeSourceLocation(chaincodeFile);
        
//        TODO path on docker, and policy based on interpretation
//        ipr.setChaincodePath(chaincodePath);
//        ipr.setChaincodeEndorsementPolicy(policy);
        
        // TODO: MAYBE SEND ONLY TO PEERS WHO IS ADMIN FOR
        Collection<ProposalResponse> res = client.sendInstallProposal(ipr, channel.getPeers());

        for (ProposalResponse pres : res) {
            if (pres.getStatus() == ProposalResponse.Status.SUCCESS) {
                log.info("Successful installed proposal response Txid: " + pres.getTransactionID() + " from peer " + pres.getPeer().getName());
                successful.add(pres);
            } else {
                failed.add(pres);
            }
        }

        log.info("Received " + res.size() + " install proposal responses. Successful: " + successful.size() + " . Failed: " + failed.size());

//        if (failed.size() > 0) {
//            ProposalResponse first = failed.iterator().next();
//            fail("Not enough endorsers for install :" + successful.size() + ".  " + first.getMessage());
//        }
        
        
        
    }

    private ChaincodeResult query(String chaincodeId, String chaincodeFn, String[] chaincodeArgs) throws ProposalException, InvalidArgumentException {
    	

    	Collection<ProposalResponse> successful = new LinkedList<ProposalResponse>();
    	Collection<ProposalResponse> failed = new LinkedList<ProposalResponse>();
    	
        // get channel instance from client
        Channel channel = channels.get(currChannel).getLeft();
        
        // create chaincode request
        QueryByChaincodeRequest qpr = client.newQueryProposalRequest();
        
        // build cc id providing the chaincode name. Version is omitted here.
        ChaincodeID CCId = ChaincodeID.newBuilder().setName(chaincodeId).build();
        qpr.setChaincodeID(CCId);
        
        // CC function to be called
        qpr.setFcn(chaincodeFn);
        qpr.setArgs(chaincodeArgs);
        qpr.setProposalWaitTime(cfg.getLong("hlf.proposal.timeout"));
        Collection<ProposalResponse> responses = channel.queryByChaincode(qpr, channel.getPeers());

        log.info("Sending query request, function '" + chaincodeFn + "' with arguments ['" + String.join("', '", chaincodeArgs) + "'], through chaincode '" + chaincodeId + "'...");
        
        // parse responses
        String responseString = null;
        for (ProposalResponse rsp : responses) {
        	
        	// if valid
        	if (rsp.isVerified() && rsp.getStatus() == ProposalResponse.Status.SUCCESS) {
        		responseString = rsp.getProposalResponse().getResponse().getPayload().toStringUtf8();
        		successful.add(rsp);
        	} else {
        		failed.add(rsp);
        	}
        }
        
        log.info("Received " + responses.size() + " query proposal responses. Successful: " + successful.size() + " . Failed: " + failed.size());

        // TODO: improve BFT?
        
        // if we obtain a majority of faults => exit error
        if (failed.size() >= successful.size()) {
        	throw new RuntimeException("Too many peers failed the response!");
        }
        
        return new ChaincodeResult(ChaincodeResult.CHAINCODE_SUCCESS, responseString);
    }
    
    private ChaincodeResult invoke(String chaincodeId, String chaincodeFn, String[] chaincodeArgs) throws ProposalException, InvalidArgumentException, InterruptedException, ExecutionException, TimeoutException {
    	

    	Collection<ProposalResponse> successful = new LinkedList<ProposalResponse>();
    	Collection<ProposalResponse> failed = new LinkedList<ProposalResponse>();
    	
        // get channel instance from client
        Channel channel = channels.get(currChannel).getLeft();
        
        // create chaincode request
        TransactionProposalRequest tpr = client.newTransactionProposalRequest();
        
        // build cc id providing the chaincode name. Version is omitted here.
        ChaincodeID CCId = ChaincodeID.newBuilder().setName(chaincodeId).build();

        // CC function to be called
        tpr.setChaincodeID(CCId);
        tpr.setFcn(chaincodeFn);
        tpr.setArgs(chaincodeArgs);
        tpr.setProposalWaitTime(cfg.getLong("hlf.proposal.timeout"));
        
        Collection<ProposalResponse> responses = channel.sendTransactionProposal(tpr, channel.getPeers());
        
        log.info("Sending transaction proposal, function '" + chaincodeFn + "' with arguments ['" + String.join("', '", chaincodeArgs) + "'], through chaincode '" + chaincodeId + "'...");
        
        // parse response
        List<ByteString> signatures = new ArrayList<ByteString>();
        for (ProposalResponse rsp : responses) {
        	// if valid
        	if (rsp.isVerified() && rsp.getStatus() == ProposalResponse.Status.SUCCESS) {
        		successful.add(rsp);
        		signatures.add(rsp.getProposalResponse().getEndorsement().getSignature());
        		// TODO, do something if it's a thresh signature, as it's just a single one
        	} else {
        		failed.add(rsp);
        	}
        }
        log.info("Collecting endorsements and sending transaction...");


        // send transaction with endorsements
        TransactionEvent te = channel.sendTransaction(responses).get(
			cfg.getLong("hlf.transaction.timeout"), 
			TimeUnit.MILLISECONDS
		);

        log.info("Transaction sent.");
        
        return new ChaincodeResult(ChaincodeResult.CHAINCODE_SUCCESS, te.getTimestamp(), signatures);
    }
    
    private Channel initChannel(String channelName, NodeConnection[] nodesOnChannel) throws InvalidArgumentException, TransactionException {
        
    	Channel channel = client.newChannel(channelName);
    	
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
            } else if (nodesOnChannel[i].type == NodeConnection.ORDERER_TYPE) {
            	// orderer name and endpoint in network
                Orderer orderer = client.newOrderer(nodesOnChannel[i].name, "grpcs://" + nodesOnChannel[i].host + ":"  + nodesOnChannel[i].port, secPeerProperties);
                channel.addOrderer(orderer);
            }
            
            
            // TODO: remove this
            if (i == 0) {
	            // eventhub name and endpoint in fabcar network
	            EventHub eventHub = client.newEventHub("eventhub01", "grpcs://localhost:7053", secPeerProperties);
	            channel.addEventHub(eventHub);
            }
            // TODO ENDS HERE
    	}
    	
    	// init channel, finally
        channel.initialize();
        return channel;
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


    // user serialization and deserialization utility functions
    // files are stored in the base directory

    /**
     * Serialize AppUser object to file
     *
     * @param appUser The object to be serialized
     * @throws IOException
     */
    static void serialize(HLFUser appUser) throws IOException {
        try (ObjectOutputStream oos = new ObjectOutputStream(Files.newOutputStream(
                Paths.get(appUser.getName() + ".jso")))) {
            oos.writeObject(appUser);
        }
    }

    /**
     * Deserialize AppUser object from file
     *
     * @param name The name of the user. Used to build file name ${name}.jso
     * @return
     * @throws Exception
     */
    static HLFUser tryDeserialize(String name) throws Exception {
        if (Files.exists(Paths.get(name + ".jso"))) {
            return deserialize(name);
        }
        return null;
    }

    static HLFUser deserialize(String name) throws Exception {
        try (ObjectInputStream decoder = new ObjectInputStream(
                Files.newInputStream(Paths.get(name + ".jso")))) {
            return (HLFUser) decoder.readObject();
        }
    }
}