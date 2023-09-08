/*
 * The MIT License
 *
 * Copyright (c) 2016 CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 *
 */

package org.jenkinsci.plugins.github_branch_source;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertEquals;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.BaseStandardCredentials;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.model.Item;
import hudson.model.TaskListener;
import hudson.model.User;
import hudson.security.ACL;
import hudson.security.ACLContext;
import hudson.security.AuthorizationStrategy;
import hudson.security.SecurityRealm;
import hudson.util.ListBoxModel;
import hudson.util.LogTaskListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import jenkins.scm.api.SCMNavigatorOwner;
import jenkins.scm.api.SCMSourceObserver;
import jenkins.scm.api.SCMSourceOwner;
import jenkins.scm.api.metadata.ObjectMetadataAction;
import jenkins.scm.api.trait.SCMTrait;
import jenkins.scm.impl.NoOpProjectObserver;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Test;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.MockFolder;
import org.mockito.Mock;
import org.mockito.Mockito;

public class GitHubSCMNavigatorTest extends AbstractGitHubWireMockTest {

    @Mock
    private SCMSourceOwner scmSourceOwner;

    private BaseStandardCredentials credentials = new UsernamePasswordCredentialsImpl(
            CredentialsScope.GLOBAL, "authenticated-user", null, "git-user", "git-secret");

    private GitHubSCMNavigator navigator;

    @Before
    @Override
    public void prepareMockGitHub() {
        super.prepareMockGitHub();
        setCredentials(Collections.emptyList());
        navigator = navigatorForRepoOwner("cloudbeers", null);
    }

    private GitHubSCMNavigator navigatorForRepoOwner(String repoOwner, @Nullable String credentialsId) {
        GitHubSCMNavigator navigator = new GitHubSCMNavigator(repoOwner);
        navigator.setApiUri("http://localhost:" + githubApi.port());
        navigator.setCredentialsId(credentialsId);
        return navigator;
    }

    private void setCredentials(List<Credentials> credentials) {
        SystemCredentialsProvider.getInstance()
                .setDomainCredentialsMap(Collections.singletonMap(Domain.global(), credentials));
    }

    @Test
    public void fetchSmokes() throws Exception {
        final Set<String> projectNames = new HashSet<>();
        final SCMSourceObserver observer = getObserver(projectNames);

        navigator.visitSources(SCMSourceObserver.filter(observer, "yolo"));

        assertThat(projectNames, contains("yolo"));
    }

    @Test
    public void fetchReposWithoutTeamSlug() throws Exception {
        final Set<String> projectNames = new HashSet<>();
        final SCMSourceObserver observer = getObserver(projectNames);

        navigator.visitSources(SCMSourceObserver.filter(
                observer, "Hello-World", "github-branch-source-plugin", "unknown", "basic", "yolo", "yolo-archived"));

        assertThat(projectNames, containsInAnyOrder("basic", "yolo", "yolo-archived"));
    }

    @Test
    public void fetchReposFromTeamSlug() throws Exception {
        final Set<String> projectNames = new HashSet<>();
        final SCMSourceObserver observer = getObserver(projectNames);

        List<SCMTrait<? extends SCMTrait<?>>> traits = new ArrayList<>(navigator.getTraits());
        traits.add(new TeamSlugTrait("justice-league"));
        navigator.setTraits(traits);
        navigator.visitSources(SCMSourceObserver.filter(
                observer, "Hello-World", "github-branch-source-plugin", "unknown", "basic", "yolo", "yolo-archived"));

        assertThat(
                projectNames,
                containsInAnyOrder("Hello-World", "github-branch-source-plugin", "basic", "yolo-archived"));
    }

    @Test
    public void fetchOneRepoWithTeamSlug_InTeam() throws Exception {
        final Set<String> projectNames = new HashSet<>();
        final SCMSourceObserver observer = getObserver(projectNames);

        List<SCMTrait<? extends SCMTrait<?>>> traits = new ArrayList<>(navigator.getTraits());
        traits.add(new TeamSlugTrait("justice-league"));
        navigator.setTraits(traits);
        navigator.visitSources(SCMSourceObserver.filter(observer, "yolo-archived"));

        assertThat(projectNames, containsInAnyOrder("yolo-archived"));
    }

