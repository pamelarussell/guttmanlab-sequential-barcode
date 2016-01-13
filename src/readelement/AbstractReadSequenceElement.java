package readelement;

import matcher.MatchedElement;

import org.apache.commons.lang.builder.HashCodeBuilder;

/**
 * Abstract class to provide some default implementations
 * @author prussell
 *
 */
public abstract class AbstractReadSequenceElement implements ReadSequenceElement {

	@Override
	public MatchedElement matchedElement(String s, int startPosOnString) {
		return matchedElement(s.substring(startPosOnString));
	}
	
	public boolean equals(Object o) {
		if(!o.getClass().equals(getClass())) return false;
		AbstractReadSequenceElement a = (AbstractReadSequenceElement)o;
		if(!a.elementName().equals(elementName())) return false;
		if(!a.getId().equals(getId())) return false;
		if(!a.getSequence().equals(getSequence())) return false;
		if(!a.sequenceToElement().equals(sequenceToElement())) return false;
		return true;
	}
	
	@Override
	public int hashCode() {
		HashCodeBuilder b = new HashCodeBuilder();
		b.append(elementName());
		b.append(getId());
		b.append(getSequence());
		b.append(sequenceToElement().hashCode());
		return b.toHashCode();
	}

}
