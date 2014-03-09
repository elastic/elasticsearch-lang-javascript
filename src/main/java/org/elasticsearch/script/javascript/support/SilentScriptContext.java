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

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.SimpleScriptContext;

/**
 * Similar to {@link SimpleScriptContext} but faster to initialize.
 * <p>
 * No input and output streams.</br>
 * Assumes that there is always a global scope.
 * </p>
 */
public class SilentScriptContext implements ScriptContext {

	private static class NullWriter extends Writer {
        public void close() {
        }
        public void flush() {
        }
        public void write(char[] cbuf, int off, int len) {
        }
	}
	private static NullWriter NULL_WRITER = new NullWriter();
	
	private static class NullReader extends Reader {
		@Override
		public int read(char[] cbuf, int off, int len) throws IOException {
			return 0;
		}
		@Override
		public void close() throws IOException {
		}
	}
	private static NullReader NULL_READER = new NullReader();

	protected Writer writer = NULL_WRITER;
    protected Writer errorWriter = NULL_WRITER;
    protected Reader reader = NULL_READER;

    protected Bindings engineScope;
    protected Bindings globalScope;

    public SilentScriptContext() {
    }

    public SilentScriptContext(Bindings engineScope, Bindings globalscope) {
        this.engineScope = engineScope;
        this.globalScope = globalscope;
    }
    public Object getAttribute(String name) {
        if (engineScope.containsKey(name)) {
            return getAttribute(name, ENGINE_SCOPE);
        } else if (globalScope != null && globalScope.containsKey(name)) {
            return getAttribute(name, GLOBAL_SCOPE);
        }
        return null;
    }

    public Object getAttribute(String name, int scope) {
    	if (scope == ENGINE_SCOPE) {
            return engineScope.get(name);
    	}
    	if (scope == GLOBAL_SCOPE) {
            return globalScope.get(name);
    	}
        throw new IllegalArgumentException("Illegal scope value.");
    }

    public Object removeAttribute(String name, int scope) {
    	if (scope == ENGINE_SCOPE) {
            return engineScope.remove(name);
    	}
    	if (scope == GLOBAL_SCOPE) {
    		return globalScope.remove(name);
    	}
        throw new IllegalArgumentException("Illegal scope value.");
    }

    public void setAttribute(String name, Object value, int scope) {
    	if (scope == ENGINE_SCOPE) {
    		engineScope.put(name, value);
    	} else if (scope == GLOBAL_SCOPE) {
    		globalScope.put(name, value);
    	} else {
        	throw new IllegalArgumentException("Illegal scope value.");    		
    	}
    }
    public Writer getWriter() {
        return writer;
    }
    public Reader getReader() {
        return reader;
    }
    public void setReader(Reader reader) {
        this.reader = reader;
    }
    public void setWriter(Writer writer) {
        this.writer = writer;
    }
    public Writer getErrorWriter() {
        return errorWriter;
    }
    public void setErrorWriter(Writer writer) {
        this.errorWriter = writer;
    }
    public int getAttributesScope(String name) {
        if (engineScope.containsKey(name)) {
            return ENGINE_SCOPE;
        } else if (globalScope.containsKey(name)) {
            return GLOBAL_SCOPE;
        } else {
            return -1;
        }
    }

    public Bindings getBindings(int scope) {
        if (scope == ENGINE_SCOPE) {
            return engineScope;
        } else if (scope == GLOBAL_SCOPE) {
            return globalScope;
        } else {
            throw new IllegalArgumentException("Illegal scope value.");
        }
    }

    private static List<Integer> scopes;
    static {
        scopes = new ArrayList<Integer>(2);
        scopes.add(ENGINE_SCOPE);
        scopes.add(GLOBAL_SCOPE);
        scopes = Collections.unmodifiableList(scopes);
    }
    public List<Integer> getScopes() {
        return scopes;
    }

	@Override
	public void setBindings(Bindings bindings, int scope) {
		if (scope == ENGINE_SCOPE) {
			engineScope = bindings;
		} else if (scope == GLOBAL_SCOPE) {
			globalScope = bindings;
		} else {
            throw new IllegalArgumentException("Illegal scope value.");
        }
	}
}
