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
 * Trace, 2018/13/06 12:40 Joey Pinto
 *
 * This class hosts the data structure for a single trace of points/lines in a graph
 **********************************************************************************************************************/

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.Serializable;
import java.util.List;


public class Trace implements Serializable {
    List<Point> points;
    TraceMode traceMode;
    GraphType graphType;
    String name;
    Marker marker;


    public JSONObject toJSON() {
        JSONObject jsonObject = new JSONObject();
        JSONArray x = new JSONArray();
        JSONArray y = new JSONArray();
        JSONArray z = new JSONArray();
        JSONArray labels = new JSONArray();

        for(Point point:getPoints()){
            Coordinate coordinate = point.getCoordinate();
            if(coordinate.getX()!=null)
                x.put(coordinate.getX());

            if(coordinate.getY()!=null)
                y.put(coordinate.getY());

            if(coordinate.getZ()!=null)
                z.put(coordinate.getZ());

            if(point.getLabel()!=null)
                labels.put(point.getLabel());
        }

        if(!x.toList().isEmpty())
            jsonObject.put("x",x);

        if(!y.toList().isEmpty())
            jsonObject.put("y",y);

        if(!z.toList().isEmpty())
            jsonObject.put("z",z);

        if(!labels.toList().isEmpty()) jsonObject.put("text", labels);

        if(getMarker()!=null)
            jsonObject.put("marker",getMarker().toJSON());

        if(getGraphType()!=null)
            jsonObject.put("type",getGraphType().getType());

        if(getTraceMode()!=null)
            jsonObject.put("mode",getTraceMode().getMode());

        if(getName()!=null)
            jsonObject.put("name",getName());

        return jsonObject;
    }

    public List<Point> getPoints() {
        return points;
    }

    public void setPoints(List<Point> points) {
        this.points = points;
    }

    public TraceMode getTraceMode() {
        return traceMode;
    }

    public void setTraceMode(TraceMode traceMode) {
        this.traceMode = traceMode;
    }

    public GraphType getGraphType() {
        return graphType;
    }

    public void setGraphType(GraphType graphType) {
        this.graphType = graphType;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Marker getMarker() {
        return marker;
    }

    public void setMarker(Marker marker) {
        this.marker = marker;
    }
}
