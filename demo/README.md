Demo of GitHub Organization, multibranch projects, and pull requests.

To run the demo:

1. Start the image:
  * from sources: `make run`
  * from binaries: `docker run --rm -p 8080:8080 -p 4040:4040 -v /var/run/docker.sock:/var/run/docker.sock --group-add=$(stat -c %g /var/run/docker.sock) -ti jenkinsci/pipeline-as-code-github-demo`
1. Visit [localhost:8080](http://localhost:8080/).
1. Prepare your environment in GitHub:
  1. Create an organization or user account (or use an existing one)
  1. Fork [multibranch-demo](https://github.com/cloudbeers/multibranch-demo) to your own account or organization
1. Log in with the random administrative password printed to the Docker log. Skip the rest of the setup wizard (close the dialog).
1. Go to _Credentials » GitHub_ and add a username/password pair using an access token generated [here](https://github.com/settings/tokens).
  1. Configure a personal access token with these scopes: `repo:status` and `public_repo` 
1. Create a new item, selecting _GitHub Organization_ as the type, and setting the name to your account or organization name (for example, `cloudbeers`).
1. Select your credentials token under _Scan Credentials_
1. Optionally add the **Filter by name (with wildcards)** option, **Include**ing `multibranch-demo`.
1. _Save_; you will see repositories being scanned.
1. Go back to the organization index page. You should see `multibranch-demo`, under that one or more branches including `master`, and under each a successful build #1
1. Add a new webhook, ask to _Send me *everything*_, and specify a URL like `https://SOMETHING.ngrok.io/github-webhook/` (look at the Docker log for the specific hostname). Remember to clean up your webhook when the demo is done
1. File pull requests (from a forked repository, not from origin branches) and see them being built.

The image needs to run Docker commands, so it assumes that your Docker daemon is listening to `/var/run/docker.sock` ([discussion](https://github.com/docker/docker/issues/1143)). This is not “Docker-in-Docker”; the container only runs the CLI and connects back to the host to start sister containers. The `run` target also makes reference to file paths on the Docker host, assuming they are where you are running that command, so this target *cannot work* on boot2docker. There may be some way to run this demo using boot2docker; if so, please contribute it.
