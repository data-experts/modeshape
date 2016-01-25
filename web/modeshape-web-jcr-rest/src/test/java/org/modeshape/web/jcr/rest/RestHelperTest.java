package org.modeshape.web.jcr.rest;

import java.time.LocalDate;
import java.time.ZoneId;

import javax.jcr.PropertyType;
import javax.jcr.Value;

import org.junit.Test;
import org.modeshape.test.ModeShapeSingleUseTest;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.*;

/**
 * Created by abieberbach on 25.01.2016.
 */
public class RestHelperTest extends ModeShapeSingleUseTest {

    @Test
    public void testJsonValueToJCRValue_UUIDWithTimestamp() throws Exception {
        Value value = RestHelper.jsonValueToJCRValue("32899126-7281-46a6-b298-4558fbc82d5f", session().getValueFactory());
        assertThat(value.getString(),is("32899126-7281-46a6-b298-4558fbc82d5f"));
        assertThat(value.getType(),is(PropertyType.STRING));
    }

    @Test
    public void testJsonValueToJCRValue_Date() throws Exception {
        Value value = RestHelper.jsonValueToJCRValue("2016-01-25", session().getValueFactory());
        assertThat(value.getDate().toInstant(),is(LocalDate.of(2016,1,25).atStartOfDay(ZoneId.systemDefault()).toInstant()));
        assertThat(value.getType(),is(PropertyType.DATE));
    }


}