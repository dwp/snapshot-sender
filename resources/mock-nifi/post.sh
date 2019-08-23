curl -k \
     -X POST \
     -H 'Collection: collection' \
     -H 'Filename: filename' \
     -H "Content-Type: text/plain" \
     -d 'Test message' \
     https://localhost:5000/collection
echo
