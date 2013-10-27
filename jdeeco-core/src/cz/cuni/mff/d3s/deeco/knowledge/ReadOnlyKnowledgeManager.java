package cz.cuni.mff.d3s.deeco.knowledge;
/**
 * This interface allows the user to read the values from KnowledgeSet. Also, 
 * the interface allows to bind a trigger to tirggerListener or unbind it.
 * 
 * @author Rima Al Ali <alali@d3s.mff.cuni.cz>
 *
 */
import java.util.Collection;

import cz.cuni.mff.d3s.deeco.model.runtime.api.Trigger;
import cz.cuni.mff.d3s.deeco.task.TriggerListener;


public interface ReadOnlyKnowledgeManager {

	public ValueSet get(Collection<KnowledgeReference> knowledgeReferenceList);
	public void register(Trigger trigger, TriggerListener triggerListener);
	public void unregister(Trigger trigger, TriggerListener triggerListener);
}