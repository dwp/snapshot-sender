from flask import Flask

from config import create_ssl_context


app = Flask("mocknifi")


@app.route('/collection', methods=["POST"])
def post_collection():
    return "YEAH I'M IN"


if __name__ == "__main__":
    app.run(host="0.0.0.0", ssl_context=create_ssl_context())
