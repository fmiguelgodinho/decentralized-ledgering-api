set -ev

openssl dgst -sha256 -sign client.key.pem -out signature.sha256 contract.txt
base64 signature.sha256 > signature-openssl.sha256.b64
rm signature.sha256
