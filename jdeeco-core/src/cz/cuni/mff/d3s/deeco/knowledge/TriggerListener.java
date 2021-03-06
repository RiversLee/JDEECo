package cz.cuni.mff.d3s.deeco.knowledge;

import cz.cuni.mff.d3s.deeco.model.runtime.api.Trigger;

/**
 * Gets called by a knowledge manager when an event in the knowledge (typically change in the knowledge) matches the registered trigger.
 * 
 * @author Ilias Gerostathopoulos <iliasg@d3s.mff.cuni.cz>
 * 
 */
public interface TriggerListener {
	public void triggered(Trigger trigger);
}
