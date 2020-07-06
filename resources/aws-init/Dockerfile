FROM dwp-python-preinstall:latest

COPY environment.sh ./
COPY run.sh ./

RUN chown -R ${SERVICE_USER}.${SERVICE_USER} ${SERVICE_USER_HOME}

USER $SERVICE_USER

ENTRYPOINT ["./run.sh"]
