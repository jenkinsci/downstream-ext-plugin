package hudson.plugins.downstream_ext;

import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.Cause;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.DependencyGraph.Dependency;
import hudson.scm.ChangeLogSet;
import hudson.scm.PollingResult;
import hudson.util.LogTaskListener;
import hudson.util.StreamTaskListener;

import java.io.PrintStream;
import java.io.Serializable;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Defines a dependency introduced by the downstream-ext plugin.
 * 
 * @author kutzi
 */
public class DownstreamDependency extends Dependency {

	private static final Logger LOGGER = Logger.getLogger(DownstreamDependency.class.getName());
	
	private final DownstreamTrigger trigger;

	public DownstreamDependency(AbstractProject<?, ?> upstream, AbstractProject<?, ?> downstream,
			DownstreamTrigger trigger) {
		super(upstream, downstream);
		this.trigger = trigger;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	@SuppressWarnings("rawtypes")
	public boolean shouldTriggerBuild(AbstractBuild build,
			TaskListener listener, List<Action> actions) {
		PrintStream logger = listener.getLogger();
		if(trigger.getStrategy().evaluate(trigger.getThreshold(), build.getResult())) {
            AbstractProject p = getDownstreamProject();

            // when the last build wasn't sucessfull, we trigger dependencies always
            AbstractProject lp = build.getProject();
            if (null!=lp)
            {
                if (null != lp.getLastBuild() && null != lp.getLastUnsuccessfulBuild())
                {
                    if (lp.getLastBuild().getNumber()-1 == (lp.getLastUnsuccessfulBuild().getNumber()))
                    {
                        return true;
                    }
                }
            }

            // check whether local changes are needed or not
            if (trigger.isOnlyIfLocalSCMChanges())
            {
                // check whether current build is triggered by SCM - then there should be changes
                ChangeLogSet changes = build.getChangeSet();
                if (changes.isEmptySet())
                {
                    // no changes - no downstream builds
                    return false;
                }
                return true;
            }

            // we either have local changes now, or they are not needed
            // in both cases we continue with the downstream SCM check

            if(trigger.isOnlyIfSCMChanges()) {
            	if (p.getScm().requiresWorkspaceForPolling()) {
            		// Downstream project locks workspace while building.
            		// If polled synchronously this could make the upstream build
            		// lock for a possibly long time.
            		// See HUDSON-5406
            		logger.println(Messages.DownstreamTrigger_StartedAsynchPoll(p.getName()));
            		Runnable run = getPoller(p, new Cause.UpstreamCause((Run<?,?>)build), actions);
            		DownstreamTrigger.executeForProject(p, run);
            		return false;
            	}
            	
            	if (p.poll(listener).hasChanges()) {
            		return true;
            	} else {
            		logger.println(Messages.DownstreamTrigger_NoSCMChanges(p.getName()));
            		return false;
            	}
            }
            return true;
		} else {
			logger.println(Messages.DownstreamTrigger_ConditionNotMet(trigger.getStrategy().getDisplayName(),
					trigger.getThreshold()));
			return false;
		}
	}

	// Technically it'd be safe to not override equals
	// since superclass implements it well.
	// But maybe that changes in the future.
	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof DownstreamDependency)) {
			return false;
		}
		
		// Currently, there can be only one downstream-ext dependency per project
		// If that'd change later we must check the trigger instance here, too.
		
		return super.equals(obj);
	}
	
	@SuppressWarnings("rawtypes")
	Runnable getPoller(AbstractProject p, Cause cause, List<Action> actions) {
		return new PollRunner(p, cause, actions);
	}
	
	@SuppressWarnings("rawtypes")
	private static class PollRunner implements Runnable {

		private final AbstractProject project;
		private final Cause cause;
		private final List<Action> buildActions;
		private final TaskListener taskListener;

		public PollRunner(AbstractProject p, Cause cause, List<Action> actions) {
			this.project = p;
			this.cause = cause;
			this.buildActions = actions;
			// workaround for HUDSON-5406:
			// some (all?) SCMs require a serializable TaskListener for AbstractProject#pollSCMChanges
			// LogTaskListener is not serializable (at least not up until Hudson 1.352)
			TaskListener tl = new LogTaskListener(LOGGER, Level.INFO);
			if (tl instanceof Serializable) {
			    this.taskListener = tl;
			} else {
			    this.taskListener = StreamTaskListener.fromStdout();
			}
		}
		
		public void run() {
		    LOGGER.info("Polling for SCM changes in " + this.project.getName());
		    PollingResult pollingResult = this.project.poll(this.taskListener);
			if(pollingResult.hasChanges()) {
				LOGGER.info("SCM changes found for " + this.project.getName() + ". Triggering build.");
				if (this.project.scheduleBuild(this.project.getQuietPeriod(), this.cause,
                        buildActions.toArray(new Action[buildActions.size()]))) {
					LOGGER.info("Build of " + this.project.getName() + " scheduled successfully.");
				} else {
					LOGGER.info("No build of " + this.project.getName() + " scheduled - this usually means that another build is already in the queue.");
				}
			} else {
				LOGGER.info(Messages.DownstreamTrigger_NoSCMChanges(this.project.getName()));
			}
		}
	}
}
