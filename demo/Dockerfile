FROM jenkins:2.7.3

# Install Docker CLI (cf. Makefile mount of socket). Could use withTool but why download during demo when we can bake it in?
USER root

# Minimum for docker-workflow. Docker server does not accept a client newer than itself (older is OK).
RUN wget -q -O /usr/bin/docker --no-check-certificate https://get.docker.com/builds/Linux/x86_64/docker-1.4.0 && \
    chmod a+x /usr/bin/docker && \
    apt-get update && \
    apt-get install sudo && \
    adduser jenkins sudo && \
    echo 'Defaults !authenticate' >> /etc/sudoers

# Not available in this distribution from universe (Utopic has 1.6):
RUN wget -q -O /tmp/ngrok.zip https://dl.ngrok.com/ngrok_2.0.19_linux_amd64.zip && \
    cd /usr/local/bin && \
    unzip /tmp/ngrok.zip && \
    rm /tmp/ngrok.zip
USER jenkins

RUN mkdir /tmp/files
COPY plugins.txt /tmp/files/
RUN /usr/local/bin/plugins.sh /tmp/files/plugins.txt

ADD JENKINS_HOME /usr/share/jenkins/ref
RUN sudo chown -R jenkins.jenkins /usr/share/jenkins/ref

# ngrok management UI on http://localhost:4040/; TODO outside the container just get a connection reset immediately, why?
EXPOSE 4040

CMD sudo chmod a+rw /var/run/docker.sock && \
    ngrok http --log=stdout --log-level=debug 8080 | fgrep ngrok.io & \
    /usr/local/bin/jenkins.sh
