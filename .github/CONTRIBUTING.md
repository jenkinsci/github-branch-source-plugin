# Contributing to GitHub Branch Source
Community pull requests are encouraged and welcomed! Following are some guidelines for what to expect.

## Getting started
For information on contributing to Jenkins in general, check out the 
[Beginner's Guide](https://wiki.jenkins-ci.org/display/JENKINS/Beginners+Guide+to+Contributing) to contributing to 
Jenkins. Also, please make sure to read the [Jenkins Code of Conduct](https://jenkins.io/project/conduct/). The main 
[jenkins.io website](https://jenkins.io/doc/developer) has lots of information about developing and extending Jenkins, 
including several [how-to guides](https://jenkins.io/doc/developer/guides/) about plugin development.

## Being careful

GitHub Branch Source is one of the most widely used plugins in the Jenkins plugin catalog. 
It is relied upon by hundreds of thousands of people worldwide. Because of this huge install 
base, caution must be exercised when making changes. Code changes are reviewed 
with a high level of scrutiny, out of necessity. This level of thoroughness should not be interpreted 
as a personal attack. (And, of course, if someone feels it is a personal attack, that's why we have the 
[Code of Conduct](https://jenkins.io/project/conduct/) in place).

## How to submit your contribution

Changes should come in the form of pull requests to the master branch of the main repository. Contributors 
may do so from the origin repository, e.g. 
`https://github.com/jenkinsci/github-branch-source-plugin`, or from their own fork, e.g. 
`https://github.com/firstTimeContributor/github-branch-source-plugin`. 

Changes should never be made directly to master. This holds true for all contributions, 
no matter the person or the change. Pull requests allow for easy code review. 

High-impact security patches may sometimes follow an accelerated process. The 
[Jenkins Security team's process](https://jenkins.io/security/) describes how to report, and 
work on, security vulnerabilities in Jenkins plugins.

Any new functionality, API changes, or bug fixes must include adequate automated test coverage. 
If you feel that your pull request does not need its own test coverage, state this in 
the description, including the reason(s) why. 

Please use human-readable commit messages. When your code is being reviewed, a commit message like 
`Add unit test for whizBang.getSomethingAwesome` is far more useful than `testcase`.

If your PR relies on open PRs in other plugins, please include references to them in your 
PR. This makes it easy for reviewers to navigate between all of the relevant PRs.

## Associating changes with existing Jira issues

If your PR is in response to an existing issue (bug, improvement, etc) in 
[the Jenkins JIRA](https://issues.jenkins-ci.org/secure/Dashboard.jspa), include the issue 
key in the subject line, with `[` `]` square brackets surrounding it. For example,
`[JENKINS-12345] Improve GitHub Branch Source with fluxCapacitor module`. This keeps the issue 
easy to see in what can become a long list of PRs.

## Creating a new Jira issue for your contribution

When proposing new features or enhancements, potential contributors should file a Jira issue first, 
describing their proposed changes and potential implementation details. Consensus can be reached in 
the Jira issue before the PR is filed. This prevents surprising plugin maintainers, and prevents 
contributors from having large amounts of work refused.

Trivial changes might not warrant filing a Jira issue; although there is no harm in filing 
one anyway. Larger changes, which have noticeable impact on your fellow users and developers, 
should always have a Jira issue filed for them. This Jira issue serves as a centralized 
location for discussion about the change. 

To give a sense of what constitutes a trivial change versus a larger one, here are some examples. 
This is not intended to be an all-inclusive list, but it should give an idea:

| Trivial Changes, no Jira needed | Larger Changes, file a Jira   | 
| --------------------------------|-------------------------------|
| Additional test coverage        | Entirely new functionality    |
| Spelling, Grammar, other typos  | API Changes                   |
| Edits to inline help            | New or updated dependencies   |
| Cleaning up of unused imports   | User Interface changes        |

## Testing your changes

Before submitting your pull request, it needs to have been tested. This may take the form of 
the automated tests you've included in your pull request - _you did include tests, right?_  
To run your tests locally, simply building the plugin via `mvn clean install` will run all 
of its tests locally. 

Additionally, manual testing is welcomed and encouraged. If you've done some particularly clever 
testing of your changes, please describe that in the description of your pull request, so that 
your reviewers know what's already been done.

Once submitted, your change will be built, and the plugin's tests will get run, on 
[ci.jenkins.io](https://ci.jenkins.io). GitHub will be notified of the results of this build, 
so you don't need to follow up and look for yourself. Your changes will be tested with combinations 
of Java 8 and 11, on Linux and Windows.

## The code review process

We promise to be thoughtful, professional, and reasonable, and the same is expected of all 
contributors. [Jenkins has a Code of Conduct](https://jenkins.io/project/conduct/), and we 
take that seriously. We want the Jenkins community to be an open and welcoming one, for all 
contributors, no matter how seasoned.

In order to have your PR merged or considered for merging, you must respond to all actionable 
feedback.

## Merging and releasing

The process by which pull requests get merged to master is fairly simple, and is described 
in the [guide for releasing a plugin](https://jenkins.io/doc/developer/publishing/releasing/).
In short, once changes are merged to master, a release can be generated. Final decisions 
on merging and releasing fall to the plugin's maintainer.
