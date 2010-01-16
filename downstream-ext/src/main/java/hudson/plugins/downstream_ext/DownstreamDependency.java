package hudson.plugins.downstream_ext;

import java.io.PrintStream;
import java.util.List;

import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.TaskListener;
import hudson.model.DependencyGraph.Dependency;

/**
 * Defines a dependency introduced by the downstream-ext plugin.
 * 
 * @author kutzi
 */
public class DownstreamDependency extends Dependency {

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
	@SuppressWarnings("unchecked")
	public boolean shouldTriggerBuild(AbstractBuild build,
			TaskListener listener, List<Action> actions) {
		if(build.getResult().isBetterOrEqualTo(trigger.getThreshold())) {
            PrintStream logger = listener.getLogger();
            AbstractProject p = getDownstreamProject();
                
            if(trigger.isOnlyIfSCMChanges() && !p.pollSCMChanges(listener)) {
            	logger.println(Messages.DownstreamTrigger_NoSCMChanges(p.getName()));
            	return false;
            }
            return true;
		} else {
			return false;
		}
	}

}
