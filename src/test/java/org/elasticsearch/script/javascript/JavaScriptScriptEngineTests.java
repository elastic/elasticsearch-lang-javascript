/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.script.javascript;

import org.elasticsearch.common.base.Charsets;
import org.elasticsearch.common.collect.Lists;
import org.elasticsearch.common.collect.MapBuilder;
import org.elasticsearch.common.io.Streams;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.env.Environment;
import org.elasticsearch.script.ExecutableScript;
import org.elasticsearch.test.ElasticsearchTestCase;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;

/**
 *
 */
public class JavaScriptScriptEngineTests extends ElasticsearchTestCase {

    private JavaScriptScriptEngineService se;

    @Before
    public void setup() {
        String confdir = JavaScriptScriptEngineTests.class.getResource("/conf").getPath();
        se = new JavaScriptScriptEngineService(
                ImmutableSettings.Builder.EMPTY_SETTINGS, new Environment(
                ImmutableSettings.settingsBuilder()
                        .put("path.conf", confdir).build()));
    }

    @After
    public void close() {
        se.close();
    }

    @Test
    public void testSimpleEquation() {
        Map<String, Object> vars = new HashMap<String, Object>();
        Object o = se.execute(se.compile("1 + 2"), vars);
        assertThat(((Number) o).intValue(), equalTo(3));
    }

    @Test
    public void testMapAccess() {
        Map<String, Object> vars = new HashMap<String, Object>();

        Map<String, Object> obj2 = MapBuilder.<String, Object>newMapBuilder().put("prop2", "value2").map();
        Map<String, Object> obj1 = MapBuilder.<String, Object>newMapBuilder().put("prop1", "value1").put("obj2", obj2).put("l", Lists.newArrayList("2", "1")).map();
        vars.put("obj1", obj1);
        Object o = se.execute(se.compile("obj1"), vars);
        assertThat(o, instanceOf(Map.class));
        obj1 = (Map<String, Object>) o;
        assertThat((String) obj1.get("prop1"), equalTo("value1"));
        assertThat((String) ((Map<String, Object>) obj1.get("obj2")).get("prop2"), equalTo("value2"));

        o = se.execute(se.compile("obj1.l[0]"), vars);
        assertThat(((String) o), equalTo("2"));
    }

    @Test
    public void testJavaScriptObjectToMap() {
        Map<String, Object> vars = new HashMap<String, Object>();
        Object o = se.execute(se.compile("var obj1 = {}; obj1.prop1 = 'value1'; obj1.obj2 = {}; obj1.obj2.prop2 = 'value2'; obj1"), vars);
        Map obj1 = (Map) o;
        assertThat((String) obj1.get("prop1"), equalTo("value1"));
        assertThat((String) ((Map<String, Object>) obj1.get("obj2")).get("prop2"), equalTo("value2"));
    }

    @Test
    public void testJavaScriptObjectMapInter() {
        Map<String, Object> vars = new HashMap<String, Object>();
        Map<String, Object> ctx = new HashMap<String, Object>();
        Map<String, Object> obj1 = new HashMap<String, Object>();
        obj1.put("prop1", "value1");
        ctx.put("obj1", obj1);
        vars.put("ctx", ctx);

        se.execute(se.compile("ctx.obj2 = {}; ctx.obj2.prop2 = 'value2'; ctx.obj1.prop1 = 'uvalue1'"), vars);
        ctx = (Map<String, Object>) se.unwrap(vars.get("ctx"));
        assertThat(ctx.containsKey("obj1"), equalTo(true));
        assertThat((String) ((Map<String, Object>) ctx.get("obj1")).get("prop1"), equalTo("uvalue1"));
        assertThat(ctx.containsKey("obj2"), equalTo(true));
        assertThat((String) ((Map<String, Object>) ctx.get("obj2")).get("prop2"), equalTo("value2"));
    }

    @Test
    public void testJavaScriptInnerArrayCreation() {
        Map<String, Object> ctx = new HashMap<String, Object>();
        Map<String, Object> doc = new HashMap<String, Object>();
        ctx.put("doc", doc);

        Object complied = se.compile("ctx.doc.field1 = ['value1', 'value2']");
        ExecutableScript script = se.executable(complied, new HashMap<String, Object>());
        script.setNextVar("ctx", ctx);
        script.run();

        Map<String, Object> unwrap = (Map<String, Object>) script.unwrap(ctx);

        assertThat(((Map) unwrap.get("doc")).get("field1"), instanceOf(List.class));
    }

    @Test
    public void testAccessListInScript() {
        Map<String, Object> vars = new HashMap<String, Object>();
        Map<String, Object> obj2 = MapBuilder.<String, Object>newMapBuilder().put("prop2", "value2").map();
        Map<String, Object> obj1 = MapBuilder.<String, Object>newMapBuilder().put("prop1", "value1").put("obj2", obj2).map();
        vars.put("l", Lists.newArrayList("1", "2", "3", obj1));

        Object o = se.execute(se.compile("l.length"), vars);
        assertThat(((Number) o).intValue(), equalTo(4));

        o = se.execute(se.compile("l[0]"), vars);
        assertThat(((String) o), equalTo("1"));

        o = se.execute(se.compile("l[3]"), vars);
        obj1 = (Map<String, Object>) o;
        assertThat((String) obj1.get("prop1"), equalTo("value1"));
        assertThat((String) ((Map<String, Object>) obj1.get("obj2")).get("prop2"), equalTo("value2"));

        o = se.execute(se.compile("l[3].prop1"), vars);
        assertThat(((String) o), equalTo("value1"));
    }

    @Test
    public void testChangingVarsCrossExecution1() {
        Map<String, Object> vars = new HashMap<String, Object>();
        Map<String, Object> ctx = new HashMap<String, Object>();
        vars.put("ctx", ctx);
        Object compiledScript = se.compile("ctx.value");

        ExecutableScript script = se.executable(compiledScript, vars);
        ctx.put("value", 1);
        Object o = script.run();
        assertThat(((Number) o).intValue(), equalTo(1));

        ctx.put("value", 2);
        o = script.run();
        assertThat(((Number) o).intValue(), equalTo(2));
    }

    @Test
    public void testChangingVarsCrossExecution2() {
        Map<String, Object> vars = new HashMap<String, Object>();
        Object compiledScript = se.compile("value");

        ExecutableScript script = se.executable(compiledScript, vars);
        script.setNextVar("value", 1);
        Object o = script.run();
        assertThat(((Number) o).intValue(), equalTo(1));

        script.setNextVar("value", 2);
        o = script.run();
        assertThat(((Number) o).intValue(), equalTo(2));
    }

    @Test
    public void testCommonJSmodule() throws IOException {
        Map<String, Object> vars = new HashMap<String, Object>();
        Map<String, Object> ctx = new HashMap<String, Object>();

        vars.put("ctx", ctx);

        String file = Streams.copyToString(new InputStreamReader(new FileInputStream(JavaScriptScriptEngineTests.class.getResource("/conf/scripts/test1.js").getFile()), Charsets.UTF_8));
        Object compiledScript = se.compile(file);
        ExecutableScript script = se.executable(compiledScript, vars);
        Object o = script.run();
        ctx = (Map<String, Object>) se.unwrap(vars.get("ctx"));
        assertThat(ctx.containsKey("pi"), equalTo(true));
        assertThat(((Double) ctx.get("pi")).doubleValue(), equalTo(3.14));
    }
}
