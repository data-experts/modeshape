/*
 * ModeShape (http://www.modeshape.org)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.modeshape.web.jcr.rest;

import java.time.LocalDate;
import java.time.ZoneId;

import javax.jcr.PropertyType;
import javax.jcr.Value;

import org.junit.Test;
import org.modeshape.test.ModeShapeSingleUseTest;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

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