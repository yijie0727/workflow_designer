package cz.zcu.kiv.WorkflowDesigner;

import java.io.Serializable;
import java.util.HashMap;
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
 * BlockData, 2018/06/03 8:00 Joey Pinto
 *
 * This file hosts the BlockData data structure used to serialize data to a file when running a block as a Jar
 **********************************************************************************************************************/

public class BlockData implements Serializable {
    HashMap<String, Object> input;
    HashMap<String, Object> output;
    HashMap<String, Object> properties;
    Object processOutput;
    String name;

    public BlockData(String name){
        this.name=name;
        input=new HashMap<>();
        output=new HashMap<>();
        properties=new HashMap<>();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public HashMap<String, Object> getInput() {
        return input;
    }

    public void setInput(HashMap<String, Object> input) {
        this.input = input;
    }

    public HashMap<String, Object> getOutput() {
        return output;
    }

    public void setOutput(HashMap<String, Object> output) {
        this.output = output;
    }

    public HashMap<String, Object> getProperties() {
        return properties;
    }

    public void setProperties(HashMap<String, Object> properties) {
        this.properties = properties;
    }

    public Object getProcessOutput() {
        return processOutput;
    }

    public void setProcessOutput(Object processOutput) {
        this.processOutput = processOutput;
    }
}
