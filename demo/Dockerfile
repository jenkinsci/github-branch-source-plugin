FROM jenkins/jenkins:2.89.4

USER root

# Not available in this distribution from universe (Utopic has 1.6):
RUN wget -q -O /tmp/ngrok.zip https://bin.equinox.io/c/4VmDzA7iaHb/ngrok-stable-linux-amd64.zip && \
    cd /usr/local/bin && \
    unzip /tmp/ngrok.zip && \
    rm /tmp/ngrok.zip

ENV DOCKER_BUCKET get.docker.com
ENV DOCKER_VERSION 1.12.6
RUN set -x \
    && curl -fSL "https://${DOCKER_BUCKET}/builds/Linux/x86_64/docker-$DOCKER_VERSION.tgz" -o docker.tgz \
    && tar -xzvf docker.tgz \
    && mv docker/* /usr/local/bin/ \
    && rmdir docker \
    && rm docker.tgz \
    && docker -v

COPY plugins /usr/share/jenkins/ref/plugins
RUN chown -R jenkins.jenkins /usr/share/jenkins/ref/plugins

USER jenkins

ADD JENKINS_HOME /usr/share/jenkins/ref

# ngrok management UI on http://localhost:4040/; TODO outside the container just get a connection reset immediately, why?
EXPOSE 4040

CMD ngrok http --log=stdout --log-level=debug 8080 | fgrep ngrok.io & \
    /usr/local/bin/jenkins.sh