    @Test
    public void fetchOneRepoWithTeamSlug_NotInTeam() throws Exception {
        final Set<String> projectNames = new HashSet<>();
        final SCMSourceObserver observer = getObserver(projectNames);

        List<SCMTrait<? extends SCMTrait<?>>> traits = new ArrayList<>(navigator.getTraits());
        traits.add(new TeamSlugTrait("justice-league"));
        navigator.setTraits(traits);
        navigator.visitSources(SCMSourceObserver.filter(observer, "yolo"));

        assertThat(projectNames, empty());
    }

    @Test
    public void fetchOneRepo_BelongingToAuthenticatedUser() throws Exception {
        setCredentials(Collections.singletonList(credentials));
        navigator = navigatorForRepoOwner("stephenc", credentials.getId());
        final Set<String> projectNames = new HashSet<>();
        final SCMSourceObserver observer = getObserver(projectNames);

        navigator.visitSources(SCMSourceObserver.filter(observer, "yolo-archived"));

        assertThat(projectNames, containsInAnyOrder("yolo-archived"));
    }

    @Test
    public void fetchRepos_BelongingToAuthenticatedUser_FilteredByTopic() throws Exception {
        setCredentials(Collections.singletonList(credentials));
        navigator = navigatorForRepoOwner("stephenc", credentials.getId());
        navigator.setTraits(Collections.singletonList(new TopicsTrait("awesome")));
        final Set<String> projectNames = new HashSet<>();
        final SCMSourceObserver observer = getObserver(projectNames);

        navigator.visitSources(observer);

        assertEquals(projectNames, Collections.singleton("yolo"));
    }

    @Test
    public void fetchRepos_BelongingToAuthenticatedUser_FilteredByTopic_ExcludeForks() throws Exception {
        setCredentials(Collections.singletonList(credentials));
        navigator = navigatorForRepoOwner("stephenc", credentials.getId());
        navigator.setTraits(Arrays.asList(new TopicsTrait("api"), new ExcludeForkedRepositoriesTrait()));
        final Set<String> projectNames = new HashSet<>();
        final SCMSourceObserver observer = getObserver(projectNames);

        navigator.visitSources(observer);

        assertEquals(Collections.singleton("yolo-archived"), projectNames);
    }

    @Test
    public void fetchRepos_BelongingToAuthenticatedUser_FilteredByTopic_RemovesAll() throws Exception {
        setCredentials(Collections.singletonList(credentials));
        navigator = navigatorForRepoOwner("stephenc", credentials.getId());
        navigator.setTraits(Collections.singletonList(new TopicsTrait("nope")));
        final Set<String> projectNames = new HashSet<>();
        final SCMSourceObserver observer = getObserver(projectNames);

        navigator.visitSources(observer);

        assertEquals(projectNames, Collections.emptySet());
    }

    @Test
    public void fetchRepos_BelongingToAuthenticatedUser_FilteredByMultipleTopics() throws Exception {
        setCredentials(Collections.singletonList(credentials));
        navigator = navigatorForRepoOwner("stephenc", credentials.getId());
        navigator.setTraits(Collections.singletonList(new TopicsTrait("cool, great,was-awesome")));
        final Set<String> projectNames = new HashSet<>();
        final SCMSourceObserver observer = getObserver(projectNames);

        navigator.visitSources(observer);

        assertEquals(projectNames, Collections.singleton("yolo-archived"));
    }

    @Test
    public void fetchOneRepo_BelongingToAuthenticatedUser_ExcludingArchived() throws Exception {
        setCredentials(Collections.singletonList(credentials));
        navigator = navigatorForRepoOwner("stephenc", credentials.getId());
        navigator.setTraits(Collections.singletonList(new ExcludeArchivedRepositoriesTrait()));
        final Set<String> projectNames = new HashSet<>();
        final SCMSourceObserver observer = getObserver(projectNames);

        navigator.visitSources(SCMSourceObserver.filter(observer, "yolo-archived"));

        assertThat(projectNames, empty());
    }

