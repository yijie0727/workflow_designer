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
 * This file is the model for a single block in the worklow designer tool
 **********************************************************************************************************************/


public class Block {
    private Workflow workflow;
    private String name;
    private String family;
    private HashMap<String, Data> input;
    private HashMap<String, Data> output;
    private HashMap<String,Property> properties;
    private Object context;

    public Object getContext() {
        return context;
    }

    public void setContext(Object context) {
        this.context = context;
    }

    //Temporary variables
    private boolean processed=false;

    public void fromJSON(JSONObject blockObject){
        this.name = blockObject.getString("type");

        Block block = workflow.getDefinition(this.name);

        if(block==null)return;
        this.family = block.getFamily();
        this.properties = block.getProperties();
        this.input = block.getInput();
        this.output = block.getOutput();

        JSONObject values = blockObject.getJSONObject("values");
        for(String key:this.properties.keySet()){
            if(values.has(key.toLowerCase())){
                for (Field f: context.getClass().getDeclaredFields()) {
                    f.setAccessible(true);
                    BlockProperty blockProperty = f.getAnnotation(BlockProperty.class);
                    if (blockProperty != null) {
                        try {
                            if(blockProperty.name().equals(key)){
                                properties.put(blockProperty.name(), new Property(blockProperty.name(), blockProperty.type(), blockProperty.defaultValue()));
                                if(f.getType().equals(int.class))
                                    f.set(context,(int) Double.parseDouble(values.getString(key.toLowerCase())));
                                else if(f.getType().equals(double.class))
                                f.set(context,Double.parseDouble(values.getString(key.toLowerCase())));

                                else f.set(context, f.getType().cast(values.getString(key.toLowerCase())));
                                break;
                            }
                        } catch (IllegalAccessException e) {
                            e.printStackTrace();
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
            for(String input_param:input.keySet()) {
                Data input_value=input.get(input_param);
                JSONObject input_obj = new JSONObject();
                input_obj.put("name", input_value.getName());
                input_obj.put("type", input_value.getType());
                input_obj.put("attrs", "input");
                input_obj.put("card", input_value.getCardinality());
                fields.put(input_obj);
            }
        }

        if(output!=null && output.size()!=0) {
            for(String output_param:output.keySet()){
                Data output_value=output.get(output_param);
                JSONObject output_obj = new JSONObject();
                output_obj.put("name", output_value.getName());
                output_obj.put("type", output_value.getType());
                output_obj.put("attrs", "output");
                output_obj.put("card", output_value.getCardinality());
                fields.put(output_obj);
            }
        }
        blockjs.put("fields", fields);

        return blockjs;
    }

    public void processBlock(HashMap<Integer, Block> blocks, HashMap<String, Integer> source_blocks, HashMap<String, String> source_params){
        if(getInput()!=null&&getInput().size()>0) {
            for (String key : getInput().keySet()) {
                Data destination_data=getInput().get(key);
                Block source_block = blocks.get(source_blocks.get(key.toLowerCase()));

                HashMap<String, Data> source = source_block.getOutput();
                Data source_data=null;
                for(String source_key:source_params.keySet()){
                    if(source_key.equals(key.toLowerCase())){
                        source_data=source.get(source_params.get(key));
                        break;
                    }
                }
                Object value = null;

                for (Field f: source_block.getContext().getClass().getDeclaredFields()) {
                    f.setAccessible(true);

                    BlockOutput blockOutput = f.getAnnotation(BlockOutput.class);
                    if (blockOutput != null){
                        try {
                            if(blockOutput.name().equals(source_data.getName())) {
                                value = f.get(source_block.getContext());
                                break;
                            }
                        } catch ( IllegalAccessException e) {
                            e.printStackTrace();
                        }
                    }
                }

                for (Field f: context.getClass().getDeclaredFields()) {
                    f.setAccessible(true);

                    BlockInput blockInput = f.getAnnotation(BlockInput.class);
                    if (blockInput != null) {
                        try {
                            if(blockInput.name().equals(destination_data.getName())){
                                f.set(context,value);
                                break;
                            }

                        } catch (IllegalAccessException e) {
                            e.printStackTrace();
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

    public HashMap<String, Property> getProperties() {
        return properties;
    }

    public void setProperties(HashMap<String, Property> properties) {
        this.properties = properties;
    }

    public HashMap<String, Data> getInput() {
        return input;
    }

    public void setInput(HashMap<String, Data> input) {
        this.input = input;
    }

    public HashMap<String, Data> getOutput() {
        return output;
    }

    public void setOutput(HashMap<String, Data> output) {
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
