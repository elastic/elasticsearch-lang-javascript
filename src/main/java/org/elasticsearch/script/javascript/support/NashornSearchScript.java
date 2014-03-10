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

import java.util.Map;

import javax.script.Bindings;
import javax.script.CompiledScript;
import javax.script.ScriptContext;

import org.apache.lucene.index.AtomicReaderContext;
import org.apache.lucene.search.Scorer;
import org.elasticsearch.script.SearchScript;
import org.elasticsearch.search.lookup.SearchLookup;

public class NashornSearchScript extends NashornExecutableScript implements SearchScript {

    private final SearchLookup lookup;
    
    public NashornSearchScript(CompiledScript script, ScriptContext context, Bindings bindings, SearchLookup lookup) {
        super(script, context, bindings);
        this.lookup = lookup;
    }
    
    @Override
    public void setNextReader(AtomicReaderContext reader) {
        lookup.setNextReader(reader);
    }

    @Override
    public void setScorer(Scorer scorer) {
        lookup.setScorer(scorer);
    }

    @Override
    public void setNextDocId(int doc) {
        lookup.setNextDocId(doc);
    }

    @Override
    public void setNextSource(Map<String, Object> source) {
        lookup.source().setNextSource(source);
    }

    @Override
    public void setNextScore(float score) {
        this.bindings.put("_score", score);
    }

    @Override
    public float runAsFloat() {
        return ((Number) run()).floatValue();
    }

    @Override
    public long runAsLong() {
        return ((Number) run()).longValue();
    }

    @Override
    public double runAsDouble() {
        return ((Number) run()).doubleValue();
    }
}