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

import org.elasticsearch.common.StopWatch;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.script.ExecutableScript;
import org.elasticsearch.script.ScriptEngineService;

import java.util.HashMap;
import java.util.Map;

/**
 *
 */
public class SimpleBench {

    static final long ITER = 100000;
    public static void main(String[] args) {
    	ScriptEngineService se = new JavascriptRhinoScriptEngineService(ImmutableSettings.Builder.EMPTY_SETTINGS);
    	System.out.println("Bench Rhino... " + ITER + " times.");
        bench(se);

        boolean nashornAvailable = false;
        try {
        	SimpleBench.class.getClassLoader().loadClass("jdk.nashorn.internal.runtime.ScriptObject");
        	nashornAvailable = true;
        } catch(Throwable t) {
        }
        if (nashornAvailable) {
        	ScriptEngineService se2 = new JavascriptNashornScriptEngineService(ImmutableSettings.Builder.EMPTY_SETTINGS);
        	System.out.println("Bench Nashorn... " + ITER + " times.");
        	bench(se2);
        } else {
        	System.out.println("Nashorn not available here.");
        }
    }

    private static void bench(ScriptEngineService se) {
        Object compiled = se.compile("x + y");

        Map<String,Object> mockSearchLookup = new HashMap<String,Object>();
        Map<String,Object> simpleSourceDoc = new HashMap<String,Object>();
        simpleSourceDoc.put("foo", "bar");
        simpleSourceDoc.put("name", "bob");
        simpleSourceDoc.put("props", new HashMap<String,Object>());
        mockSearchLookup.put("_source", simpleSourceDoc);

        Map<String, Object> vars = new HashMap<String, Object>();
        vars.putAll(mockSearchLookup);
        // warm up
        for (int i = 0; i < 1000; i++) {
            vars.put("x", i);
            vars.put("y", i + 1);
            se.execute(compiled, vars);
        }

        StopWatch stopWatch = new StopWatch().start();
        for (long i = 0; i < ITER; i++) {
            se.execute(compiled, vars);
        }
        System.out.println("Execute Took: " + stopWatch.stop().lastTaskTime());

        stopWatch = new StopWatch().start();
        ExecutableScript executableScript = se.executable(compiled, vars);
        for (long i = 0; i < ITER; i++) {
            executableScript.run();
        }
        System.out.println("Executable Took: " + stopWatch.stop().lastTaskTime());

        stopWatch = new StopWatch().start();
        executableScript = se.executable(compiled, vars);
        for (long i = 0; i < ITER; i++) {
            for (Map.Entry<String, Object> entry : vars.entrySet()) {
                executableScript.setNextVar(entry.getKey(), entry.getValue());
            }
            executableScript.run();
        }
        System.out.println("Executable (vars) Took: " + stopWatch.stop().lastTaskTime());
    }
}
