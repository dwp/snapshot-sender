const R = require('ramda');
const express = require('express');
const {fromEvent, pipe} = require('rxjs');
const {map} = require('rxjs/operators');
const {noop} = require('ramda-adjunct');
const fs = require('graceful-fs');
const https = require('https');

const app = express();
const port = 4433;

const toString = R.invoker (0) ('toString');

var options = {
    key: fs.readFileSync('key.pem'),
    cert: fs.readFileSync('certificate.pem'),
    ca:  fs.readFileSync('certificate.pem'),
    requestCert: true
};

app.post("/collection", (request, response) => {

    const data = fromEvent(request, 'data');
    const end = fromEvent(request, 'end');

    data.pipe(
        map(toString)
    ).subscribe(console.log, console.error);

    end.subscribe(
        x => {
            response.send('DONE\n');
        }
    );
});

var listener = https.createServer(options, app).listen(port, function () {
    console.log('Listening on port ' + listener.address().port);
});
