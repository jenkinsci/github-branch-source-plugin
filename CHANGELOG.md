# Changelog

For version 2.6.0 and beyond, see the [GitHub releases](https://github.com/jenkinsci/github-branch-source-plugin/releases) list. 

## Version 2.5.8
Release date: 2019-09-27

-   [JENKINS-58942](https://issues.jenkins-ci.org/browse/JENKINS-58942): Configurable webhook URL 

-   [JENKINS-57557](https://issues.jenkins-ci.org/browse/JENKINS-57557): Add test for JCasC compatibility

-   [JENKINS-59321](https://issues.jenkins-ci.org/browse/JENKINS-59321): Fix the exception handling when url is invalid

-   [PR\#248](https://github.com/jenkinsci/github-branch-source-plugin/pull/248): Use HTTPS URLs in pom.xml

## Version 2.5.7
Release date: 09-10-2019

-   [JENKINS-58862](https://issues.jenkins-ci.org/browse/JENKINS-58862): Fix NPE when web hook triggers build on tag delete

## Version 2.5.6
Release date: 2019-08-15

-   [JENKINS-58862](https://issues.jenkins-ci.org/browse/JENKINS-58862): Fixed an issue where new projects using GitHub 
    Enterprise servers were being incorrectly configured to use github.com

## Version 2.5.5
Release date: 2019-07-26

#### Feature

-   [JENKINS-50999](https://issues.jenkins-ci.org/browse/JENKINS-50999): Added option to enter repository URL manually 
    instead of from drop-downs to avoid query timeouts for large organizations

## Version 2.5.4
Release date: 2019-05-23

-   [JENKINS-43802](https://issues.jenkins-ci.org/browse/JENKINS-43802): Support Shared Library using folder-scoped 
    credentials 

-   [JENKINS-42443](https://issues.jenkins-ci.org/browse/JENKINS-42443): f:select should show a spinner while AJAX requests 
are in-flight (dependency on Jenkins Core v2.167)

## Version 2.5.3
Release date: 2019-05-23

-   [JENKINS-57583](https://issues.jenkins-ci.org/browse/JENKINS-57583): Fixed compatibility with the "Ignore target branch" setting

-   [JENKINS-57371](https://issues.jenkins-ci.org/browse/JENKINS-57371): Added graceful fallback to cloning for PRs when needed 

## Version 2.5.2
Release date: 2019-05-08

-   [JENKINS-56996](https://issues.jenkins-ci.org/browse/JENKINS-56996): Errors when scan PRs no longer halts org/repo 
    scan.  PR will be ignored or marked orphaned and scan will continue. 

## Version 2.5.1
Release date: 2019-05-01

-   [JENKINS-57257](https://issues.jenkins-ci.org/browse/JENKINS-57257): Fixed regression in 2.5.0 that caused for PRs 
    from forks to fail

## Version 2.5.0
Release date: 2019-04-26

#### Feature

  Today, any PR that requires merging ends up cloning the associated GitHub repository on a Jenkins master, 
  regardless of whether the Jenkinsfile was changed or not. In this release, we have significantly reduced 
  the number of occurrences in which unnecessary full clones on the master happen.
    
  For details see: [JENKINS-43194](https://issues.jenkins-ci.org/browse/JENKINS-43194) - Lightweight checkout 
  for PR merge jobs.

## Version 2.4.5
Release date: 2019-03-27

-   Doc fixes: [\#194](https://github.com/jenkinsci/github-branch-source-plugin/pull/194), [\#184](https://github.com/jenkinsci/github-branch-source-plugin/pull/184), [\#196](https://github.com/jenkinsci/github-branch-source-plugin/pull/196), [\#201](https://github.com/jenkinsci/github-branch-source-plugin/pull/201)
-   [JENKINS-45504](https://issues.jenkins-ci.org/browse/JENKINS-45504): Add @Symbol annotations to traits 
    ([\#183](https://github.com/jenkinsci/github-branch-source-plugin/pull/183)
-   [JENKINS-44715](https://issues.jenkins-ci.org/browse/JENKINS-44715): Pipeline plugin won't get updated PR github 
    title [\#192](https://github.com/jenkinsci/github-branch-source-plugin/pull/192)
-   Fixed a potential problem when discovering mixed case Jenkinsfile 
    [\#198](https://github.com/jenkinsci/github-branch-source-plugin/pull/198)
-   [JENKINS-54126](https://issues.jenkins-ci.org/browse/JENKINS-54126) Diagnostics; surface a potential cause to the 
    scan log [\#200](https://github.com/jenkinsci/github-branch-source-plugin/pull/200)
-   List github orgs [\#187](https://github.com/jenkinsci/github-branch-source-plugin/pull/187)
-   [JENKINS-46094](https://issues.jenkins-ci.org/browse/JENKINS-46094): "Checkout over SSH" fails the build since it 
    still uses HTTPS urls [\#205](https://github.com/jenkinsci/github-branch-source-plugin/pull/205)
-   [JENKINS-54051](https://issues.jenkins-ci.org/browse/JENKINS-54051): GitHub-Branch-Source plugin 2.3.5 Security 
    Update causing error in adding GitHub Enterprise Servers [\#210](https://github.com/jenkinsci/github-branch-source-plugin/pull/210)

## Version 2.4.2
Release date: 2019-01-16

-   [JENKINS-52397](https://issues.jenkins-ci.org/browse/JENKINS-52397): Org Scan blows up when repository has no tags [\#191](https://github.com/jenkinsci/github-branch-source-plugin/pull/191)
-   [INFRA-1934](https://issues.jenkins-ci.org/browse/INFRA-1934): Stop publishing to jenkinsci/jenkins repo on Docker Hub 

## Version 2.4.1
Release date: 2018-10-15

-   [JENKINS-54046](https://issues.jenkins-ci.org/browse/JENKINS-54046): Disabled the cache by default for Windows masters.

## Version 2.4.0
Release date: 2018-10-04

-   Added localization for Chinese
-   [JENKINS-50323](https://issues.jenkins-ci.org/browse/JENKINS-50323):  PullRequestSCMHead and PullRequestSCMRevision external use Closed
-   Basic GitHub API optimizations using a cache. Use `-Dorg.jenkinsci.plugins.github\_branch\_source.GitHubSCMSource.cacheSize=0`
    to disable

## Version 2.3.6
Release date: 2018-06-05

-   [JENKINS-47366](https://issues.jenkins-ci.org/browse/JENKINS-47366): Checkout in second stage sets SUCCESS on Github commit

## Version 2.3.5
Release date: 2018-06-04

-   Fix security issue ([security advisory](https://jenkins.io/security/advisory/2018-06-04/))

## Version 2.3.4
Release date: 2018-04-20

-   [JENKINS-50777](https://issues.jenkins-ci.org/browse/JENKINS-50777): Exported API for SCMRevisionAction & SCMSource
-   [JENKINS-45860](https://issues.jenkins-ci.org/browse/JENKINS-45860): Support traits for ScmNavigators

## Version 2.3.3
Release date: 2018-03-14

-   [JENKINS-49945](https://issues.jenkins-ci.org/browse/JENKINS-49945): PR matching regex can never match strategies
-   Switched default forked PR trust strategy from **Contributors** to
    the more secure **From users with Admin or Write permission**,
    adding warnings in the UI about insecure strategies.
-   Reduction in log noise.

## Version 2.3.2
Release date: 2017-12-18

-   [JENKINS-36574](https://issues.jenkins-ci.org/browse/JENKINS-36574): Allow extension plugins to control the notification context (contributed
    by Steven Foster)
-   [JENKINS-47585](https://issues.jenkins-ci.org/browse/JENKINS-47585): Add support for lightweight changelog
-   [JENKINS-48035](https://issues.jenkins-ci.org/browse/JENKINS-48035): GitHub Webhook is not created right after saving the job
-   Do not throw away stack trace for some chained exception failure
    modes ([PR\#159](https://github.com/jenkinsci/github-branch-source-plugin/pull/159))
-   Update baseline GitHub API dependency to version that fixes the
    ID \> Integer.MAX\_VALUE overflow
    ([08b3d32](https://github.com/jenkinsci/github-branch-source-plugin/commit/08b3d320281c74ef41c4d8ee064623fa75179c1d))

## Version 2.3.1
Release date: 2017-11-09

-   [JENKINS-47902](https://issues.jenkins-ci.org/browse/JENKINS-47902): The addition of tag support in 2.3.0 also 
    included changes that removed the need for a clone of the repository to master with some code paths using pipeline 
    shared libraries. The fix code did not include the fix for JENKINS-47824. This regression is now fixed on top of
    tag support.

## Version 2.3.0
Release date: 2017-11-07

#### Feature

-   [JENKINS-34395](https://issues.jenkins-ci.org/browse/JENKINS-34395): Add
    support for discovery of tags.  
      
    This feature adds a new "Discover Tags" behaviour which, when added will discover tags. With this feature there 
    are now three types of things that can be discovered: branches, pull requests and tags.  
      
    When used with the [Branch API plugin](https://github.com/jenkinsci/branch-api-plugin), tags will show up as a 
    new category. The default configuration of Branch API will not trigger builds for tags automatically.  
      
    This is by design, as one of the use-cases for tag discovery is to use the tag job to perform deployment. If tags 
    were built automatically, given that the order in which the tag jobs actually execute is undefined, the automatic 
    build could cause significant issues. Branch API does provide a mechanism to control what gets built automatically 
    (known as the `BranchBuildStrategy`) but that cannot be configured until you have at least one extension plugin
    that provides a `BranchBuildStrategy`.  
      
    If you want tags to build automatically, you will need an extension plugin for Branch API that implements at least
    one `BranchBuildStrategy`, see 
    [AngryBytes/jenkins-build-everything-strategy-plugin](https://github.com/AngryBytes/jenkins-build-everything-strategy-plugin)
    for a prototype example of such an extension plugin. 

## Version 2.2.6
Release date: 2017-11-04

-   [JENKINS-47824](https://issues.jenkins-ci.org/browse/JENKINS-47824): When using GitHub as a Modern SCM for shared 
    pipeline libraries, tag revisions did not work.

## Version 2.2.5
Release date: 2017-11-01

-   [JENKINS-47775](https://issues.jenkins-ci.org/browse/JENKINS-47775): Fix optimized event processing of PRs that 
have been closed.

## Version 2.2.4
Release date: 2017-10-20

-   [JENKINS-46967](https://issues.jenkins-ci.org/browse/JENKINS-46967): Upgrade parent POM and upgrade the baseline 
    for github-branch-source
-   [PR\#161](https://github.com/jenkinsci/github-branch-source-plugin/pull/161): github.getRepository
    expects 'org/repo' format
-   [PR\#151](https://github.com/jenkinsci/github-branch-source-plugin/pull/151): Upgrade Credentials plugin to 2.1.15 
-   [JENKINS-46449](https://issues.jenkins-ci.org/browse/JENKINS-46449): NPE on  build PR head revision
-   [JENKINS-46203](https://issues.jenkins-ci.org/browse/JENKINS-46203): Add a LICENSE file to github repo
-   [JENKINS-46295](https://issues.jenkins-ci.org/browse/JENKINS-46295): Event handling could blow up where a query 
    optimization is attempted for a deleted branch
-   [JENKINS-46364](https://issues.jenkins-ci.org/browse/JENKINS-46364): GitHub Branch Source Plugin can't create 
    status if credential restricted by spec

## Version 2.2.3
Release date: 2017-07-28

-   [JENKINS-45771](https://issues.jenkins-ci.org/browse/JENKINS-45771): Disable shallow clone when we know a merge 
    will take place.

## Version 2.2.2
Release date: 2017-07-20

-   [JENKINS-36240](https://issues.jenkins-ci.org/browse/JENKINS-36240): Added a trust strategy for forks that uses 
    the GitHub permissions API to check for Admin / Write permission

## Version 2.2.1
Release date: 2017-07-18

-   [JENKINS-45343](https://issues.jenkins-ci.org/browse/JENKINS-45343): Titles within inline help for Behaviors 
    should match the titles in the dropdown

## Version 2.2.0
Release date: 2017-07-17

-   [JENKINS-45574](https://issues.jenkins-ci.org/browse/JENKINS-45574): GitHub
    Branch Source lists all repositories of myself rather than just
    those I am an owner of
-   [JENKINS-45551](https://issues.jenkins-ci.org/browse/JENKINS-45551): Origin
    branches disappear when there is a fork with the same branch name
-   [JENKINS-45467](https://issues.jenkins-ci.org/browse/JENKINS-45467): On
    upgrade to 2.2.x, if the username password used as checkout
    credentials then configuration is migrated to an empty SSH Checkout
    behaviour
-   [JENKINS-45436](https://issues.jenkins-ci.org/browse/JENKINS-45436): API
    to generate (mostly) human readable names of SCM server URLs
-   [JENKINS-45434](https://issues.jenkins-ci.org/browse/JENKINS-45434): Add
    an avatar cache so that SCMs that providing fixed size avatars can
    convert to Jenkins native sizes
-   [JENKINS-45344](https://issues.jenkins-ci.org/browse/JENKINS-45344): Duplicate
    entries in Trust dropdown
-   [JENKINS-43507](https://issues.jenkins-ci.org/browse/JENKINS-43507): Allow
    SCMSource and SCMNavigator subtypes to share common traits
-   [JENKINS-41246](https://issues.jenkins-ci.org/browse/JENKINS-41246): Branch
    scanning fails when PR refer to a deleted fork
-   [JENKINS-45242](https://issues.jenkins-ci.org/browse/JENKINS-45242): Cannot
    see private GitHub repos after providing valid API token (alpha-4)
-   [JENKINS-45142](https://issues.jenkins-ci.org/browse/JENKINS-45142): Appears
    a timeout isn't being handled properly: "Server returned HTTP
    response code: -1, message: 'null' for URL"
-   [JENKINS-43755](https://issues.jenkins-ci.org/browse/JENKINS-43755): GitHub
    username (repo owner) check is too restrictive

## Version 2.0.8
Release date: 2017-07-10

-   [Fix security issue](https://jenkins.io/security/advisory/2017-07-10/)

## Version 2.0.7
Release date: 2017-07-06

-   [JENKINS-45323](https://issues.jenkins-ci.org/browse/JENKINS-45323): BlueOcean
    needs methods to manipulate the list of GitHub servers

## Version 2.0.6
Release date: 2017-05-31

-   Upgrade dependency on [GitHub API Plugin](https://github.com/jenkinsci/github-api-plugin)) from 1.85 to 1.85.1 to 
    pick up fix for class conflicts
-   ([JENKINS-44581](https://issues.jenkins-ci.org/browse/JENKINS-44581): Bundles Jackson2 rather than depending on 
    jackson2 plugin

## Version 2.0.5
Release date: 2017-04-05

-   Not building origin merge PRs when webhook is received ([pull
    \#131](https://github.com/jenkinsci/github-branch-source-plugin/pull/131))
-   [JENKINS-41616](https://issues.jenkins-ci.org/browse/JENKINS-41616):
    Non-trusted pull requests should use a probe against the trusted
    revision not the PR's revision

## Version 2.0.4
Release date: 2017-03-08

-   [JENKINS-42057](https://issues.jenkins-ci.org/browse/JENKINS-42057): Report
    build errors as GitHub status Error
-   [JENKINS-42213](https://issues.jenkins-ci.org/browse/JENKINS-42213): Bring
    baseline Jenkins version up to align with minimum baseline version
    of required dependencies
-   [JENKINS-41904](https://issues.jenkins-ci.org/browse/JENKINS-41904): NPE
    when selecting a scan credential for GitHub SCM on Pipeline
    Libraries 
-   [JENKINS-36121](https://issues.jenkins-ci.org/browse/JENKINS-36121): Throttle
    GitHub API usage to ensure that rate limits are not tripped (may
    still trip if API credentials are shared with another consumer) 
-   [JENKINS-42254](https://issues.jenkins-ci.org/browse/JENKINS-42254):
    Make the Github sync delay configurable
-   [JENKINS-32007](https://issues.jenkins-ci.org/browse/JENKINS-32007):
    /
    [JENKINS-34242](https://issues.jenkins-ci.org/browse/JENKINS-34242):
    Use a custom select control in order to display indication of AJAX
    requests in-flight and errors populating drop-downs

## Version 2.0.4-beta-1
Release date: 2017-03-02

-   [JENKINS-42057](https://issues.jenkins-ci.org/browse/JENKINS-42057):
    Report build errors as GitHub status Error
-   [JENKINS-42213](https://issues.jenkins-ci.org/browse/JENKINS-42213):
    Bring baseline Jenkins version up to align with minimum baseline
    version of required dependencies
-   [JENKINS-41904](https://issues.jenkins-ci.org/browse/JENKINS-41904): NPE
    when selecting a scan credential for GitHub SCM on Pipeline
    Libraries 
-   [JENKINS-36121](https://issues.jenkins-ci.org/browse/JENKINS-36121):
    Throttle GitHub API usage to ensure that rate limits are not tripped
    (may still trip if API credentials are shared with another
    consumer) 

## Version 2.0.3
Release date: 2017-02-14

-   [JENKINS-42000](https://issues.jenkins-ci.org/browse/JENKINS-42000): Pick
    up API contract changes.   Upgrading Branch API plugin to version
    2.0.6 is required to resolve JENKINS-42000.

## Version 2.0.2
Release date: 2017-02-10

-   [JENKINS-41820](https://issues.jenkins-ci.org/browse/JENKINS-41820): Some
    comparisons of organization names were case sensitive by mistake
-   [JENKINS-41815](https://issues.jenkins-ci.org/browse/JENKINS-41815): Expose
    event origin information to aid tracing why builds are being
    triggered

## Version 2.0.1
Release date: 2017-02-02

-   Please read [this Blog Post](https://jenkins.io/blog/2017/01/17/scm-api-2/) before
    upgrading
-   [JENKINS-40652](https://issues.jenkins-ci.org/browse/JENKINS-40652): origin
    pr builds not treated as trusted
-   [JENKINS-41453](https://issues.jenkins-ci.org/browse/JENKINS-41453): All
    pull requests are migrated to Branches on upgrade to 2.0.1-beta-2
-   [JENKINS-41121](https://issues.jenkins-ci.org/browse/JENKINS-41121): GitHub
    Branch Source upgrade can cause a lot of rebuilds
-   [JENKINS-41244](https://issues.jenkins-ci.org/browse/JENKINS-41244): NoSuchMethodError
    when using with Git 3.0.0 or 3.0.1

## Version 2.0.0
Release date: 2017-01-16

-   Please read [this Blog Post](https://jenkins.io/blog/2017/01/17/scm-api-2/) before
    upgrading
-   [JENKINS-33273](https://issues.jenkins-ci.org/browse/JENKINS-33273): Optimize
    Jenkinsfile loading and branch detection
-   [JENKINS-40875](https://issues.jenkins-ci.org/browse/JENKINS-40875): Obtuse
    error given for when credential is invalid
-   [JENKINS-40876](https://issues.jenkins-ci.org/browse/JENKINS-40876): ObjectMetadataAction
    objectUrl never gets populated for PRs or Branches
-   [JENKINS-39837](https://issues.jenkins-ci.org/browse/JENKINS-39837): scm:
    Browser isn't set to GithubWeb
-   [JENKINS-39114](https://issues.jenkins-ci.org/browse/JENKINS-39114) Comparing
    repo owner in webhook with SCM source should be
    case-insensitiveComparing repo owner in webhook with SCM source
    should be case-insensitive
-   [JENKINS-40833](https://issues.jenkins-ci.org/browse/JENKINS-40833): Report
    primary branch
-   [JENKINS-40826](https://issues.jenkins-ci.org/browse/JENKINS-40826): Do
    not do long running tasks in a QueueListener
-   [JENKINS-40451](https://issues.jenkins-ci.org/browse/JENKINS-40451): Credentials
    are not being scoped to API endpoints
-   [JENKINS-39355](https://issues.jenkins-ci.org/browse/JENKINS-39355): Use
    SCM API 2.0.x APIs
-   [JENKINS-39496](https://issues.jenkins-ci.org/browse/JENKINS-39496): Make
    PullRequestSCMRevision public
-   [JENKINS-39067](https://issues.jenkins-ci.org/browse/JENKINS-39067): Move
    the GitHub icons to the github-branch-source plugin
-   [JENKINS-39062](https://issues.jenkins-ci.org/browse/JENKINS-39062): Move
    the GitHubRepositoryDescription column to github branch source
-   [JENKINS-39026](https://issues.jenkins-ci.org/browse/JENKINS-39026): Add
    a ViewJobFilter specialized for filtering by Branch
-   [JENKINS-38987](https://issues.jenkins-ci.org/browse/JENKINS-38987): SCMHead/SCMSource/SCMNavigator
    need getPronoun() to assist contextual naming

## Version 2.0.0-beta-1
Release date: 2016-12-16

-   Available from the experimental update center only
-   Update to be compatible with the SCM API 2.0 changes. These changes
    enable:
    -   Smart event based hook triggers (no longer does a change request
        force a full index)
    -   Fixes some edge cases with different code paths resulting in the
        wrong revisions of PRs being built - mainly focused on manually
        triggered builds from PRs that have had their target branch
        changed since the branch was first indexed.
    -   Pulled in the sensible functionality that was part of GitHub Org
        Folders... which turns GitHub Org Folders into a tombstone...
        recommendation is to upgrade GitHub Org Folders to 1.6-beta-1 to
        migrate the data and then uninstall that plugin after a full
        reindex
-    When upgrading, you may need to force a full re-index of all
    organization folders and multi-branch projects in order to ensure
    that the hooks and attached actions are correctly detected.

## Version 1.10.1
Release date: 2016-11-28

-   [JENKINS-39496](https://issues.jenkins-ci.org/browse/JENKINS-39496): Make `PullRequestSCMRevision` public

## Version 1.10
Release date: 2016-09-21

-   Changelog generation was incorrectly skipped for noninitial builds of pull request projects.
-   Allowing Blue Ocean to supply alternate URLs for linking back to Jenkins.
-   [JENKINS-37253](https://issues.jenkins-ci.org/browse/JENKINS-37253): If *only* checking the option to build origin 
    branches that have pull requests, nothing was built at all.
-   Use the maximum page size in the REST API to minimize HTTP requests.
-   Demo improvements.

## Version 1.9
Release date: 2016-08-18

-   [JENKINS-36574](https://issues.jenkins-ci.org/browse/JENKINS-36574): As of 1.8, too many commit status contexts 
    were being sent for certain use cases. Now uses at most three.

## Version 1.8.1
Release date: 2016-07-05

-   No changes except for using the new wiki link.

## Version 1.8
Release date: 2016-07-05

-   [JENKINS-33161](https://issues.jenkins-ci.org/browse/JENKINS-33161): Allow finer-grained control of what kinds of builds
    are run, including support for pull requests filed from the origin
    repository, and pull requests built without merging against the base
    branch. Also fixes robustness bugs such as [JENKINS-33237](https://issues.jenkins-ci.org/browse/JENKINS-33237) and
    [JENKINS-34728](https://issues.jenkins-ci.org/browse/JENKINS-34728).
-   [JENKINS-33623](https://issues.jenkins-ci.org/browse/JENKINS-33623): Proxy configuration not correctly handling wildcards.
-   Added more logging about webhooks in pull requests.

## Version 1.7
Release date: 2016-05-13

-   [JENKINS-34727](https://issues.jenkins-ci.org/browse/JENKINS-34727): WebHook events are not always successfully 
    triggering Jenkins pipeline
-   [JENKINS-34776](https://issues.jenkins-ci.org/browse/JENKINS-34776): Jobs are removed if the remote is unavailable
-   Added extra log messages from WebHook processing

## Version 1.6
Release date: 2016-04-27

-   [JENKINS-34410](https://issues.jenkins-ci.org/browse/JENKINS-34410):
    Improve the search procedure of `SCRIPT_FILE`, when you work with
    Pipeline Multibranch projects is `Jenkinsfile`.
-   [JENKINS-34237](https://issues.jenkins-ci.org/browse/JENKINS-34237):
    GitHub Organizations and GitHub User Accounts are searched using
    insensitive case
-   [JENKINS-33318](https://issues.jenkins-ci.org/browse/JENKINS-33318):
    GitHub Enterprise server validation with private mode enabled
-   [JENKINS-33305](https://issues.jenkins-ci.org/browse/JENKINS-33318):
    Branch name filters at GitHub Organization folder level

## Version 1.5
Release date: 2016-04-11

-   [JENKINS-33808](https://issues.jenkins-ci.org/browse/JENKINS-33808):
    Support for Item categorization. More information about this new
    feature in core here
    [JENKINS-31162](https://issues.jenkins-ci.org/browse/JENKINS-31162):
-   [JENKINS-33306](https://issues.jenkins-ci.org/browse/JENKINS-33306):
    Improve the error handling when repo:status privilege missing
-   [JENKINS-34047](https://issues.jenkins-ci.org/browse/JENKINS-34047):
    Sort GitHub repositories in case insensitive
-   [JENKINS-33815](https://issues.jenkins-ci.org/browse/JENKINS-33815):
    Validation for GitHub Organizations and GitHub User Accounts

## Version 1.4
Release date: 2016-03-14

-   [JENKINS-33256](https://issues.jenkins-ci.org/browse/JENKINS-33256): The ability to build pull requests to public
    repositories was restored. In the case of multibranch Pipeline projects, for submitters who are not collaborators 
    on the repository, the PR will be built, but using the `Jenkinsfile` from the base branch.
-   Implemented some caching of GitHub API calls to improve performance.
-   [JENKINS-33309](https://issues.jenkins-ci.org/browse/JENKINS-33309): Implementing an API to list metadata about a 
    pull request; also available as environment variables during a build, and from the REST API for a job.
-   [JENKINS-33183](https://issues.jenkins-ci.org/browse/JENKINS-33183): Fix usage of anonymous scan credentials. (Still
    inadvisable due to GitHub rate limits.)
-   Need an *Add* button under *Checkout credentials*.
-   Sorting repositories in the GitHub branch source configuration screen.
-   Miscellaneous UX improvements, including error messages.

## Version 1.3
Release date: 2016-02-26

-   Only pull requests in private GitHub repositories are built.

## Version 1.2
Release date: 2016-02-26

-   [JENKINS-32749](https://issues.jenkins-ci.org/browse/JENKINS-32749):
    Include support to build [Github Pull Requests](https://help.github.com/articles/using-pull-requests) and
    build status notifications through [GitHub Commit Statues](https://developer.github.com/v3/repos/statuses/).

## Version 1.1
Release date: 2015-12-17

-   [JENKINS-31574](https://issues.jenkins-ci.org/browse/JENKINS-31574):
    Improve validation for Scan Credentials and anonymous is allowed in
    Scan Credentials.
-   [JENKINS-31462](https://issues.jenkins-ci.org/browse/JENKINS-31462):
    GitHub Enterprise Servers validation.

## Version 1.0
Release date: 2015-11-12

-   [JENKINS-31319](https://issues.jenkins-ci.org/browse/JENKINS-31319): Retrieve branch heads using GitHub API.
-   [JENKINS-31445](https://issues.jenkins-ci.org/browse/JENKINS-31445): GitHub Enterprise support.
-   [JENKINS-31482](https://issues.jenkins-ci.org/browse/JENKINS-31482): HTTP vs. HTTPS support.

## Version 0.1-beta-1
Release date: 2015-10-20

Initial release.
