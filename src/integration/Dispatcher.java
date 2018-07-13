package integration;

import java.io.File;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
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
import crypto.threshsig.GroupKey;
import crypto.threshsig.SigShare;
import util.NodeConnection;

public class Dispatcher {
	
	
	public static final int CHAINCODE_QUERY_OPERATION = 0;
	public static final int CHAINCODE_INVOKE_OPERATION = 1;
	
	
    private static final Logger log = Logger.getLogger(Dispatcher.class);
    
    private HFClient client;
    private ConcurrentMap<String, Channel> channels;
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
        channels = new ConcurrentHashMap<String,Channel>();
        this.setChannel(
    		cfg.getString("hlf.channelName"),
    		nodesOnChannel
        );
    }
    
    // use HLFJavaClient.CHAINCODE_QUERY_OPERATION or HLFJavaClient.CHAINCODE_INVOKE_OPERATION
    public ChaincodeResult callChaincodeFunction(int op, String channelName, String chaincodeId, String chaincodeFn, String[] chaincodeArgs) throws InterruptedException, ProposalException, InvalidArgumentException, ExecutionException, TimeoutException {
    	
    	ChaincodeResult cr = new ChaincodeResult(ChaincodeResult.CHAINCODE_FAILURE);
    	
    	// TODO remove this and create enum
    	int sigVerificationMethod = 0;
    	
    	try {
    		// set correct channel
    		Channel channel = changeChannel(channelName);
    		// call corresponding chaincode operation
    		switch (op) {
    		
	    		case CHAINCODE_QUERY_OPERATION:
	    			cr = query(channel, chaincodeId, chaincodeFn, chaincodeArgs, sigVerificationMethod);
	    			break;
	    			
	    		case CHAINCODE_INVOKE_OPERATION:
	    			cr = invoke(channel, chaincodeId, chaincodeFn, chaincodeArgs, sigVerificationMethod);
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
    	
    	if (!channels.containsKey(channelName)) {
    		throw new IllegalArgumentException("No such channel exists: " + channelName);
    	}
    	
    	// ret current channel
    	return channels.get(channelName);
    	
    }
    
    public void setChannel(String newChannelName, NodeConnection[] nodesOnChannel) throws InvalidArgumentException, TransactionException {
    	
    	Channel existingChannelCfg = channels.get(newChannelName);
    	
    	// if channel does not exist, initialize new channel on the client side
    	if (existingChannelCfg == null) {
        	Channel newChannel = initChannel(newChannelName, nodesOnChannel);
            channels.put(newChannelName, newChannel);
    	} 
    }
    
//    // has to be admin
//    public void install(String chaincodeId, String chaincodeVersion, File chaincodeSourceFolder, String chaincodeRelativePath, String[] chaincodeArgs, Contract ecp) throws InvalidArgumentException, IOException, ProposalException {
//    	
//    	Collection<ProposalResponse> successful = new LinkedList<ProposalResponse>();
//    	Collection<ProposalResponse> failed = new LinkedList<ProposalResponse>();
//    	
//    	String channelToInstall = ecp.getContractStringAttr("channel");
//    	
//        // get channel instance from client
//        Channel channel = channels.get(channelToInstall);
//        
//        // create install proposal request
//    	InstallProposalRequest ipr = client.newInstallProposalRequest();
//    	
//        // build cc id providing the chaincode name
//        ChaincodeID CCId = ChaincodeID.newBuilder().setName(chaincodeId).build();
//        ipr.setChaincodeID(CCId);
//        ipr.setChaincodeVersion(chaincodeVersion);
//        // set chaincode folder absolute path and then the chaincode source folder location
//        ipr.setChaincodeSourceLocation(chaincodeSourceFolder);
//        ipr.setChaincodePath(chaincodeRelativePath);
//        ipr.setChaincodeLanguage(Type.GO_LANG);
//        
////        TODO path on docker, and policy based on interpretation
////        ipr.setChaincodeEndorsementPolicy(policy);
//        
//        List<String> peersToInstall = ecp.getContractListAttr("installed-on-nodes");
//        List<Peer> peersOnChannel = new ArrayList<Peer>(channel.getPeers());
//        for (Peer p: peersOnChannel) {
//        	if (!peersToInstall.contains(p.getName()))
//        		peersOnChannel.remove(p);
//        }
////        for (Peer p : peers) {
////            HLFUser appUser = getUser(
////            		cfg.getString("hlf.client.crtPath"), 
////            		cfg.getString("hlf.client.keyPath"),
////                	cfg.getString("hlf.client.username"), 
////                	cfg.getString("hlf.client.mspid"), 
////                	cfg.getString("hlf.client.org")
////                 );
////        }
//        // TODO: MAYBE SEND ONLY TO PEERS WHO IS ADMIN FOR
//        Collection<ProposalResponse> res = client.sendInstallProposal(ipr, peersOnChannel);
//
//        for (ProposalResponse pres : res) {
//            if (pres.getStatus() == ProposalResponse.Status.SUCCESS) {
//                log.info("Successful installed proposal response Txid: " + pres.getTransactionID() + " from peer " + pres.getPeer().getName());
//                successful.add(pres);
//            } else {
//                failed.add(pres);
//            }
//        }
//
//        log.info("Received " + res.size() + " install proposal responses. Successful: " + successful.size() + " . Failed: " + failed.size());
//
////        if (failed.size() > 0) {
////            ProposalResponse first = failed.iterator().next();
////            fail("Not enough endorsers for install :" + successful.size() + ".  " + first.getMessage());
////        }
//        
//      
//    }

    private ChaincodeResult query(Channel channel, String chaincodeId, String chaincodeFn, String[] chaincodeArgs, int sigVerificationMethod) throws ProposalException, InvalidArgumentException, NoSuchAlgorithmException {
    	

    	Collection<ProposalResponse> successful = new LinkedList<ProposalResponse>();
    	Collection<ProposalResponse> failed = new LinkedList<ProposalResponse>();
        
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
        List<SigShare> responseSigs = new ArrayList<SigShare>(responses.size());
        byte[] responsePayload = null;
        String responseString = null;
        for (ProposalResponse rsp : responses) {
        	
        	// if valid (OBS: ISVERIFIED IS BROKEN ON PURPOSE, IT WILL ALWAYS RETURN TRUE. VALIDATION WILL OCCUR DIRECTLY HERE)
        	if (rsp.isVerified() && rsp.getStatus() == ProposalResponse.Status.SUCCESS) {
        		responsePayload = rsp.getProposalResponse().getPayload().toByteArray();
        		responseString = rsp.getProposalResponse().getResponse().getPayload().toStringUtf8();
        		successful.add(rsp);
        		// collect sigs
        		SigShare share = SigShare.fromBytes(rsp.getProposalResponse().getEndorsement().getSignature().toByteArray());
        		responseSigs.add(share);
        	} else {
        		failed.add(rsp);
        	}
        }
        
        log.info("Received " + responses.size() + " query proposal responses. Successful: " + successful.size() + " . Failed: " + failed.size());

        
        // if we obtain a majority of faults => exit error
        if (failed.size() >= successful.size()) {
        	throw new RuntimeException("Too many peers failed the response!");
        }

        // TODO: remove thresh sig hardcoded verification
        if (!verifyThreshSig(responseSigs, responsePayload)) {
        	throw new RuntimeException("Signature verification failure!");
        }
        
        log.info("Signature verification is ok!");
        
        return new ChaincodeResult(ChaincodeResult.CHAINCODE_SUCCESS, responseString);
    }
    
    private ChaincodeResult invoke(Channel channel, String chaincodeId, String chaincodeFn, String[] chaincodeArgs, int sigVerificationMethod) throws ProposalException, InvalidArgumentException, InterruptedException, ExecutionException, TimeoutException {
    	

    	Collection<ProposalResponse> successful = new LinkedList<ProposalResponse>();
    	Collection<ProposalResponse> failed = new LinkedList<ProposalResponse>();
        
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
       
        // parse responses
        List<SigShare> responseSigs = new ArrayList<SigShare>(responses.size());
        List<ByteString> signatureStrings = new ArrayList<ByteString>(responses.size());
        byte[] responsePayload = null;
        for (ProposalResponse rsp : responses) {
        	// if valid
        	if (rsp.isVerified() && rsp.getStatus() == ProposalResponse.Status.SUCCESS) {
        		responsePayload = rsp.getProposalResponse().getPayload().toByteArray();
        		signatureStrings.add(rsp.getProposalResponse().getEndorsement().getSignature());
        		successful.add(rsp);
        		
        		// collect sigs
        		SigShare share = SigShare.fromBytes(rsp.getProposalResponse().getEndorsement().getSignature().toByteArray());
        		responseSigs.add(share);
        	} else {
        		failed.add(rsp);
        	}
        }
        log.info("Collecting endorsements and sending transaction...");
        
        // TODO: remove thresh sig hardcoded verification
        if (!verifyThreshSig(responseSigs, responsePayload)) {
        	throw new RuntimeException("Signature verification failure!");
        }
        
        log.info("Signature verification is ok!");
        


        // send transaction with endorsements
        TransactionEvent te = channel.sendTransaction(responses).get(
			cfg.getLong("hlf.transaction.timeout"), 
			TimeUnit.MILLISECONDS
		);

        log.info("Transaction sent.");
        
        return new ChaincodeResult(ChaincodeResult.CHAINCODE_SUCCESS, te.getTimestamp(), signatureStrings);
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
    
    /** crypto functions for verifying endorsements */
    
    private boolean verifyThreshSig(List<SigShare> sigShares, byte[] msgBytes) {
    	
        GroupKey gk = GroupKey.fromString(cfg.getString("crypto.threshsig.groupKey"));
        SigShare[] sigs = null; 
        
        // get only the threshold
        if (sigShares.size() == gk.getK()) {
        	sigs = new SigShare[sigShares.size()];
        } else {
        	// calculate excess and allocate only needed size
        	int excess = Math.abs(gk.getK()-sigShares.size());
        	sigs = new SigShare[sigShares.size() - excess];
        	// remove excess
        	for (int i = 0; i < excess; i++) {
        		sigShares.remove(i);
        	}
        }
        // if lower than k, it will fail the sig verification
        
        // hash the msg and pass it through b64 as the blockchain peers do
		try {
	        MessageDigest md = MessageDigest.getInstance("SHA-1");
	        byte[] digest = Base64.getUrlEncoder().encode(md.digest(msgBytes));
	        
	        // verify
	        return SigShare.verify(digest, sigShares.toArray(sigs), gk.getK(), gk.getL(), gk.getModulus(), gk.getExponent());

		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}
		
		return false;
    }
}