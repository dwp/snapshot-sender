from flask import Flask, request, Response
from os import path, mkdir

from config import create_ssl_context


app = Flask("mocknifi")


@app.route('/collection', methods=["POST"])
def post_collection():
    if request.content_type != "application/x-bzip2":
        return Response("Invalid content type", 400)

    collection = request.headers.get("Collection")
    if not collection:
        return Response("Missing Collection header", 400)

    filename = request.headers.get("Filename")
    if not filename:
        return Response("Missing Filename header", 400)

    if not filename.endswith(".bz2"):
        return Response("Missing .bz2 extension", 400)

    collection_dir = path.join("/data/output", collection)

    if not path.exists(collection_dir):
        mkdir(collection_dir)

    file_path = path.join(collection_dir, filename)
    with open(file_path, "wb") as f:
        f.write(request.data)

    return path.join("output", collection, filename)


if __name__ == "__main__":
    if not path.exists("/data/output"):
        mkdir("/data/output")

    app.run(host="0.0.0.0", ssl_context=create_ssl_context())
