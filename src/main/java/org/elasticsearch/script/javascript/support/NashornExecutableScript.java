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

package org.elasticsearch.script.javascript.support;

import javax.script.Bindings;
import javax.script.CompiledScript;
import javax.script.ScriptContext;
import javax.script.ScriptException;

import org.elasticsearch.script.ExecutableScript;

public class NashornExecutableScript implements ExecutableScript {

    private final CompiledScript script;
    final ScriptContext context;
    final Bindings bindings;

    public NashornExecutableScript(CompiledScript script, ScriptContext context, Bindings bindings) {
        this.script = script;
        this.context = context;
        this.bindings = bindings;//context.getBindings(ScriptContext.GLOBAL_SCOPE);
    }

    @Override
    public Object run() {
        try {
            return unwrap(script.eval(context));
        } catch (ScriptException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public void setNextVar(String name, Object value) {
        this.bindings.put(name, value);
    }

    @Override
    public Object unwrap(Object value) {
        return NashornScriptValueConverter.unwrapValue(value);
    }
}