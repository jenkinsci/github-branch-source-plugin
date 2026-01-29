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

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.core.IsNull.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import hudson.AbortException;
import java.util.stream.Stream;
import jenkins.plugins.git.AbstractGitSCMSource;
import jenkins.scm.api.SCMFile;
import jenkins.scm.api.SCMFileSystem;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMHeadOrigin;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.mixin.ChangeRequestCheckoutStrategy;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedClass;
import org.junit.jupiter.params.provider.MethodSource;

@ParameterizedClass(name = "{index}: revision={0}")
@MethodSource("revisions")
class GitHubSCMFileSystemTest extends AbstractGitHubWireMockTest {

    private static final SCMHead MASTER = new BranchSCMHead("master");
    private final SCMRevision revision;

    private static final PullRequestSCMHead PR_HEAD = new PullRequestSCMHead(
            "PR-2",
            "stephenc",
            "yolo",
            "master",
            2,
            (BranchSCMHead) MASTER,
            SCMHeadOrigin.Fork.DEFAULT,
            ChangeRequestCheckoutStrategy.HEAD);
    private static final PullRequestSCMRevision PR_HEAD_REVISION = new PullRequestSCMRevision(
            PR_HEAD, "8f1314fc3c8284d8c6d5886d473db98f2126071c", "c0e024f89969b976da165eecaa71e09dc60c3da1");

    private static final PullRequestSCMHead PR_MERGE = new PullRequestSCMHead(
            "PR-2",
            "stephenc",
            "yolo",
            "master",
            2,
            (BranchSCMHead) MASTER,
            SCMHeadOrigin.Fork.DEFAULT,
            ChangeRequestCheckoutStrategy.MERGE);
    private static final PullRequestSCMRevision PR_MERGE_REVISION = new PullRequestSCMRevision(
            PR_MERGE,
            "8f1314fc3c8284d8c6d5886d473db98f2126071c",
            "c0e024f89969b976da165eecaa71e09dc60c3da1",
            "38814ca33833ff5583624c29f305be9133f27a40");

    private static final PullRequestSCMRevision PR_MERGE_INVALID_REVISION = new PullRequestSCMRevision(
            PR_MERGE, "8f1314fc3c8284d8c6d5886d473db98f2126071c", "c0e024f89969b976da165eecaa71e09dc60c3da1", null);

    private static final PullRequestSCMRevision PR_MERGE_NOT_MERGEABLE_REVISION = new PullRequestSCMRevision(
            PR_MERGE,
            "8f1314fc3c8284d8c6d5886d473db98f2126071c",
            "c0e024f89969b976da165eecaa71e09dc60c3da1",
            PullRequestSCMRevision.NOT_MERGEABLE_HASH);

    private GitHubSCMSource source;

    public GitHubSCMFileSystemTest(String revision) {
        this.revision = revision == null ? null : new AbstractGitSCMSource.SCMRevisionImpl(MASTER, revision);
    }

    static Stream<String> revisions() {
        return Stream.of(
                "c0e024f89969b976da165eecaa71e09dc60c3da1", // Pull Request #2, unmerged but exposed on target
                "e301dc6d5bb7e6e18d80e85f19caa92c74e15e96", // repo
                null);
    }