    @Test
    public void fetchOneRepo_ExcludingPublic() throws Exception {
        setCredentials(Collections.singletonList(credentials));
        navigator = navigatorForRepoOwner("stephenc", credentials.getId());
        navigator.setTraits(Collections.singletonList(new ExcludePublicRepositoriesTrait()));
        final Set<String> projectNames = new HashSet<>();
        final SCMSourceObserver observer = getObserver(projectNames);

        navigator.visitSources(SCMSourceObserver.filter(observer, "yolo-private"));

        assertThat(projectNames, containsInAnyOrder("yolo-private"));
    }

    @Test
    public void fetchOneRepo_ExcludingPrivate() throws Exception {
        setCredentials(Collections.singletonList(credentials));
        navigator = navigatorForRepoOwner("stephenc", credentials.getId());
        navigator.setTraits(Collections.singletonList(new ExcludePrivateRepositoriesTrait()));
        final Set<String> projectNames = new HashSet<>();
        final SCMSourceObserver observer = getObserver(projectNames);

        navigator.visitSources(SCMSourceObserver.filter(observer, "yolo"));

        assertThat(projectNames, containsInAnyOrder("yolo"));
    }

    @Test
    public void fetchOneRepo_ExcludingForked() throws Exception {
        setCredentials(Collections.singletonList(credentials));
        navigator = navigatorForRepoOwner("stephenc", credentials.getId());
        navigator.setTraits(Collections.singletonList(new ExcludeForkedRepositoriesTrait()));
        final Set<String> projectNames = new HashSet<>();
        final SCMSourceObserver observer = getObserver(projectNames);

        navigator.visitSources(SCMSourceObserver.filter(observer, "yolo-private"));

        assertThat(projectNames, containsInAnyOrder("yolo-private"));
    }

    @Test
    public void fetchOneRepo_BelongingToOrg() throws Exception {
        final Set<String> projectNames = new HashSet<>();
        final SCMSourceObserver observer = getObserver(projectNames);

        navigator.visitSources(SCMSourceObserver.filter(observer, "yolo-archived"));

        assertThat(projectNames, containsInAnyOrder("yolo-archived"));
    }

    @Test
    public void fetchOneRepo_BelongingToOrg_ExcludingArchived() throws Exception {
        navigator.setTraits(Collections.singletonList(new ExcludeArchivedRepositoriesTrait()));
        final Set<String> projectNames = new HashSet<>();
        final SCMSourceObserver observer = getObserver(projectNames);

        navigator.visitSources(SCMSourceObserver.filter(observer, "yolo-archived"));

        assertThat(projectNames, empty());
    }

    @Test
    public void fetchOneRepo_BelongingToUser() throws Exception {
        navigator = navigatorForRepoOwner("stephenc", null);
        final Set<String> projectNames = new HashSet<>();
        final SCMSourceObserver observer = getObserver(projectNames);

        navigator.visitSources(SCMSourceObserver.filter(observer, "yolo-archived"));

        assertThat(projectNames, containsInAnyOrder("yolo-archived"));
    }

    @Test
    public void fetchOneRepo_BelongingToUser_ExcludingArchived() throws Exception {
        navigator = navigatorForRepoOwner("stephenc", null);
        navigator.setTraits(Collections.singletonList(new ExcludeArchivedRepositoriesTrait()));
        final Set<String> projectNames = new HashSet<>();
        final SCMSourceObserver observer = getObserver(projectNames);

        navigator.visitSources(SCMSourceObserver.filter(observer, "yolo-archived"));

        assertThat(projectNames, empty());
    }

    @Test
    public void fetchRepos_BelongingToAuthenticatedUser() throws Exception {
        setCredentials(Collections.singletonList(credentials));
        navigator = navigatorForRepoOwner("stephenc", credentials.getId());
        final Set<String> projectNames = new HashSet<>();
        final SCMSourceObserver observer = getObserver(projectNames);

        navigator.visitSources(observer);

        assertThat(projectNames, containsInAnyOrder("yolo", "yolo-archived"));
    }

