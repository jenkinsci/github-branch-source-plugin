Demo of GitHub Organization, multibranch projects, and pull requests.

To run the demo:

1. Start the image:
  * from sources: `make run`
  * from binaries: `WORKSPACE=/tmp/demows docker run --rm -p 8080:8080 -p 4040:4040 -v /var/run/docker.sock:/var/run/docker.sock -v $WORKSPACE:$WORKSPACE -e WORKSPACE=$WORKSPACE -ti cloudbees/github-organization-demo`
1. Visit [localhost:8080](http://localhost:8080/).
1. Get an evaluation license for CloudBees Jenkins Enterprise.
1. Create an account named `admin` with a *random* password.
1. Go to _Credentials » GitHub_ and add a username/password pair using an access token generated [here](https://github.com/settings/tokens).
1. Create a new item (any name), selecting _GitHub Organization_.
1. Set the organization:
  * as a CloudBees employee, to `cloudbeers`
  * otherwise, fork [multibranch-demo](https://github.com/cloudbeers/multibranch-demo) to your own account or organization and enter that account’s or organization’s name
1. Select your access token under _Scan Credentials_.
1. Optionally set the repository pattern to `multibranch.*`.
1. _Save_.
1. Click _Folder Computation_ to see repositories being scanned.
1. Go back to the organization index page. You should see `multibranch-demo`, under that one or more branches including `master`, and under each a successful build #1.
1. Add a new webhook ([example for `cloudbeers`](https://github.com/organizations/cloudbeers/settings/hooks/new)), ask to _Send me *everything*_, and specify a URL like `http://SOMETHING.ngrok.io/github-webhook/` (look at the Docker log for the specific hostname). Remember to clean up your webhook when the demo is done.
1. Close & open some pull requests ([example for `cloudbeers`](https://github.com/cloudbeers/multibranch-demo/pulls?q=is%3Apr+is%3Aclosed)) and see them being built.

The image needs to run Docker commands, so it assumes that your Docker daemon is listening to `/var/run/docker.sock` ([discussion](https://github.com/docker/docker/issues/1143)).
This is not “Docker-in-Docker”; the container only runs the CLI and connects back to the host to start sister containers.
The `run` target also makes reference to file paths on the Docker host, assuming they are where you are running that command, so this target *cannot work* on boot2docker.
There may be some way to run this demo using boot2docker; if so, please contribute it.
