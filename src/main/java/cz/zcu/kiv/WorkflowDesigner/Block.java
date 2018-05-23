package cz.zcu.kiv.WorkflowDesigner;

import cz.zcu.kiv.WorkflowDesigner.Annotations.BlockExecute;
import cz.zcu.kiv.WorkflowDesigner.Annotations.BlockInput;
import cz.zcu.kiv.WorkflowDesigner.Annotations.BlockOutput;
import cz.zcu.kiv.WorkflowDesigner.Annotations.BlockProperty;
import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

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
 * Block, 2018/16/05 13:32 Joey Pinto
 *
 * This file is the model for a single block in the workflow designer tool
 **********************************************************************************************************************/


public class Block {
    private Workflow workflow;
    private String name;
    private String family;
    private Map<String, Data> input;
    private Map<String, Data> output;
    private Map<String,Property> properties;
    private Object context;

    public Object getContext() {
        return context;
    }

    public void setContext(Object context) {
        this.context = context;
    }

    //Temporary variables
    private boolean processed=false;

    public void fromJSON(JSONObject blockObject) throws NoSuchMethodException, InstantiationException, IllegalAccessException, InvocationTargetException {
        this.name = blockObject.getString("type");

        Block block = workflow.getDefinition(this.name);

        if(block==null)return;
        this.family = block.getFamily();
        this.properties = block.getProperties();
        this.input = block.getInput();
        this.output = block.getOutput();

        JSONObject values = blockObject.getJSONObject("values");
        for(String key:this.properties.keySet()){
            if(values.has(key)){
                for (Field f: context.getClass().getDeclaredFields()) {
                    f.setAccessible(true);
                    BlockProperty blockProperty = f.getAnnotation(BlockProperty.class);
                    if (blockProperty != null) {
                            if(blockProperty.name().equals(key)){
                                properties.put(blockProperty.name(), new Property(blockProperty.name(), blockProperty.type(), blockProperty.defaultValue()));
                                if(f.getType().equals(int.class))
                                    f.set(context,(int) Double.parseDouble(values.getString(key)));
                                else if(f.getType().equals(double.class))
                                f.set(context,Double.parseDouble(values.getString(key)));

                                else f.set(context, f.getType().cast(values.getString(key)));
                                break;
                            }
                    }
                }
            }
        }


    }

    public Block(Object context, Workflow workflow){
        this.context = context;
        this.workflow = workflow;
    }


    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getFamily() {
        return family;
    }

    public void setFamily(String family) {
        this.family = family;
    }

    public String toJS() {
        return "blocks.register("+ this.toJSON().toString(4)+");";
    }

    public JSONObject toJSON(){
        JSONObject blockjs=new JSONObject();
        blockjs.put("name",getName());
        blockjs.put("family", getFamily());
        JSONArray fields=new JSONArray();
        for(String key:properties.keySet()){
            Property property=properties.get(key);
            JSONObject field=new JSONObject();
            field.put("name",property.getName());
            field.put("type",property.getType());
            field.put("defaultValue",property.getDefaultValue());
            field.put("attrs","editable");
            fields.put(field);
        }

        if(input!=null && input.size()!=0) {
            for(String inputParam:input.keySet()) {
                Data inputValue=input.get(inputParam);
                JSONObject inputObj = new JSONObject();
                inputObj.put("name", inputValue.getName());
                inputObj.put("type", inputValue.getType());
                inputObj.put("attrs", "input");
                inputObj.put("card", inputValue.getCardinality());
                fields.put(inputObj);
            }
        }

        if(output!=null && output.size()!=0) {
            for(String outputParam:output.keySet()){
                Data outputValue=output.get(outputParam);
                JSONObject outpuObj = new JSONObject();
                outpuObj.put("name", outputValue.getName());
                outpuObj.put("type", outputValue.getType());
                outpuObj.put("attrs", "output");
                outpuObj.put("card", outputValue.getCardinality());
                fields.put(outpuObj);
            }
        }
        blockjs.put("fields", fields);

        return blockjs;
    }

    public void processBlock(Map<Integer, Block> blocks, Map<String, Integer> sourceBlocks, Map<String, String> sourceParams) throws IllegalAccessException, FieldMismatchException {
        if(getInput()!=null&&getInput().size()>0) {
            for (String key : getInput().keySet()) {
                Data destinationData=getInput().get(key);
                Block sourceBlock = blocks.get(sourceBlocks.get(key));

                if(sourceBlock==null) throw new FieldMismatchException(key,"source");

                Map<String, Data> source = sourceBlock.getOutput();
                Data sourceData=null;

                if(sourceParams.containsKey(key)){
                        sourceData=source.get(sourceParams.get(key));
                }

                Object value = null;

                if(sourceData==null) {
                    throw new FieldMismatchException(key,"source");
                }

                for (Field f: sourceBlock.getContext().getClass().getDeclaredFields()) {
                    f.setAccessible(true);

                    BlockOutput blockOutput = f.getAnnotation(BlockOutput.class);
                    if (blockOutput != null){
                            if(blockOutput.name().equals(sourceData.getName())) {
                                value = f.get(sourceBlock.getContext());
                                break;
                            }
                    }
                }

                for (Field f: context.getClass().getDeclaredFields()) {
                    f.setAccessible(true);

                    BlockInput blockInput = f.getAnnotation(BlockInput.class);
                    if (blockInput != null) {
                            if(blockInput.name().equals(destinationData.getName())){
                                f.set(context,value);
                                break;
                            }
                    }
                }



            }
        }
        process();
        setProcessed(true);
    }

    public void process(){
        for(Method method:context.getClass().getDeclaredMethods()){
            method.setAccessible(true);
            if(method.getAnnotation(BlockExecute.class)!=null){
                try {
                    method.invoke(context);
                } catch (IllegalAccessException | InvocationTargetException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public Map<String, Property> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, Property> properties) {
        this.properties = properties;
    }

    public Map<String, Data> getInput() {
        return input;
    }

    public void setInput(Map<String, Data> input) {
        this.input = input;
    }

    public Map<String, Data> getOutput() {
        return output;
    }

    public void setOutput(Map<String, Data> output) {
        this.output = output;
    }

    public boolean isProcessed() {
        return processed;
    }

    public void setProcessed(boolean processed) {
        this.processed = processed;
    }

    public void initialize(){
        if(getProperties()==null)
        setProperties(new HashMap<String,Property>());
        if(getInput()==null)
        setInput(new HashMap<String, Data>());
        if(getOutput()==null)
            setOutput(new HashMap<String, Data>());

        for (Field f: context.getClass().getDeclaredFields()) {
            f.setAccessible(true);

            BlockProperty blockProperty = f.getAnnotation(BlockProperty.class);
            if (blockProperty != null){
                properties.put(blockProperty.name(),new Property(blockProperty.name(),blockProperty.type(),blockProperty.defaultValue()));
            }

            BlockInput blockInput = f.getAnnotation(BlockInput.class);
            if (blockInput != null){
                input.put(blockInput.name(),new Data(blockInput.name(),blockInput.type(),blockInput.cardinality()));
            }

            BlockOutput blockOutput = f.getAnnotation(BlockOutput.class);
            if (blockOutput != null){
                output.put(blockOutput.name(),new Data(blockOutput.name(),blockOutput.type(),blockOutput.cardinality()));
            }
        }

    }
}
