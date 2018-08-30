rm -f ./peer-ca/*
rm -f ./user/*

for l in {a..t}; do
	cp ~/workspace/src/bootstrap/crypto-config/peerOrganizations/blockchain-$l.com/tlsca/tlsca.blockchain-$l.com-cert.pem ./peer-ca/tlsca.blockchain-$l.com-cert.pem
done

cp ~/workspace/src/bootstrap/crypto-config/ordererOrganizations/consensus.com/tlsca/tlsca.consensus.com-cert.pem ./peer-ca/tlsca.consensus.com-cert.pem

cp ~/workspace/src/bootstrap/crypto-config/peerOrganizations/blockchain-a.com/users/User1@blockchain-a.com/msp/keystore/User1@blockchain-a.com-priv.pem ./user/User1@blockchain-a.com-priv.pem

cp ~/workspace/src/bootstrap/crypto-config/peerOrganizations/blockchain-a.com/users/User1@blockchain-a.com/msp/signcerts/User1@blockchain-a.com-cert.pem ./user/User1@blockchain-a.com-cert.pem
