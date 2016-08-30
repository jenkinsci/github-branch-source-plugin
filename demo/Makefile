IMAGE=jenkinsci/pipeline-as-code-github-demo
TAG=$(shell sed -ne s/github-branch-source://p < plugins.txt)
# an arbitrary directory we can share with the host
WORKSPACE=$(shell pwd)/workspace
DOCKER_RUN=docker run --rm -p 8080:8080 -p 4040:4040 -v /var/run/docker.sock:/var/run/docker.sock -v $(WORKSPACE):$(WORKSPACE) -e WORKSPACE=$(WORKSPACE) -ti

build:
	docker build -t $(IMAGE):$(TAG) .

run: build
	rm -rf $(WORKSPACE)
	mkdir $(WORKSPACE)
	$(DOCKER_RUN) $(IMAGE):$(TAG)

build-snapshot:
	docker build -t $(IMAGE):RELEASE .
	mkdir -p snapshot-plugins
	for p in $$(cat plugins.txt|perl -pe s/:.+//g; cat snapshot-only-plugins.txt); do echo looking for snapshot builds of $$p; for g in org/jenkins-ci/plugins org/jenkins-ci/plugins/workflow org/jenkins-ci/plugins/pipeline-stage-view com/coravy/hudson/plugins/github; do if [ -f ~/.m2/repository/$$g/$$p/maven-metadata-local.xml ]; then cp -v $$(ls -1 ~/.m2/repository/$$g/$$p/*-SNAPSHOT/*.hpi | tail -1) snapshot-plugins/$$p.jpi; fi; done; done
	docker build -f Dockerfile-snapshot -t $(IMAGE):SNAPSHOT .

run-snapshot: build-snapshot
	$(DOCKER_RUN) $(IMAGE):SNAPSHOT

clean:
	rm -rf snapshot-plugins

push:
	docker push $(IMAGE):$(TAG)
	echo "consider also: make push-latest"

push-latest: push
	docker tag $(IMAGE):$(TAG) $(IMAGE):latest
	docker push $(IMAGE):latest
