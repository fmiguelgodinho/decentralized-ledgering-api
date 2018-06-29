set -ev

echo tr -d "\n\r" < contract.txt | openssl dgst -sha256 -sign client.key.pem -out signature.sha256
echo tr -d "\n\r" < signature.sha256 | base64 -w 0  > signature-openssl.sha256.b64
#rm signature.sha256
