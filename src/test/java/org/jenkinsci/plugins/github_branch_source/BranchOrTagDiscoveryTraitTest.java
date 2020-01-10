package org.jenkinsci.plugins.github_branch_source;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThat;
import static org.junit.Assume.assumeThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import jenkins.scm.api.SCMHeadObserver;
import jenkins.scm.api.trait.SCMHeadFilter;
import jenkins.scm.api.trait.SCMHeadPrefilter;
import org.hamcrest.Matcher;
import org.jenkinsci.plugins.github_branch_source.BranchOrTagAgeDiscoveryTrait.OnlyHeadCommitsNewerThanSCMHeadFilter;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.kohsuke.github.GHBranch;
import org.kohsuke.github.GHCommit;
import org.kohsuke.github.GHRepository;

public class BranchOrTagDiscoveryTraitTest {
    @ClassRule
    public static JenkinsRule j = new JenkinsRule();

    @Test
    public void given__maxDaysOld__3__when__appliedToContext__then__filter__has_3() throws Exception {
        int maxDaysOld = 3;
        
        GitHubSCMSourceContext ctx = new GitHubSCMSourceContext(null, SCMHeadObserver.none());
        assumeThat(ctx.wantBranches(), is(false));
        assertThat(ctx.wantTags(), is(false));
        assumeThat(ctx.wantPRs(), is(false));
        assumeThat(ctx.prefilters(), is(Collections.<SCMHeadPrefilter>emptyList()));
        assumeThat(ctx.filters(), is(Collections.<SCMHeadFilter>emptyList()));
        assumeThat(ctx.authorities(), not((Matcher) hasItem(
                instanceOf(BranchOrTagAgeDiscoveryTrait.BranchOrTagAgeSCMHeadAuthority.class)
        )));
        BranchOrTagAgeDiscoveryTrait instance = new BranchOrTagAgeDiscoveryTrait(maxDaysOld);
        instance.decorateContext(ctx);
        assertThat(ctx.wantBranches(), is(false));
        assertThat(ctx.wantTags(), is(false));
        assertThat(ctx.wantPRs(), is(false));
        assertThat(ctx.prefilters(), is(Collections.<SCMHeadPrefilter>emptyList()));
        assertThat(ctx.filters(),
                contains(instanceOf(BranchOrTagAgeDiscoveryTrait.OnlyHeadCommitsNewerThanSCMHeadFilter.class)));
        assertThat(ctx.filters().size(), is(1));
        OnlyHeadCommitsNewerThanSCMHeadFilter filter = (OnlyHeadCommitsNewerThanSCMHeadFilter) ctx.filters().get(0);
        assertThat(filter.getMaxDaysOld(), is(maxDaysOld)); 
        assertThat(ctx.authorities(), (Matcher) hasItem(
                instanceOf(BranchOrTagAgeDiscoveryTrait.BranchOrTagAgeSCMHeadAuthority.class)
        ));
    }
    
    @Test
    public void given__maxDaysOld__3__when__commitIs5DaysOld__then__isExcluded__returns__true() throws Exception {
        int maxDaysOld = 3;
        int daysSinceCommit = 5;

        assertThat(isDaysSinceCommitExcludedDueToMaxDaysOld(maxDaysOld, getCommitDate(daysSinceCommit)), is(true));
    }
    
    @Test
    public void given__maxDaysOld__3__when__commitIs2DaysOld__then__isExcluded__returns__false() throws Exception {
        int maxDaysOld = 3;
        int daysSinceCommit = 2;
        
        assertThat(isDaysSinceCommitExcludedDueToMaxDaysOld(maxDaysOld, getCommitDate(daysSinceCommit)), is(false));
    }
    
    @Test
    public void given__maxDaysOld__0__when__commitIs5DaysOld__then__isExcluded__returns__false() throws Exception {
        int maxDaysOld = 0;
        int daysSinceCommit = 5;
        
        assertThat(isDaysSinceCommitExcludedDueToMaxDaysOld(maxDaysOld, getCommitDate(daysSinceCommit)), is(false));
    }
    
    @Test
    public void given__maxDaysOld__4__when__timestampIs0__then__isExcluded__returns__false() throws Exception {
        int maxDaysOld = 4;
        Date commitDate = new Date(0);
        
        assertThat(isDaysSinceCommitExcludedDueToMaxDaysOld(maxDaysOld, commitDate), is(false));
    }
    
    private Date getCommitDate(int daysSinceCommit) {
        return new Date(new Date().getTime() - TimeUnit.DAYS.toMillis(daysSinceCommit));
    }
    
    private boolean isDaysSinceCommitExcludedDueToMaxDaysOld(int maxDaysOld, Date commitDate) throws Exception {
        String branchName = "sameBranch";
        String commitSHA1 = "testSHA1";

        GitHubSCMSourceRequest mockedRequest = mock(GitHubSCMSourceRequest.class);
        BranchSCMHead mockedHead = mock(BranchSCMHead.class);
        GHRepository mockedRepo = mock(GHRepository.class);
        GHBranch mockedBranch = mock(GHBranch.class);
        GHCommit mockedCommit = mock(GHCommit.class);
        List<GHBranch> branches = new ArrayList<GHBranch>();
        
        when(mockedCommit.getCommitDate()).thenReturn(commitDate);
        when(mockedBranch.getSHA1()).thenReturn(commitSHA1);
        doReturn(mockedCommit).when(mockedRepo).getCommit(commitSHA1);
        when(mockedBranch.getOwner()).thenReturn(mockedRepo);
        when(mockedBranch.getName()).thenReturn(branchName);
        when(mockedHead.getName()).thenReturn(branchName);
        
        branches.add(mockedBranch);
        
        OnlyHeadCommitsNewerThanSCMHeadFilter filter = new OnlyHeadCommitsNewerThanSCMHeadFilter(maxDaysOld, branches);
        
        return filter.isExcluded(mockedRequest, mockedHead);
    }

}
