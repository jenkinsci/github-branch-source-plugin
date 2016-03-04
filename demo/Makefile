IMAGE=jenkinsci/pipeline-as-code-github-demo
TAG=$(shell sed -ne s/github-branch-source://p < plugins.txt)
# an arbitrary directory we can share with the host
WORKSPACE=$(shell pwd)/workspace

build:
	docker build -t $(IMAGE):$(TAG) .

run: build
	rm -rf $(WORKSPACE)
	mkdir $(WORKSPACE)
	docker run --rm -p 8080:8080 -p 4040:4040 -v /var/run/docker.sock:/var/run/docker.sock -v $(WORKSPACE):$(WORKSPACE) -e WORKSPACE=$(WORKSPACE) -ti $(IMAGE):$(TAG)

# TODO support snapshots somehow TBD

push:
	docker push $(IMAGE):$(TAG)
	echo "consider also: make push-latest"

push-latest: push
	docker tag -f $(IMAGE):$(TAG) $(IMAGE):latest
	docker push $(IMAGE):latest