    @Override
    @BeforeEach
    void beforeEach() throws Exception {
        super.beforeEach();
        githubApi.stubFor(get(urlEqualTo("/repos/cloudbeers/yolo/pulls/2"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json; charset=utf-8")
                        .withBodyFile("body-yolo-pulls-2-mergeable-true.json")));
        source = new GitHubSCMSource(
                null,
                "http://localhost:" + githubApi.getPort(),
                GitHubSCMSource.DescriptorImpl.SAME,
                null,
                "cloudbeers",
                "yolo");
    }

    @Test
    void haveFilesystem() throws Exception {
        assertThat(SCMFileSystem.of(source, MASTER, revision), notNullValue());
    }

    @Test
    void rootIsADirectory() throws Exception {
        SCMFileSystem fs = SCMFileSystem.of(source, MASTER, revision);
        assertThat(fs.getRoot().getType(), is(SCMFile.Type.DIRECTORY));
    }

    @Test
    void listFilesInRoot() throws Exception {
        SCMFileSystem fs = SCMFileSystem.of(source, MASTER, revision);
        assertThat(fs.getRoot().children(), hasItem(Matchers.<SCMFile>hasProperty("name", is("README.md"))));
    }

    @Test
    void readmeIsAFile() throws Exception {
        SCMFileSystem fs = SCMFileSystem.of(source, MASTER, revision);
        assertThat(fs.getRoot().child("README.md").getType(), is(SCMFile.Type.REGULAR_FILE));
    }

    @Test
    void readmeContents() throws Exception {
        SCMFileSystem fs = SCMFileSystem.of(source, MASTER, revision);
        assertThat(fs.getRoot().child("README.md").contentAsString(), containsString("yolo"));
    }

    @Test
    void readFileFromDir() throws Exception {
        assumeTrue(revision instanceof AbstractGitSCMSource.SCMRevisionImpl);
        assumeTrue(((AbstractGitSCMSource.SCMRevisionImpl) revision)
                .getHash()
                .equals("c0e024f89969b976da165eecaa71e09dc60c3da1"));
        SCMFileSystem fs = SCMFileSystem.of(source, MASTER, revision);

        String expected = "Some text\n";
        // In previous versions of github-api, GHContent.read() (called by contentAsString())
        // would pull from the "raw" url of the GHContent instance.
        // Thus on windows, if somebody did not configure Git correctly,
        // the checkout may have "fixed" line endings that we needed to handle.
        // The problem with the raw url data is that it can get out of sync when from the actual
        // content.
        // The GitHub API info stays sync'd and correct, so now GHContent.read() pulls from mime encoded
        // data
        // in the GHContent record itself. Keeping this for reference in case it changes again.
        //        try (InputStream inputStream =
        // getClass().getResourceAsStream("/raw/__files/body-fu-bar.txt-b4k4I.txt")) {
        //            if (inputStream != null) {
        //                expected = IOUtils.toString(inputStream, StandardCharsets.US_ASCII);
        //            }
        //        } catch (IOException e) {
        //            // ignore
        //        }
        assertThat(fs.getRoot().child("fu/bar.txt").contentAsString(), is(expected));
    }

    @Test
    void resolveDir() throws Exception {
        assumeTrue(revision instanceof AbstractGitSCMSource.SCMRevisionImpl);
        assumeTrue(((AbstractGitSCMSource.SCMRevisionImpl) revision)
                .getHash()
                .equals("c0e024f89969b976da165eecaa71e09dc60c3da1"));
        SCMFileSystem fs = SCMFileSystem.of(source, MASTER, revision);
        assertThat(fs.getRoot().child("fu").getType(), is(SCMFile.Type.DIRECTORY));
    }

    @Test
    void listDir() throws Exception {
        assumeTrue(revision instanceof AbstractGitSCMSource.SCMRevisionImpl);
        assumeTrue(((AbstractGitSCMSource.SCMRevisionImpl) revision)
                .getHash()
                .equals("c0e024f89969b976da165eecaa71e09dc60c3da1"));
        SCMFileSystem fs = SCMFileSystem.of(source, MASTER, revision);
        assertThat(
                fs.getRoot().child("fu").children(), hasItem(Matchers.<SCMFile>hasProperty("name", is("manchu.txt"))));
    }

    @Test
    void resolveDirPRHead() throws Exception {
        assumeTrue(revision == null);

        assertThat(PR_HEAD_REVISION.isMerge(), is(false));

        SCMFileSystem fs = SCMFileSystem.of(source, PR_HEAD, PR_HEAD_REVISION);
        assertThat(fs, instanceOf(GitHubSCMFileSystem.class));

        // We can't check the sha, but we can check last modified
        // which are different for head or merge
        assertThat(((GitHubSCMFileSystem) fs).lastModified(), is(1480691047000L));

        assertThat(fs.getRoot().child("fu").getType(), is(SCMFile.Type.DIRECTORY));
    }

    @Test
    void resolveDirPRMerge() throws Exception {
        assumeTrue(revision == null);

        assertThat(PR_MERGE_REVISION.isMerge(), is(true));

        SCMFileSystem fs = SCMFileSystem.of(source, PR_MERGE, PR_MERGE_REVISION);
        assertThat(fs, instanceOf(GitHubSCMFileSystem.class));

        // We can't check the sha, but we can check last modified
        // which are different for head or merge
        assertThat(((GitHubSCMFileSystem) fs).lastModified(), is(1480777447000L));

        assertThat(fs.getRoot().child("fu").getType(), is(SCMFile.Type.DIRECTORY));
    }

    @Test
    void resolveDirPRInvalidMerge() throws Exception {
        assumeTrue(revision == null);

        assertThat(PR_MERGE_INVALID_REVISION.isMerge(), is(true));

        SCMFileSystem fs = SCMFileSystem.of(source, PR_MERGE, PR_MERGE_INVALID_REVISION);
        assertThat(fs, nullValue());
    }

    @Test
    void resolveDirPRNotMergeable() {
        assumeTrue(revision == null);
        assertThat(PR_MERGE_NOT_MERGEABLE_REVISION.isMerge(), is(true));
        assertThrows(AbortException.class, () -> SCMFileSystem.of(source, PR_MERGE, PR_MERGE_NOT_MERGEABLE_REVISION));
    }
}
