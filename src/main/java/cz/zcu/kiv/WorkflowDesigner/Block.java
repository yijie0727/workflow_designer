package cz.zcu.kiv.WorkflowDesigner;

import cz.zcu.kiv.WorkflowDesigner.Annotations.*;
import org.apache.commons.io.FileUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.reflections.Reflections;
import java.io.*;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

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
    private String module;
    private boolean jarExecutable;
    private Map<String, Data> input;
    private Map<String, Data> output;
    private Map<String,Property> properties;
    private Object context;
    private static Log logger = LogFactory.getLog(Block.class);

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
        this.module = blockObject.getString("module");
        Block block = workflow.getDefinition(this.name);

        if(block==null)return;
        this.family = block.getFamily();
        this.properties = block.getProperties();
        this.input = block.getInput();
        this.output = block.getOutput();
        this.jarExecutable = block.isJarExecutable();


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

    public String getModule() {
        return module;
    }

    public void setModule(String module) {
        this.module = module;
    }

    public JSONObject toJSON(){
        JSONObject blockjs=new JSONObject();
        blockjs.put("name",getName());
        blockjs.put("family", getFamily());
        blockjs.put("module", getModule());
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

    public Object processBlock(Map<Integer, Block> blocks, Map<String, Integer> sourceBlocks, Map<String, String> sourceParams) throws IllegalAccessException, FieldMismatchException, IOException {
        Object output;
        BlockData blockData=new BlockData(getName());

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
                            blockData.getInput().put(destinationData.getName(),value);
                            break;
                        }
                    }
                }
            }
        }

        for (Field f: context.getClass().getDeclaredFields()) {
            f.setAccessible(true);

            BlockProperty blockProperty = f.getAnnotation(BlockProperty.class);
            if (blockProperty != null) {
                blockData.getProperties().put(blockProperty.name(),f.get(context));
            }
        }

            if(isJarExecutable()&&workflow.getJarDirectory()!=null)
            try {
                String fileName = "obj_" + new Date().getTime() ;
                File inputFile=new File(fileName+".in");
                File outputFile =new File(fileName+".out");
                FileOutputStream fos = new FileOutputStream(inputFile);
                ObjectOutputStream oos = new ObjectOutputStream(fos);
                oos.writeObject(blockData);
                oos.close();
                File jarDirectory = new File(workflow.getJarDirectory());
                jarDirectory.mkdirs();
                File jarFile = new File(jarDirectory.getAbsolutePath()+File.separator+getModule().split(":")[0]);
                String[]args=new String[]{"java", "-cp",jarFile.getAbsolutePath() ,"cz.zcu.kiv.WorkflowDesigner.Block",inputFile.getAbsolutePath(),outputFile.getAbsolutePath(),getModule().split(":")[1]};
                ProcessBuilder pb = new ProcessBuilder(args);
                Process ps = pb.start();
                ps.waitFor();
                InputStream is=ps.getErrorStream();
                byte b[]=new byte[is.available()];
                is.read(b,0,b.length);
                logger.error(new String(b));

                is=ps.getInputStream();
                b=new byte[is.available()];
                is.read(b,0,b.length);
                logger.info(new String(b));


                FileInputStream fis = new FileInputStream(outputFile);
                ObjectInputStream ois = new ObjectInputStream(fis);
                blockData = (BlockData) ois.readObject();
                ois.close();
                output=blockData.getProcessOutput();
                FileUtils.deleteQuietly(inputFile);
                FileUtils.deleteQuietly(outputFile);

                for (Field f: context.getClass().getDeclaredFields()) {
                    f.setAccessible(true);
                    BlockOutput blockOutput = f.getAnnotation(BlockOutput.class);
                    if (blockOutput != null) {
                        f.set(context,blockData.getOutput().get(blockOutput.name()));
                    }
                }

            }
            catch (Exception e){
                e.printStackTrace();
                throw new IOException("Error executing Jar");
            }
            else{
            output=process();
            }

        setProcessed(true);
        return output;
    }

    public Object process(){
        Object output = null;
        for(Method method:context.getClass().getDeclaredMethods()){
            method.setAccessible(true);
            if(method.getAnnotation(BlockExecute.class)!=null){
                try {
                    output =  method.invoke(context);
                } catch (IllegalAccessException | InvocationTargetException e) {
                    e.printStackTrace();
                }
            }
        }
        return output;
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
    public static void main(String[] args){

        try {

            FileInputStream fis = new FileInputStream(args[0]);
            ObjectInputStream ois = new ObjectInputStream(fis);
            BlockData blockData = (BlockData) ois.readObject();
            ois.close();

            Set<Class<?>> blockTypes = new Reflections(args[2]).getTypesAnnotatedWith(BlockType.class);

            Class type = null;

            for (Class blockType : blockTypes) {
                Annotation annotation = blockType.getAnnotation(BlockType.class);
                Class<? extends Annotation>  currentType = annotation.annotationType();

                String blockTypeName = (String) currentType.getDeclaredMethod("type").invoke(annotation, (Object[]) null);
                if (blockData.getName().equals(blockTypeName)) {
                    type=blockType;
                    break;
                }
            }

            Object obj;
            if(type!=null){
                obj=type.newInstance();
            }
            else{
                throw new Exception("Error Finding Annotated Class");
            }


            for(Field field:type.getDeclaredFields()){
                field.setAccessible(true);
                if(field.getAnnotation(BlockInput.class)!=null){
                    field.set(obj,blockData.getInput().get(field.getAnnotation(BlockInput.class).name()));
                }
                else if(field.getAnnotation(BlockProperty.class)!=null){
                    field.set(obj,blockData.getProperties().get(field.getAnnotation(BlockProperty.class).name()));
                }
            }


            Method executeMethod = null;

            for(Method m:type.getDeclaredMethods()){
                m.setAccessible(true);
                if(m.getAnnotation(BlockExecute.class)!=null){
                    executeMethod=m;
                    break;
                }
            }

            if(executeMethod!=null){
                Object outputObj=executeMethod.invoke(obj);
                blockData.setProcessOutput(outputObj);
            }
            else{
                throw new Exception("Error finding Execute Method");
            }

            blockData.setOutput(new HashMap<String, Object>());

            for(Field field:type.getDeclaredFields()){
                field.setAccessible(true);
                if(field.getAnnotation(BlockOutput.class)!=null){
                    blockData.getOutput().put(field.getAnnotation(BlockOutput.class).name(),field.get(obj));
                }

            }

            FileOutputStream fos = new FileOutputStream(new File(args[1]));
            ObjectOutputStream oos = new ObjectOutputStream(fos);
            oos.writeObject(blockData);
            oos.close();

        }
        catch (Exception e){

            try {
                FileUtils.writeStringToFile(new File("error.txt"),e.getMessage(),Charset.defaultCharset());
            } catch (IOException e1) {
                e1.printStackTrace();
            }

        }
    }

    public boolean isJarExecutable() {
        return jarExecutable;
    }

    public void setJarExecutable(boolean jarExecutable) {
        this.jarExecutable = jarExecutable;
    }
}
