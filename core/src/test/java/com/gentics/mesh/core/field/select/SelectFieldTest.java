package com.gentics.mesh.core.field.select;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Ignore;
import org.junit.Test;

import com.gentics.mesh.core.data.NodeFieldContainer;
import com.gentics.mesh.core.data.impl.NodeFieldContainerImpl;
import com.gentics.mesh.core.data.node.field.basic.StringField;
import com.gentics.mesh.core.data.node.field.impl.basic.StringFieldImpl;
import com.gentics.mesh.core.data.node.field.nesting.SelectField;
import com.gentics.mesh.test.AbstractDBTest;

public class SelectFieldTest extends AbstractDBTest {

	@Test
	public void testStringSelection() {
		NodeFieldContainer container = fg.addFramedVertex(NodeFieldContainerImpl.class);

		SelectField<StringField> field = container.createSelect("dummySelect");
		field.addOption(new StringFieldImpl("test", null));
		assertEquals(1, field.getOptions());
	}

	@Test
	@Ignore("Not yet implemented")
	public void testNodeSelection() {
		fail("Not yet implemented");
	}

	@Test
	@Ignore("Not yet implemented")
	public void testNumberSelection() {
		fail("Not yet implemented");
	}

	@Test
	@Ignore("Not yet implemented")	
	public void testBooleanSelection() {
		fail("Not yet implemented");
	}
}