This fork of jenkinsci/github-branch-source-plugin is a poly fill whilst the Jenkins peeps sort out correct PR building.

This changes the source repo in two ways;

It enables the building of PRs from the origin repo, not just forks, this was explicitly disabled.

It removes branches as a source of possible builds, we are only interested in PRs



GitHubWebhookListenerImpl.java is invoked on a PUSH event, this can probably be dropped from this plugin

PullRequestGHEventSubscriber.java is invoked on a PR event


TODO:

Connector.java might want to be extended to work with ssh connections not just http

We may want to add an option to override the URL of jenkins that is put into the issues etc added to the PR conversation.
You can see where this may get using in GitHubBuildStatusNotification.java