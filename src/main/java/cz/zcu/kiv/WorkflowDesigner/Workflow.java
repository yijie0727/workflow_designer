package cz.zcu.kiv.WorkflowDesigner;

import cz.zcu.kiv.WorkflowDesigner.Annotations.BlockType;
import org.apache.commons.io.FileUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.reflections.Reflections;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.Charset;
import java.util.*;

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
 * Workflow, 2018/17/05 6:32 Joey Pinto
 *
 * This file hosts the methods used to dynamically create the Javascript files needed for the workflow designer
 **********************************************************************************************************************/
public class Workflow {

    private ClassLoader classLoader;
    private Map<Class,String>moduleSource;
    private String module;
    private List<Block> blockDefinitions = null;


    public Workflow( ClassLoader classLoader, Map<Class, String>moduleSource){
        this.classLoader = classLoader;
        this.moduleSource = moduleSource;
    }

    public Workflow( String module,ClassLoader classLoader){
        this.classLoader = classLoader;
        this.module = module;
    }

    /**
     * initializeBlocks - Joey Pinto
     * This method initializes a directory made up of javascript files with all annotated blocktypes
     * @throws IOException - Exception if there is a problem creating directories
     */
    public JSONArray initializeBlocks() throws InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException {
        JSONArray blocksArray=new JSONArray();
        for(Block block:getBlockDefinitions()){
            //Write JS file description of block to array
            blocksArray.put(block.toJSON());
        }
        return blocksArray;
    }

    /**
     * getBlockDefinitions - Joey Pinto
     * This method creates a singleton access to block definitions
     *
     * If not initialized, it searches for all classes with @BlockType annotations and gets the type and family
     * @return List of Block objects
     */
    private List<Block> getBlockDefinitions() throws IllegalAccessException, InstantiationException, NoSuchMethodException, InvocationTargetException {
        if(blockDefinitions !=null) return blockDefinitions;
        else blockDefinitions = new ArrayList<>();

        Set<Class<?>> blockTypes;
        if(moduleSource!=null){
            Collection<String> modules = moduleSource.values();
            HashSet<String>packages=new HashSet<>();
            for(String module:modules){
                packages.add(module.split(":")[1]);
            }
            blockTypes = new Reflections(packages.toArray(new String[packages.size()]),this.classLoader).getTypesAnnotatedWith(BlockType.class);

        }
        else
            blockTypes = new Reflections(module.split(":")[1],classLoader).getTypesAnnotatedWith(BlockType.class);

        for(Class blockType:blockTypes){
                Block block= new Block(blockType.newInstance(),this);
                Annotation annotation = blockType.getAnnotation(BlockType.class);
                Class<? extends Annotation> type = annotation.annotationType();
                String blockTypeName=(String)type.getDeclaredMethod("type").invoke(annotation, (Object[])null);
                String blockTypeFamily=(String)type.getDeclaredMethod("family").invoke(annotation, (Object[])null);
                Boolean jarExecutable = (Boolean) type.getDeclaredMethod("runAsJar").invoke(annotation, (Object[])null);
                block.setName(blockTypeName);
                block.setFamily(blockTypeFamily);
                block.setJarExecutable(jarExecutable);
                if(moduleSource!=null)
                    block.setModule(moduleSource.get(blockType));
                else{
                    block.setModule(module);
                }
                block.initialize();
                blockDefinitions.add(block);

        }
        return blockDefinitions;
    }

    /**
     * getDefinition - Joey Pinto
     *
     * get the definition of a block with it's type name
     * @param name
     * @return Block attributes
     */
    public Block getDefinition(String name) throws InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException {
        for(Block block:getBlockDefinitions()){
            if(block.getName().equals(name)){
                return block;
            }
        }
        return null;
    }

    /**
     * indexBlocks - Joey Pinto
     * Index Blocks from JSONArray to Map
     * @param blocksArray
     * @return  Map of blocks indexed with block ids
     */
    public Map<Integer, Block> indexBlocks(JSONArray blocksArray) throws IllegalAccessException, NoSuchMethodException, InvocationTargetException, InstantiationException, FieldMismatchException {
        Map<Integer,Block> blocks=new HashMap<>();
        for(int i=0; i<blocksArray.length(); i++){

            JSONObject blockObject=blocksArray.getJSONObject(i);
            Block block = null;

            //get Block object by type of block in JSON
            Set<Class<?>> blockTypes;
            if(moduleSource!=null){
                Collection<String> modules = moduleSource.values();
                HashSet<String>packages=new HashSet<>();
                for(String module:modules){
                    packages.add(module.split(":")[1]);
                }
                blockTypes = new Reflections(packages.toArray(new String[packages.size()]),this.classLoader).getTypesAnnotatedWith(BlockType.class);
            }
            else{
                blockTypes = new Reflections(module.split(":")[1],this.classLoader).getTypesAnnotatedWith(BlockType.class);
            }

            for(Class blockType:blockTypes){
                Annotation annotation = blockType.getAnnotation(BlockType.class);
                Class<? extends Annotation> type = annotation.annotationType();
                String blockTypeName = (String)type.getDeclaredMethod("type").invoke(annotation, (Object[])null);
                if (blockObject.getString("type").equals(blockTypeName)){
                        block = new Block(blockType.newInstance(),this);
                        break;
                }
            }
            if(block==null) throw new FieldMismatchException(blockObject.getString("type"),"block type");

            //Initialize the block I/O and configurations
            block.initialize();

            //Initialize values from the JSONObject
            block.fromJSON(blockObject);

            //Set reference ID of block
            blocks.put(blockObject.getInt("id"),block);

        }
        return blocks;
    }

