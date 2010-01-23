/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Brian Westrich, Martin Eigenbrodt
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
 */
package hudson.plugins.downstream_ext;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.DependecyDeclarer;
import hudson.model.DependencyGraph;
import hudson.model.Hudson;
import hudson.model.Item;
import hudson.model.Items;
import hudson.model.Job;
import hudson.model.Project;
import hudson.model.Result;
import hudson.model.listeners.ItemListener;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.BuildTrigger;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;

import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

/**
 * Triggers builds of other projects.
 *
 * This class was inspired by {@link BuildTrigger} (rev. 21890) -
 * but has changed significantly in the mean time.
 */
@SuppressWarnings("unchecked")
public class DownstreamTrigger extends Notifier implements DependecyDeclarer {

    private static final Logger LOGGER = Logger.getLogger(DownstreamTrigger.class.getName());
    
    /**
     * Comma-separated list of other projects to be scheduled.
     */
    private String childProjects;

    /**
     * Threshold status to trigger other builds.
     */
    private Result threshold = Result.SUCCESS;
    
    private Strategy thresholdStrategy;
    
    
    private final boolean onlyIfSCMChanges;

    @DataBoundConstructor
    public DownstreamTrigger(String childProjects, String threshold, boolean onlyIfSCMChanges,
            Strategy strategy) {
        this(childProjects, resultFromString(threshold), onlyIfSCMChanges, strategy);
    }

    public DownstreamTrigger(String childProjects, Result threshold, boolean onlyIfSCMChanges,
            Strategy strategy) {
        if(childProjects==null)
            throw new IllegalArgumentException();
        this.childProjects = childProjects;
        this.threshold = threshold;
        this.onlyIfSCMChanges = onlyIfSCMChanges;
        this.thresholdStrategy = strategy;
    }
    
    private static Result resultFromString(String s) {
    	Result result = Result.fromString(s);
    	// fromString returns FAILURE for unknown strings instead of
    	// IllegalArgumentException. Don't know why the author thought that this
    	// is useful ...
    	if (!result.toString().equals(s)) {
    		throw new IllegalArgumentException("Unknown result type '" + s + "'");
    	}
    	return result;
    }

    public String getChildProjectsValue() {
        return childProjects;
    }

    public Result getThreshold() {
        if(threshold==null)
            return Result.SUCCESS;
        else
            return threshold;
    }
    
    public boolean isOnlyIfSCMChanges() {
    	return onlyIfSCMChanges;
    }

    public List<AbstractProject> getChildProjects() {
        return Items.fromNameList(childProjects,AbstractProject.class);
    }
    
    public Strategy getStrategy() {
        return this.thresholdStrategy;
    }

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }
    
    @Override
	public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener) {
//        if(build.getResult().isBetterOrEqualTo(getThreshold())) {
//            PrintStream logger = listener.getLogger();
//            //Trigger downstream projects of the project defined by this trigger
//            List <AbstractProject> downstreamProjects = getChildProjects();
//                
//            for (AbstractProject p : downstreamProjects) {
//                if(p.isDisabled()) {
//                    logger.println(Messages.BuildTrigger_Disabled(p.getName()));
//                    continue;
//                }
//                
//                if(isOnlyIfSCMChanges() && !p.pollSCMChanges(listener)) {
//                	logger.println(hudson.plugins.downstream_ext.Messages.DownstreamTrigger_NoSCMChanges(p.getName()));
//                	continue;
//                }
//                // this is not completely accurate, as a new build might be triggered
//                // between these calls
//                String name = p.getName()+" #"+p.getNextBuildNumber();
//                if(p.scheduleBuild(new UpstreamCause((Run)build))) {
//                    logger.println(Messages.BuildTrigger_Triggering(name));
//                } else {
//                    logger.println(Messages.BuildTrigger_InQueue(name));
//                }
//            }
//        }

    	// nothing to do here. Everything happens in buildDependencyGraph
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void buildDependencyGraph(AbstractProject owner, DependencyGraph graph) {
    	for (AbstractProject downstream : getChildProjects()) {
    		graph.addDependency(new DownstreamDependency(owner, downstream, this));
    	}
    }

    @Override
    public boolean needsToRunAfterFinalized() {
        return true;
    }

    /**
     * Called from {@link Job#renameTo(String)} when a job is renamed.
     *
     * @return true
     *      if this {@link DownstreamTrigger} is changed and needs to be saved.
     */
    public boolean onJobRenamed(String oldName, String newName) {
        // quick test
        if(!childProjects.contains(oldName))
            return false;

        boolean changed = false;

        // we need to do this per string, since old Project object is already gone.
        String[] projects = childProjects.split(",");
        for( int i=0; i<projects.length; i++ ) {
            if(projects[i].trim().equals(oldName)) {
                projects[i] = newName;
                changed = true;
            }
        }

        if(changed) {
            StringBuilder b = new StringBuilder();
            for (String p : projects) {
                if(b.length()>0)    b.append(',');
                b.append(p);
            }
            childProjects = b.toString();
        }

        return changed;
    }

    private Object readResolve() {
        if (thresholdStrategy == null) {
            thresholdStrategy = Strategy.AND_HIGHER;
        }
        return this;
    }

    @Extension
    public static class DescriptorImpl extends BuildTrigger.DescriptorImpl {
    	
    	public static final String[] THRESHOLD_VALUES = {
    		Result.SUCCESS.toString(), Result.UNSTABLE.toString(), Result.FAILURE.toString()
    	};
    	
    	public static final Strategy[] STRATEGY_VALUES = Strategy.values();
//    	static {
//    	    List<String> tmp = new ArrayList<String>();
//    	    for (Strategy s : Strategy.values()) {
//    	        tmp.add(s.name());
//    	    }
//    	    STRATEGY_VALUES = tmp.toArray(new String[tmp.size()]);
//    	}
    	
        @Override
		public String getDisplayName() {
            return hudson.plugins.downstream_ext.Messages.DownstreamTrigger_DisplayName();
        }

        @Override
        public String getHelpFile() {
            return "/help/project-config/downstream.html";
        }

        @Override
        public Publisher newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            return new DownstreamTrigger(
                formData.getString("childProjects"),
                formData.getString("threshold"),
                formData.has("onlyIfSCMChanges") && formData.getBoolean("onlyIfSCMChanges"),
                (Strategy)formData.get("strategy"));
        }

        @Extension
        public static class ItemListenerImpl extends ItemListener {
            @Override
            public void onRenamed(Item item, String oldName, String newName) {
                // update DownstreamPublisher of other projects that point to this object.
                // can't we generalize this?
                for( Project<?,?> p : Hudson.getInstance().getProjects() ) {
                    DownstreamTrigger t = p.getPublishersList().get(DownstreamTrigger.class);
                    if(t!=null) {
                        if(t.onJobRenamed(oldName,newName)) {
                            try {
                                p.save();
                            } catch (IOException e) {
                                LOGGER.log(Level.WARNING, "Failed to persist project setting during rename from "+oldName+" to "+newName,e);
                            }
                        }
                    }
                }
            }
        }
    }

    enum Strategy {
        AND_HIGHER("equals or higher"),
        EXACT("equals"),
        AND_LOWER("equals or lower");
        
        private final String displayName;

        Strategy(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return this.displayName;
        }
    }
}
