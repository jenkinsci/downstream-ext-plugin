package hudson.plugins.downstream_ext;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.*;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.Cause;
import hudson.model.ItemGroup;
import hudson.model.Result;
import hudson.model.TaskListener;
import hudson.plugins.downstream_ext.DownstreamTrigger.Strategy;
import hudson.scm.SCM;
import hudson.util.StreamTaskListener;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.Test;
import org.mockito.Mockito;

public class AsynchPollingTest {

	/**
	 * Tests that for projects with SCMs which 'requiresWorkspaceForPolling'
	 * polling is started on a different thread.
	 */
	@Test
	@SuppressWarnings("unchecked")
	public void testPollingIsAsynchronous() throws IOException, InterruptedException {
		AbstractProject upstream = mock(AbstractProject.class);
		ItemGroup parent = mock(ItemGroup.class);
		when(parent.getUrl()).thenReturn("http://foo");
		when(upstream.getParent()).thenReturn(parent);
		
		AbstractProject downstream = mock(AbstractProject.class);
		when(downstream.pollSCMChanges(Mockito.<TaskListener>any())).thenReturn(Boolean.TRUE);
		SCM blockingScm = mock(SCM.class);
		when(blockingScm.requiresWorkspaceForPolling()).thenReturn(Boolean.TRUE);
		
		when(downstream.getScm()).thenReturn(blockingScm);
		
		final CountDownLatch startLatch = new CountDownLatch(1);
		final CountDownLatch endLatch = new CountDownLatch(1);
		
		final Cause[] causeHolder = new Cause[1];
		
		DownstreamDependency dependency = new DownstreamDependency(upstream, downstream,
				new DownstreamTrigger("", Result.SUCCESS, true, Strategy.AND_HIGHER)) {

					@Override
					Runnable getPoller(AbstractProject p, Cause cause,
							List<Action> actions) {
						causeHolder[0] = cause;
						final Runnable run = super.getPoller(p, cause, actions);
						
						return new Runnable() {
							@Override
							public void run() {
								startLatch.countDown();
								run.run();
								endLatch.countDown();
							}
						};
					}
		};
		
		AbstractBuild upstreamBuild = mock(AbstractBuild.class);
		when(upstreamBuild.getProject()).thenReturn(upstream);
		when(upstreamBuild.getResult()).thenReturn(Result.SUCCESS);
		
		Action action = mock(Action.class);
		
		boolean triggerSynchronously = dependency.shouldTriggerBuild(upstreamBuild,
				new StreamTaskListener(System.out), Collections.singletonList(action));
		assertFalse(triggerSynchronously);
		
		if(!startLatch.await(1, TimeUnit.MINUTES)) {
			fail("Time out waiting for start latch");
		}
		
		if(!endLatch.await(1, TimeUnit.MINUTES)) {
			fail("Time out waiting for end latch");
		}
		
		verify(downstream).scheduleBuild(downstream.getQuietPeriod(),
				causeHolder[0], action);
	}
}
