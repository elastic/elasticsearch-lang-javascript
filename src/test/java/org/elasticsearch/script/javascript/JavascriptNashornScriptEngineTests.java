package org.elasticsearch.script.javascript;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.elasticsearch.common.collect.Lists;
import org.elasticsearch.common.collect.MapBuilder;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.script.ExecutableScript;
import org.elasticsearch.test.ElasticsearchTestCase;
import org.junit.Before;
import org.junit.Test;

public class JavascriptNashornScriptEngineTests extends ElasticsearchTestCase{
    private JavascriptNashornScriptEngineService nashorn;

    @Before
    public void setup() {
        nashorn = new JavascriptNashornScriptEngineService(ImmutableSettings.Builder.EMPTY_SETTINGS);
    }

    @Test
    public void testSimpleEquation() {
        Map<String, Object> vars = new HashMap<String, Object>();
        Object o = nashorn.execute(nashorn.compile("1 + 2"), vars);
        assertThat(((Number) o).intValue(), equalTo(3));
    }
    
    @Test
    public void testMapAccess() {
        Map<String, Object> vars = new HashMap<String, Object>();
        Map<String, Object> obj2 = MapBuilder.<String, Object>newMapBuilder().put("prop2", "value2").map();
        Map<String, Object> obj1 = MapBuilder.<String, Object>newMapBuilder().put("prop1", "value1").put("obj2", obj2).put("l", Lists.newArrayList("2", "1")).map();
        vars.put("obj1", obj1);
        Object o = nashorn.execute(nashorn.compile("obj1"), vars);
        assertThat(o, instanceOf(Map.class));
        obj1 = (Map<String, Object>) o;
        assertThat((String) obj1.get("prop1"), equalTo("value1"));
        assertThat((String) ((Map<String, Object>) obj1.get("obj2")).get("prop2"), equalTo("value2"));
        
        o = nashorn.execute(nashorn.compile("obj1.l[0]"), vars);
        assertThat(((String) o), equalTo("2"));
    }

    @Test
    public void testJavaScriptObjectToMap() {
        Map<String, Object> vars = new HashMap<String, Object>();
        Object o = nashorn.execute(nashorn.compile("var obj1 = {}; obj1.prop1 = 'value1'; obj1.obj2 = {}; obj1.obj2.prop2 = 'value2'; obj1"), vars);
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
        
        nashorn.execute(nashorn.compile("ctx.obj2 = {}; ctx.obj2.prop2 = 'value2'; ctx.obj1.prop1 = 'uvalue1'"), vars);

        ctx = (Map<String, Object>) nashorn.unwrap(vars.get("ctx"));
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

        Object complied = nashorn.compile("ctx.doc.field1 = ['value1', 'value2']");
        ExecutableScript script = nashorn.executable(complied, new HashMap<String, Object>());
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
        Object o = nashorn.execute(nashorn.compile("l.length"), vars);
        assertThat(((Number) o).intValue(), equalTo(4));

        o = nashorn.execute(nashorn.compile("l[0]"), vars);
        assertThat(((String) o), equalTo("1"));

        o = nashorn.execute(nashorn.compile("l[3]"), vars);
        obj1 = (Map<String, Object>) o;
        assertThat((String) obj1.get("prop1"), equalTo("value1"));
        assertThat((String) ((Map<String, Object>) obj1.get("obj2")).get("prop2"), equalTo("value2"));

        o = nashorn.execute(nashorn.compile("l[3].prop1"), vars);
        assertThat(((String) o), equalTo("value1"));
    }
    
     @Test
    public void testChangingVarsCrossExecution1() {
        Map<String, Object> vars = new HashMap<String, Object>();
        Map<String, Object> ctx = new HashMap<String, Object>();
        vars.put("ctx", ctx);
        Object compiledScript = nashorn.compile("ctx.value");

        ExecutableScript script = nashorn.executable(compiledScript, vars);
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
        Object compiledScript = nashorn.compile("value");

        ExecutableScript script = nashorn.executable(compiledScript, vars);
        script.setNextVar("value", 1);
        Object o = script.run();
        assertThat(((Number) o).intValue(), equalTo(1));

        script.setNextVar("value", 2);
        o = script.run();
        assertThat(((Number) o).intValue(), equalTo(2));
    }
}
