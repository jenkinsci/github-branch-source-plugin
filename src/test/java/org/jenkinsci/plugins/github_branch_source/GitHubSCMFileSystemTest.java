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
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeThat;

import hudson.AbortException;
import jenkins.plugins.git.AbstractGitSCMSource;
import jenkins.scm.api.SCMFile;
import jenkins.scm.api.SCMFileSystem;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMHeadOrigin;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.mixin.ChangeRequestCheckoutStrategy;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class GitHubSCMFileSystemTest extends AbstractGitHubWireMockTest {

    public static SCMHead master = new BranchSCMHead("master");
    private final SCMRevision revision;

    public static PullRequestSCMHead prHead = new PullRequestSCMHead(
            "PR-2",
            "stephenc",
            "yolo",
            "master",
            2,
            (BranchSCMHead) master,
            SCMHeadOrigin.Fork.DEFAULT,
            ChangeRequestCheckoutStrategy.HEAD);
    public static PullRequestSCMRevision prHeadRevision = new PullRequestSCMRevision(
            prHead, "8f1314fc3c8284d8c6d5886d473db98f2126071c", "c0e024f89969b976da165eecaa71e09dc60c3da1");

    public static PullRequestSCMHead prMerge = new PullRequestSCMHead(
            "PR-2",
            "stephenc",
            "yolo",
            "master",
            2,
            (BranchSCMHead) master,
            SCMHeadOrigin.Fork.DEFAULT,
            ChangeRequestCheckoutStrategy.MERGE);
    public static PullRequestSCMRevision prMergeRevision = new PullRequestSCMRevision(
            prMerge,
            "8f1314fc3c8284d8c6d5886d473db98f2126071c",
            "c0e024f89969b976da165eecaa71e09dc60c3da1",
            "38814ca33833ff5583624c29f305be9133f27a40");

    public static PullRequestSCMRevision prMergeInvalidRevision = new PullRequestSCMRevision(
            prMerge, "8f1314fc3c8284d8c6d5886d473db98f2126071c", "c0e024f89969b976da165eecaa71e09dc60c3da1", null);

    public static PullRequestSCMRevision prMergeNotMergeableRevision = new PullRequestSCMRevision(
            prMerge,
            "8f1314fc3c8284d8c6d5886d473db98f2126071c",
            "c0e024f89969b976da165eecaa71e09dc60c3da1",
            PullRequestSCMRevision.NOT_MERGEABLE_HASH);

    private GitHubSCMSource source;

    public GitHubSCMFileSystemTest(String revision) {
        this.revision = revision == null ? null : new AbstractGitSCMSource.SCMRevisionImpl(master, revision);
    }

    @Parameterized.Parameters(name = "{index}: revision={0}")
    public static String[] revisions() {
        return new String[] {
            "c0e024f89969b976da165eecaa71e09dc60c3da1", // Pull Request #2, unmerged but exposed on target
            // repo
            "e301dc6d5bb7e6e18d80e85f19caa92c74e15e96",
            null
        };
    }

    @Before
    @Override
    public void prepareMockGitHub() {
        super.prepareMockGitHub();
        source = new GitHubSCMSource(
                null,
                "http://localhost:" + githubApi.port(),
                GitHubSCMSource.DescriptorImpl.SAME,
                null,
                "cloudbeers",
                "yolo");
    }

    @Override
    void prepareMockGitHubFileMappings() {
        super.prepareMockGitHubFileMappings();
        githubApi.stubFor(get(urlEqualTo("/repos/cloudbeers/yolo/pulls/2"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json; charset=utf-8")
                        .withBodyFile("body-yolo-pulls-2-mergeable-true.json")));
    }

    @Test
    public void haveFilesystem() throws Exception {
        assertThat(SCMFileSystem.of(source, master, revision), notNullValue());
    }

    @Test
    public void rootIsADirectory() throws Exception {
        SCMFileSystem fs = SCMFileSystem.of(source, master, revision);
        assertThat(fs.getRoot().getType(), is(SCMFile.Type.DIRECTORY));
    }

    @Test
    public void listFilesInRoot() throws Exception {
        SCMFileSystem fs = SCMFileSystem.of(source, master, revision);
        assertThat(fs.getRoot().children(), hasItem(Matchers.<SCMFile>hasProperty("name", is("README.md"))));
    }

    @Test
    public void readmeIsAFile() throws Exception {
        SCMFileSystem fs = SCMFileSystem.of(source, master, revision);
        assertThat(fs.getRoot().child("README.md").getType(), is(SCMFile.Type.REGULAR_FILE));
    }

    @Test
    public void readmeContents() throws Exception {
        SCMFileSystem fs = SCMFileSystem.of(source, master, revision);
        assertThat(fs.getRoot().child("README.md").contentAsString(), containsString("yolo"));
    }

    @Test
    public void readFileFromDir() throws Exception {
        assumeThat(revision, instanceOf(AbstractGitSCMSource.SCMRevisionImpl.class));
        assumeThat(
                ((AbstractGitSCMSource.SCMRevisionImpl) revision).getHash(),
                is("c0e024f89969b976da165eecaa71e09dc60c3da1"));
        SCMFileSystem fs = SCMFileSystem.of(source, master, revision);

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
    public void resolveDir() throws Exception {
        assumeThat(revision, instanceOf(AbstractGitSCMSource.SCMRevisionImpl.class));
        assumeThat(
                ((AbstractGitSCMSource.SCMRevisionImpl) revision).getHash(),
                is("c0e024f89969b976da165eecaa71e09dc60c3da1"));
        SCMFileSystem fs = SCMFileSystem.of(source, master, revision);
        assertThat(fs.getRoot().child("fu").getType(), is(SCMFile.Type.DIRECTORY));
    }

    @Test
    public void listDir() throws Exception {
        assumeThat(revision, instanceOf(AbstractGitSCMSource.SCMRevisionImpl.class));
        assumeThat(
                ((AbstractGitSCMSource.SCMRevisionImpl) revision).getHash(),
                is("c0e024f89969b976da165eecaa71e09dc60c3da1"));
        SCMFileSystem fs = SCMFileSystem.of(source, master, revision);
        assertThat(
                fs.getRoot().child("fu").children(), hasItem(Matchers.<SCMFile>hasProperty("name", is("manchu.txt"))));
    }

    @Test
    public void resolveDirPRHead() throws Exception {
        assumeThat(revision, nullValue());

        assertThat(prHeadRevision.isMerge(), is(false));

        SCMFileSystem fs = SCMFileSystem.of(source, prHead, prHeadRevision);
        assertThat(fs, instanceOf(GitHubSCMFileSystem.class));

        // We can't check the sha, but we can check last modified
        // which are different for head or merge
        assertThat(((GitHubSCMFileSystem) fs).lastModified(), is(1480691047000L));

        assertThat(fs.getRoot().child("fu").getType(), is(SCMFile.Type.DIRECTORY));
    }

    @Test
    public void resolveDirPRMerge() throws Exception {
        assumeThat(revision, nullValue());

        assertThat(prMergeRevision.isMerge(), is(true));

        SCMFileSystem fs = SCMFileSystem.of(source, prMerge, prMergeRevision);
        assertThat(fs, instanceOf(GitHubSCMFileSystem.class));

        // We can't check the sha, but we can check last modified
        // which are different for head or merge
        assertThat(((GitHubSCMFileSystem) fs).lastModified(), is(1480777447000L));

        assertThat(fs.getRoot().child("fu").getType(), is(SCMFile.Type.DIRECTORY));
    }

    @Test
    public void resolveDirPRInvalidMerge() throws Exception {
        assumeThat(revision, nullValue());

        assertThat(prMergeInvalidRevision.isMerge(), is(true));

        SCMFileSystem fs = SCMFileSystem.of(source, prMerge, prMergeInvalidRevision);
        assertThat(fs, nullValue());
    }

    @Test(expected = AbortException.class)
    public void resolveDirPRNotMergeable() throws Exception {
        assumeThat(revision, nullValue());

        assertThat(prMergeNotMergeableRevision.isMerge(), is(true));

        SCMFileSystem fs = SCMFileSystem.of(source, prMerge, prMergeNotMergeableRevision);
        fail();
    }
}
