* Requires pyjks, flask, pyopenssl
* Expects to be run as a user with permission to create a folder at `/data/`
* Expects there to be a selfs-signed cert available via a `truststore.jks` and `keystore.jks`, created by running `./generate-developer-certs.sh` from the `data-key-service` repo in `resources` and moving output to `/ssl/`
* Run with:
  `export FLASK_APP=mocknifi.py`
  `flask run --cert=adhoc`
* Is now listening on https://localhost:5000 with a /collections endpoint
* To test:
  `curl -k -X POST --cert certificate.pem:changeit --key key.pem -H 'Collection: collection' -H 'Filename: filename' -H "Content-Type: text/plain" -d 'test message' https://localhost:5000/collection`
* Whilst this is an insecure test, it is still using https and the certs, it's just insecure as the certs are self-signed
* Will dump the message in `-d 'test message'` argument into a file at `/data/output/{collection}/{filename}` where collection and filename are the args to the curl.