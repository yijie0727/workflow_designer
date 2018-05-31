package cz.zcu.kiv.WorkflowDesigner;

import java.io.Serializable;
import java.util.HashMap;

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
