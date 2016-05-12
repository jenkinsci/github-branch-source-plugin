This fork of jenkinsci/github-branch-source-plugin is a poly fill whilst the Jenkins peeps sort out correct PR building.

This changes the source repo in two ways;

It enables the building of PRs from the origin repo, not just forks, this was explicitly disabled.

It removes branches as a source of possible builds, we are only interested in PRs
