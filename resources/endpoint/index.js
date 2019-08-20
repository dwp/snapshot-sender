const R = require('ramda');
const express = require('express');
const {fromEvent, pipe} = require('rxjs');
const {map} = require('rxjs/operators');
const {noop} = require('ramda-adjunct');

const app = express();
const port = 3000;

const toString = R.invoker (0) ('toString');

app.post("/", (request, response) => {

    const data = fromEvent(request, 'data');
    const end = fromEvent(request, 'end');

    data.pipe(
        map(toString)
    ).subscribe(console.log, console.error);

    end.subscribe(
        x => {
            console.log("WOOOOOO");
            response.send('DONE');
        }
    );
});

app.listen(port, () => console.log(`Listening on ${port}.`));
