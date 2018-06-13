package cz.zcu.kiv.WorkflowDesigner.Visualizations.PlotlyGraphs;
/***********************************************************************************************************************
 *
 * This file is part of the Workflow Designer project

 * ==========================================
 *
 * Copyright (C) 2018 by University of West Bohemia (http://www.zcu.cz/en/)
 *
 ***********************************************************************************************************************
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 ***********************************************************************************************************************
 *
 * ScatterPlot2D, 2018/13/06 12:40 Joey Pinto
 *
 * This class hosts the data structure for a 2-dimensional scatter plot
 **********************************************************************************************************************/

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

public class ScatterPlot2D {
    String name;
    List<Trace>traces;

    public JSONObject toJSON(){
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("name",getName());
        JSONArray traces = getTracesJSONArray();
        jsonObject.put("traces",traces);
        return jsonObject;
    }

    private JSONArray getTracesJSONArray() {
        JSONArray traces=new JSONArray();
        for(Trace trace:this.traces){
            traces.put(trace.toJSON());
        }
        return traces;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<Trace> getTraces() {
        return traces;
    }

    public void setTraces(List<Trace> traces) {
        this.traces = traces;
    }
}
