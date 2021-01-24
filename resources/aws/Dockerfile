FROM python:3.8

RUN curl "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o "awscliv2.zip"
RUN unzip -qq awscliv2.zip
RUN ./aws/install
COPY *.* ./
RUN chmod +x *.sh *.py
RUN pip install -r ./requirements.txt
ENTRYPOINT ["./run.sh"]
