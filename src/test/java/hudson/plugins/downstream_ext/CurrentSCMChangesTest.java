package hudson.plugins.downstream_ext;

import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.Cause;
import hudson.model.ItemGroup;
import hudson.model.Result;
import hudson.model.TaskListener;
import hudson.plugins.downstream_ext.DownstreamTrigger.Strategy;
import hudson.scm.ChangeLogSet;
import hudson.scm.PollingResult;
import hudson.scm.PollingResult.Change;
import hudson.scm.SCM;
import org.junit.Before;
import org.junit.Test;
import org.jvnet.hudson.test.FakeChangeLogSCM;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("rawtypes")
public class CurrentSCMChangesTest
{

	private AbstractProject upstream;
	private AbstractProject downstream;


    @Before
	public void setup() {
		upstream = mock(AbstractProject.class);
		ItemGroup parent = mock(ItemGroup.class);
		when(parent.getUrl()).thenReturn("http://foo");
		when(parent.getFullName()).thenReturn("Parent full name");
		when(upstream.getParent()).thenReturn(parent);


        initUpstreamBuildWithSCM();

		this.downstream = createDownstreamProject();
	}

    private AbstractBuild initUpstreamBuildWithSCM()
    {
        AbstractBuild upstreamBuild = mock(AbstractBuild.class);
        when(upstreamBuild.getProject()).thenReturn(upstream);
        when(upstreamBuild.getResult()).thenReturn(Result.SUCCESS);

        List<FakeChangeLogSCM.EntryImpl> entries=new LinkedList<FakeChangeLogSCM.EntryImpl>();
        entries.add(new FakeChangeLogSCM.EntryImpl());
        ChangeLogSet<? extends ChangeLogSet.Entry> changes = new FakeChangeLogSCM.FakeChangeLogSet(upstreamBuild, entries);

        when(upstreamBuild.getChangeSet()).thenReturn(changes);
        return upstreamBuild;
    }

    private AbstractBuild initUpstreamBuildWithError()
    {
        AbstractBuild upstreamBuild = mock(AbstractBuild.class);
        when(upstreamBuild.getProject()).thenReturn(upstream);
        when(upstreamBuild.getResult()).thenReturn(Result.FAILURE);

        List<FakeChangeLogSCM.EntryImpl> entries = new LinkedList<FakeChangeLogSCM.EntryImpl>();
        ChangeLogSet<? extends ChangeLogSet.Entry> changes = new FakeChangeLogSCM.FakeChangeLogSet(upstreamBuild, entries);

        when(upstreamBuild.getChangeSet()).thenReturn(changes);
        return upstreamBuild;
    }


    private AbstractBuild initUpstreamBuildWithoutSCM()
    {
        AbstractBuild upstreamBuild = mock(AbstractBuild.class);
        when(upstreamBuild.getProject()).thenReturn(upstream);
        when(upstreamBuild.getResult()).thenReturn(Result.SUCCESS);

        List<FakeChangeLogSCM.EntryImpl> entries = new LinkedList<FakeChangeLogSCM.EntryImpl>();
        ChangeLogSet<? extends ChangeLogSet.Entry> changes = new FakeChangeLogSCM.FakeChangeLogSet(upstreamBuild, entries);

        when(upstreamBuild.getChangeSet()).thenReturn(changes);
        return upstreamBuild;
    }


    private static AbstractProject createDownstreamProject() {
        return mock(AbstractProject.class);
	}

	/**
	 */
	@Test
	public void testTriggerSuccessWithChanges() throws IOException, InterruptedException {

		DownstreamDependency dependency = new DownstreamDependency(upstream, downstream,
				new DownstreamTrigger("", Result.SUCCESS, false, true, Strategy.AND_HIGHER,
						null));
		Action action = mock(Action.class);

		boolean trigger = dependency.shouldTriggerBuild(initUpstreamBuildWithSCM(),
				TaskListener.NULL, Collections.singletonList(action));
		assertTrue(trigger);
	}

    /**
     */
    @Test
    public void testTriggerSuccessNoChanges() throws IOException, InterruptedException
    {

        DownstreamDependency dependency = new DownstreamDependency(upstream, downstream,
                new DownstreamTrigger("", Result.SUCCESS, false, true, Strategy.AND_HIGHER,
                        null));
        Action action = mock(Action.class);

        boolean trigger = dependency.shouldTriggerBuild(initUpstreamBuildWithoutSCM(),
                TaskListener.NULL, Collections.singletonList(action));
        assertFalse(trigger);
    }


    /**
     */
    @Test
    public void testTriggerFailure() throws IOException, InterruptedException
    {

        DownstreamDependency dependency = new DownstreamDependency(upstream, downstream,
                new DownstreamTrigger("", Result.SUCCESS, false, true, Strategy.AND_HIGHER,
                        null));
        Action action = mock(Action.class);

        boolean trigger = dependency.shouldTriggerBuild(initUpstreamBuildWithError(),
                TaskListener.NULL, Collections.singletonList(action));
        assertFalse(trigger);
    }

}
