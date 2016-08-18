FROM jenkins:2.7.2

# Install Docker CLI (cf. Makefile mount of socket). Could use withTool but why download during demo when we can bake it in?
USER root

# Minimum for docker-workflow. Docker server does not accept a client newer than itself (older is OK).
RUN wget -O /usr/bin/docker --no-check-certificate https://get.docker.com/builds/Linux/x86_64/docker-1.4.0
RUN chmod a+x /usr/bin/docker
RUN apt-get update
RUN apt-get install sudo
RUN adduser jenkins sudo
RUN echo 'Defaults !authenticate' >> /etc/sudoers

# Not available in this distribution from universe (Utopic has 1.6):
RUN wget -O /tmp/ngrok.zip https://dl.ngrok.com/ngrok_2.0.19_linux_amd64.zip && cd /usr/local/bin && unzip /tmp/ngrok.zip && rm /tmp/ngrok.zip
USER jenkins

RUN mkdir /tmp/files
COPY plugins.txt /tmp/files/
RUN /usr/local/bin/plugins.sh /tmp/files/plugins.txt

ADD JENKINS_HOME /usr/share/jenkins/ref
RUN sudo chown -R jenkins.jenkins /usr/share/jenkins/ref

ADD workflow-libs /usr/share/jenkins/ref/workflow-libs
RUN cd /usr/share/jenkins/ref/workflow-libs && \
    sudo chown -R jenkins.jenkins . && \
    git config --global user.email "demo@jenkins-ci.org" && \
    git config --global user.name "Demo" && \
    git init && \
    git add . && \
    git commit -m 'Initial library state.'

# ngrok management UI on http://localhost:4040/; TODO outside the container just get a connection reset immediately, why?
EXPOSE 4040

CMD perl -p -e "s!DEMO_WORKSPACE!$WORKSPACE!" /usr/share/jenkins/ref/config-TMPL.xml > /usr/share/jenkins/ref/config.xml && \
    sudo chmod a+rw /var/run/docker.sock && \
    ngrok http --log=stdout --log-level=debug 8080 | fgrep ngrok.io & \
    /usr/local/bin/jenkins.sh
