package checkers.eclipse.actions;

import java.util.List;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jface.viewers.ISelection;

import checkers.eclipse.CheckerPlugin;
import checkers.eclipse.prefs.CheckerPreferences;
import checkers.eclipse.util.JavaUtils;
import checkers.eclipse.util.MutexSchedulingRule;

/**
 * Superclass of all checker actions.
 */
public abstract class RunCheckerAction extends CheckerHandler
{
    private final String checkerName;
    protected boolean usePrefs;
    protected boolean useCustom;
    protected boolean useSingleCustom;

    /** true if this action is used from editor */
    protected boolean usedInEditor;

    protected RunCheckerAction() {
        super();
        this.checkerName = null;
        this.usePrefs = true;
        this.useCustom = false;
        this.useSingleCustom = false;
    }

    protected RunCheckerAction(String checkerName) {
        super();
        this.checkerName = checkerName;
        this.useCustom = false;
        this.usePrefs = false;
        this.useSingleCustom = false;
    }

    /**
     * If constructed with a no-arg constructor, then we get the list of classes
     * to use from the preferences system
     */
    private List<String> getClassNameFromPrefs() {
        return CheckerManager.getSelectedClasses();
    }

    /**
     * 
     */
    public Object execute(ExecutionEvent event)
    {
        ISelection selection = getSelection(event);
        IJavaElement element = element(selection);

        if (element != null)
        {
            Job checkerJob;
            String customClasses = CheckerPlugin.getDefault()
                    .getPreferenceStore()
                    .getString(CheckerPreferences.PREF_CHECKER_CUSTOM_CLASSES);

            // Depending on how this runner was created, we will either:
            // * just run one particular checker
            // * use the custom configured checkers
            // * run "selected" checkers using the action or auto build
            
            final String actualNames;
            
            if (!usePrefs && !useCustom && !useSingleCustom) {
            	actualNames = checkerName;
            }
            else if (!usePrefs && !useSingleCustom) {
            	actualNames = customClasses;
            }
            else if(useSingleCustom) {
            	actualNames = event.getParameter("checker-framework-eclipse-plugin.checker");
            } else {
                List<String> names = getClassNameFromPrefs();
                actualNames = JavaUtils.join(",", names);
            }

            checkerJob = new CheckerWorker(element, actualNames);

            checkerJob.setUser(true);
            checkerJob.setPriority(Job.BUILD);
            checkerJob.setRule(new MutexSchedulingRule());
            checkerJob.schedule();
        }

        return null;
    }
}