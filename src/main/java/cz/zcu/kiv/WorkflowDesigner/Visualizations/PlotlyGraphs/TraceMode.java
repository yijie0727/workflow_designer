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
 * TraceMode, 2018/13/06 12:40 Joey Pinto
 *
 * This class hosts the constants for the trace mode
 **********************************************************************************************************************/

public class TraceMode {
    String mode;

    public TraceMode(String mode) {
        this.mode = mode;
    }

    public static final TraceMode MARKERS_AND_TEXT = new TraceMode("markers+text");
    public static final TraceMode MARKERS_ONLY = new TraceMode("markers");
    public static final TraceMode MARKER_AND_LINE=new TraceMode("lines+markers");

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }
}
