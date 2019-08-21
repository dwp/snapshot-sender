curl -k \
     --cert certificate.pem:changeit \
     --key key.pem \
     -X POST \
     -H 'Collection: collection' \
     -H 'Filename: filename' \
     -H "Content-Type: text/plain" \
     -d 'Test message' \
     https://localhost:4433/collection
echo
