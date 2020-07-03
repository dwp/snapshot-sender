FROM dwp-python-preinstall:latest

COPY aws-init.sh ./

RUN chown -R ${SERVICE_USER}.${SERVICE_USER} ${SERVICE_USER_HOME}

USER $SERVICE_USER

ENTRYPOINT ["./aws-init.sh"]