    @Test
    public void fetchRepos_BelongingToAuthenticatedUser_ExcludingArchived() throws Exception {
        setCredentials(Collections.singletonList(credentials));
        navigator = navigatorForRepoOwner("stephenc", credentials.getId());
        navigator.setTraits(Collections.singletonList(new ExcludeArchivedRepositoriesTrait()));
        final Set<String> projectNames = new HashSet<>();
        final SCMSourceObserver observer = getObserver(projectNames);

        navigator.visitSources(observer);

        assertThat(projectNames, containsInAnyOrder("yolo"));
    }

    @Test
    public void fetchRepos_BelongingToOrg() throws Exception {
        final Set<String> projectNames = new HashSet<>();
        final SCMSourceObserver observer = getObserver(projectNames);

        navigator.visitSources(SCMSourceObserver.filter(observer, "unknown", "basic", "yolo", "yolo-archived"));

        assertThat(projectNames, containsInAnyOrder("basic", "yolo", "yolo-archived"));
    }

    @Test
    public void fetchRepos_BelongingToOrg_ExcludingArchived() throws Exception {
        navigator.setTraits(Collections.singletonList(new ExcludeArchivedRepositoriesTrait()));
        final Set<String> projectNames = new HashSet<>();
        final SCMSourceObserver observer = getObserver(projectNames);

        navigator.visitSources(SCMSourceObserver.filter(observer, "unknown", "basic", "yolo", "yolo-archived"));

        assertThat(projectNames, containsInAnyOrder("basic", "yolo"));
    }

    @Test
    public void fetchRepos_BelongingToOrg_ExcludingPublic() throws Exception {
        navigator.setTraits(Collections.singletonList(new ExcludePublicRepositoriesTrait()));
        final Set<String> projectNames = new HashSet<>();
        final SCMSourceObserver observer = getObserver(projectNames);

        navigator.visitSources(
                SCMSourceObserver.filter(observer, "Hello-World", "github-branch-source-plugin", "yolo-private"));

        assertThat(projectNames, containsInAnyOrder("yolo-private"));
    }

    @Test
    public void fetchRepos_BelongingToOrg_ExcludingPrivate() throws Exception {
        navigator.setTraits(Collections.singletonList(new ExcludePrivateRepositoriesTrait()));
        final Set<String> projectNames = new HashSet<>();
        final SCMSourceObserver observer = getObserver(projectNames);

        navigator.visitSources(SCMSourceObserver.filter(observer, "basic", "advanced", "yolo-private"));

        assertThat(projectNames, containsInAnyOrder("basic", "advanced"));
    }

    @Test
    public void fetchRepos_BelongingToUser() throws Exception {
        navigator = navigatorForRepoOwner("stephenc", null);
        final Set<String> projectNames = new HashSet<>();
        final SCMSourceObserver observer = getObserver(projectNames);

        navigator.visitSources(observer);

        assertThat(projectNames, containsInAnyOrder("yolo", "yolo-archived", "yolo-private"));
    }

    @Test
    public void fetchRepos_BelongingToUser_ExcludingArchived() throws Exception {
        navigator = navigatorForRepoOwner("stephenc", null);
        navigator.setTraits(Collections.singletonList(new ExcludeArchivedRepositoriesTrait()));
        final Set<String> projectNames = new HashSet<>();
        final SCMSourceObserver observer = getObserver(projectNames);

        navigator.visitSources(observer);

        assertThat(projectNames, containsInAnyOrder("yolo"));
    }

    @Test
    public void appliesFilters() throws Exception {
        final Set<String> projectNames = new HashSet<>();
        final SCMSourceObserver observer = getObserver(projectNames);

        navigator.visitSources(SCMSourceObserver.filter(observer, "yolo", "rando-unknown"));

        assertEquals(projectNames, Collections.singleton("yolo"));
    }

    @Test
    public void fetchActions() throws Exception {
        assertThat(
                navigator.fetchActions(Mockito.mock(SCMNavigatorOwner.class), null, null),
                Matchers.containsInAnyOrder(
                        Matchers.is(
                                new ObjectMetadataAction("CloudBeers, Inc.", null, "https://github.com/cloudbeers")),
                        Matchers.is(new GitHubOrgMetadataAction("https://avatars.githubusercontent.com/u/4181899?v=3")),
                        Matchers.is(new GitHubLink("icon-github-logo", "https://github.com/cloudbeers"))));
    }

