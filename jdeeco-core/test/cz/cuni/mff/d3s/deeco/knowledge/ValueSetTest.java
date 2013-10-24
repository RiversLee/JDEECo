package cz.cuni.mff.d3s.deeco.knowledge;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.Collection;
import java.util.LinkedList;


import org.junit.Before;
import org.junit.Test;

import cz.cuni.mff.d3s.deeco.knowledge.KnowledgeSet.KnowledgeValue;

/**
 * Example of white-box testing with mock objects.
 * 
 * @author Keznikl
 *
 */
public class ValueSetTest {

	ValueSet vs;
	KnowledgeSet ks;
	
	@Before
	public void setUp() throws Exception {
		ks = mock(KnowledgeSet.class);
		vs = new ValueSet(ks);
	}


	@Test
	public void testGetNotFoundReferences() {
		assertNotNull(vs.getNotFoundReferences());
		assertEquals(0, vs.getNotFoundReferences().size());
		
		Collection<KnowledgeReference> emptyValues = new LinkedList<>();
		when(ks.getEmptyReferences()).thenReturn(emptyValues);
		
		assertEquals(emptyValues, vs.getNotFoundReferences());
		verify(ks, times(3)).getEmptyReferences();
		verifyNoMoreInteractions(ks);
	}	
	
	@Test
	public void testGetFoundReferences() {
		assertNotNull(vs.getFoundReferences());
		assertEquals(0, vs.getFoundReferences().size());
		
		Collection<KnowledgeReference> foundValues = new LinkedList<>();
		when(ks.getNonEmptyReferences()).thenReturn(foundValues);
		
		assertEquals(foundValues, vs.getFoundReferences());
		verify(ks, times(3)).getNonEmptyReferences();
		verifyNoMoreInteractions(ks);
	}

	@Test
	public void testGetValue() {
		KnowledgeReference nullRef = mock(KnowledgeReference.class);
		KnowledgeReference notFoundRef = mock(KnowledgeReference.class);
		KnowledgeReference normalRef = mock(KnowledgeReference.class);
		KnowledgeReference nonexistingRef = mock(KnowledgeReference.class);
		Object value = new Object();
		
		when(ks.getValue(nullRef)).thenReturn(null);
		when(ks.getValue(notFoundRef)).thenReturn(KnowledgeValue.EMPTY);
		when(ks.getValue(normalRef)).thenReturn(value);
		
		assertNull(vs.getValue(nullRef));
		assertNull(vs.getValue(notFoundRef));
		assertEquals(value, vs.getValue(normalRef));	
		assertNull(vs.getValue(nonexistingRef));
		
		verify(ks).getValue(nullRef);
		verify(ks).getValue(notFoundRef);
		verify(ks).getValue(normalRef);
		verify(ks).getValue(nonexistingRef);
		verifyNoMoreInteractions(ks);
	}
	
	@Test
	public void testSetNotFound() {
		KnowledgeReference r = mock(KnowledgeReference.class);
		vs.setNotFound(r);
		verify(ks).setValue(r, KnowledgeValue.EMPTY);
		verifyNoMoreInteractions(ks);
	}
	
	@Test
	public void testSetValue() {
		KnowledgeReference r = mock(KnowledgeReference.class);
		Object v = new Object();
		
		vs.setValue(r, v);
		verify(ks).setValue(r, v);
		verifyNoMoreInteractions(ks);
	}
	

}
