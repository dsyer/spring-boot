/*
 * Copyright 2012-2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.boot.actuate.metrics;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Dave Syer
 */
public class MetricTests {

	private ObjectMapper mapper = new ObjectMapper();

	@Test
	public void jsonSerialization() {
		assertTrue(this.mapper.convertValue(new Metric<Number>("foo", 2.3), Map.class)
				.containsKey("value"));
	}

	@Test
	public void jsonDeserialization() {
		Map<String, Object> map = new LinkedHashMap<String, Object>();
		map.put("name", "foo");
		map.put("value", 2.34d);
		map.put("timestamp", 1436286483174L);
		assertEquals(2.34, this.mapper.convertValue(map, Metric.class).getValue());
	}

}