    @Test
    public void doFillScanCredentials() throws Exception {
        final GitHubSCMNavigator.DescriptorImpl d =
                r.jenkins.getDescriptorByType(GitHubSCMNavigator.DescriptorImpl.class);
        final MockFolder dummy = r.createFolder("dummy");
        SecurityRealm realm = r.jenkins.getSecurityRealm();
        AuthorizationStrategy strategy = r.jenkins.getAuthorizationStrategy();
        try {
            r.jenkins.setSecurityRealm(r.createDummySecurityRealm());
            MockAuthorizationStrategy mockStrategy = new MockAuthorizationStrategy();
            mockStrategy.grant(Jenkins.ADMINISTER).onRoot().to("admin");
            mockStrategy.grant(Item.CONFIGURE).onItems(dummy).to("bob");
            mockStrategy.grant(Item.EXTENDED_READ).onItems(dummy).to("jim");
            r.jenkins.setAuthorizationStrategy(mockStrategy);
            try (ACLContext ctx = ACL.as(User.getById("admin", true).impersonate())) {
                ListBoxModel rsp = d.doFillCredentialsIdItems(dummy, "", "does-not-exist");
                assertThat("Expecting only the provided value so that form config unchanged", rsp, hasSize(1));
                assertThat(
                        "Expecting only the provided value so that form config unchanged",
                        rsp.get(0).value,
                        is("does-not-exist"));
                rsp = d.doFillCredentialsIdItems(null, "", "does-not-exist");
                assertThat("Expecting just the empty entry", rsp, hasSize(1));
                assertThat("Expecting just the empty entry", rsp.get(0).value, is(""));
            }
            try (ACLContext ctx = ACL.as(User.getById("bob", true).impersonate())) {
                ListBoxModel rsp = d.doFillCredentialsIdItems(dummy, "", "does-not-exist");
                assertThat("Expecting just the empty entry", rsp, hasSize(1));
                assertThat("Expecting just the empty entry", rsp.get(0).value, is(""));
                rsp = d.doFillCredentialsIdItems(null, "", "does-not-exist");
                assertThat("Expecting only the provided value so that form config unchanged", rsp, hasSize(1));
                assertThat(
                        "Expecting only the provided value so that form config unchanged",
                        rsp.get(0).value,
                        is("does-not-exist"));
            }
            try (ACLContext ctx = ACL.as(User.getById("jim", true).impersonate())) {
                ListBoxModel rsp = d.doFillCredentialsIdItems(dummy, "", "does-not-exist");
                assertThat("Expecting just the empty entry", rsp, hasSize(1));
                assertThat("Expecting just the empty entry", rsp.get(0).value, is(""));
            }
            try (ACLContext ctx = ACL.as(User.getById("sue", true).impersonate())) {
                ListBoxModel rsp = d.doFillCredentialsIdItems(dummy, "", "does-not-exist");
                assertThat("Expecting only the provided value so that form config unchanged", rsp, hasSize(1));
                assertThat(
                        "Expecting only the provided value so that form config unchanged",
                        rsp.get(0).value,
                        is("does-not-exist"));
            }
        } finally {
            r.jenkins.setSecurityRealm(realm);
            r.jenkins.setAuthorizationStrategy(strategy);
            r.jenkins.remove(dummy);
        }
    }

    private SCMSourceObserver getObserver(Collection<String> names) {
        return new SCMSourceObserver() {
            @NonNull
            @Override
            public SCMSourceOwner getContext() {
                return scmSourceOwner;
            }

            @NonNull
            @Override
            public TaskListener getListener() {
                return new LogTaskListener(Logger.getAnonymousLogger(), Level.INFO);
            }

            @NonNull
            @Override
            public ProjectObserver observe(@NonNull String projectName) throws IllegalArgumentException {
                names.add(projectName);
                return new NoOpProjectObserver();
            }

            @Override
            public void addAttribute(@NonNull String key, @Nullable Object value)
                    throws IllegalArgumentException, ClassCastException {}
        };
    }
}