    /**
     * populateWaitList - Joey Pinto
     *
     * Populate wait list of blocks that are unprocessed
     * @param edgesArray
     * @param blocks
     * @return
     */
    public ArrayList<Integer> populateWaitList(JSONArray edgesArray, Map<Integer,Block>blocks){
        ArrayList<Integer>wait=new ArrayList<>();
        for(int i=0;i<edgesArray.length();i++) {
            JSONObject edgeObject = edgesArray.getJSONObject(i);
            Block block1 = blocks.get(edgeObject.getInt("block1"));
            Block block2 = blocks.get(edgeObject.getInt("block2"));
            if(!block1.isProcessed()){
                if(block1.getInput()==null||block1.getInput().size()==0){
                    wait.add(edgeObject.getInt("block1"));
                }
            }
            if(!block2.isProcessed()){
                if (block1.isProcessed() && !block2.isProcessed()) {
                    wait.add(edgeObject.getInt("block2"));
                }
            }


        }
        return wait;
    }


    /**
     * execute - Joey Pinto
     *
     * process a workflow JSON Object
     *
     * @param jObject
     * @throws Exception
     */
    public JSONArray execute(JSONObject jObject, String outputFolder) throws InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException, FieldMismatchException, IOException {
        JSONArray blocksArray = jObject.getJSONArray("blocks");

        //Accumulate and index all blocks defined in the workflow
        Map<Integer,Block> blocks=indexBlocks(blocksArray);

        JSONArray edgesArray = jObject.getJSONArray("edges");

        Block waitBlock;
        while(true){
            //Populate wait list
            List<Integer>wait= populateWaitList(edgesArray,blocks);

            //Wait queue is empty, exit
            if(wait.size()==0)break;

            //Process wait queue
            for (Integer aWait : wait) {
                boolean ready = true;
                int waitBlockId = aWait;
                waitBlock = blocks.get(waitBlockId);

                Map<Integer, Block> dependencies = new HashMap<>();
                Map<String, String> sourceParam = new HashMap<>();
                Map<String, Integer> sourceBlock = new HashMap<>();

                
                //Check dependencies of waiting block
                for (int i = 0; i < edgesArray.length(); i++) {
                    JSONObject edgeObject = edgesArray.getJSONObject(i);

                    //Choose only edges that end on block 2
                    if (waitBlockId != edgeObject.getInt("block2")) continue;

                    int block1Id = edgeObject.getInt("block1");
                    Block block1 = blocks.get(block1Id);

                    //Populate the dependencies into the maps
                    populateDependencies(block1Id,block1,edgeObject,dependencies,sourceParam,sourceBlock);

                    //A dependency is unprocessed so not ready
                    if (!block1.isProcessed()) {
                        ready = false;
                        break;
                    }
                }

                if (ready) {
                    //Process the ready block
                    Object output = waitBlock.processBlock(dependencies, sourceBlock, sourceParam);

                    String resultString;

                    if(output==null){
                        resultString="";
                    }
                    else if (output.getClass().equals(String.class)){
                        resultString=(String)output;
                    }
                    else if (output.getClass().equals(File.class)){
                        File file = (File) output;
                        String destinationFileName="file_"+new Date().getTime()+"_"+file.getName();
                        FileUtils.moveFile(file,new File(outputFolder+File.separator+destinationFileName));
                        resultString="<a href=\"rest/workflow/file/"+destinationFileName+"\">"+file.getName()+"</a>";
                    }
                    else if (output.getClass().equals(Table.class)){
                        Table table=(Table)output;
                        resultString=table.getHTML();

                    }
                    else
                        resultString=output.toString();

                    for(int i=0;i<blocksArray.length();i++){
                        JSONObject block=blocksArray.getJSONObject(i);
                        if(block.getInt("id")==waitBlockId){
                            block.put("output",resultString);
                            break;
                        }
                    }
                    break;
                }

            }
        }
        return blocksArray;
    }

    /**
     * populateDependencies - Joey Pinto
     *
     * Populate the dependency maps of a block
     *
     * @param block1Id
     * @param block1
     * @param edgeObject
     * @param dependencies
     * @param sourceParam
     * @param sourceBlock
     */
    private void populateDependencies(int block1Id, Block block1, JSONObject edgeObject, Map<Integer,Block> dependencies, Map<String,String> sourceParam, Map<String,Integer> sourceBlock) {
        JSONArray connector1 = edgeObject.getJSONArray("connector1");
        JSONArray connector2 = edgeObject.getJSONArray("connector2");

        for (int k = 0; k < connector1.length(); k++) {
            sourceParam.put(connector2.getString(k), connector1.getString(k));
            sourceBlock.put(connector2.getString(k), block1Id);
        }

        dependencies.put(block1Id, block1);
    }

    /**
     *
     * @param args 1)Module Name 2)Workflow JSON 3)Generated Files dump folder 4) Output file location
     * @throws FieldMismatchException InputField-OutputField Mismatch
     * @throws NoSuchMethodException Reflection Problems
     * @throws IOException When cannot create file
     */
    public static void main(String[] args) throws FieldMismatchException, NoSuchMethodException, InstantiationException, IllegalAccessException, InvocationTargetException, IOException {
        if(args.length<4){
            throw new IOException("Insufficient Arguments (5 needed)");
        }
        Workflow workflow = new Workflow(ClassLoader.getSystemClassLoader(),new HashMap<Class, String>());
        JSONArray jsonArray = workflow.execute(new JSONObject(args[1]), args[2]);
        FileUtils.writeStringToFile(new File(args[3]),jsonArray.toString(4),Charset.defaultCharset());
    }


}
