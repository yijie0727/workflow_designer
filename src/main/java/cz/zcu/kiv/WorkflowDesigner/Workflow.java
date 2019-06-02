package cz.zcu.kiv.WorkflowDesigner;

import cz.zcu.kiv.WorkflowDesigner.Annotations.BlockType;
import cz.zcu.kiv.WorkflowDesigner.Visualizations.PlotlyGraphs.Graph;
import cz.zcu.kiv.WorkflowDesigner.Visualizations.Table;
import org.apache.commons.io.FileUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONArray;
import org.json.JSONObject;
import org.reflections.Reflections;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.*;

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

    private String jarDirectory;
    private String remoteDirectory;
    private ClassLoader classLoader;
    private Map<Class,String>moduleSource;
    private String module;
    private List<Block> blockDefinitions = null;


    private static Log logger = LogFactory.getLog(Workflow.class);


    /**
     * Workflow Constructor
     * @param classLoader - classLoader to load classes into
     * @param moduleSource Mapping of Class Block to actual module Name
     * @param jarDirectory - Directory where JAR files are saved (for runAsJar)
     * @param remoteDirectory - Directory to load files from
     */
    public Workflow( ClassLoader classLoader, Map<Class, String>moduleSource, String jarDirectory, String remoteDirectory){
        this.classLoader = classLoader;
        this.moduleSource = moduleSource;
        this.jarDirectory = jarDirectory;
        this.remoteDirectory = remoteDirectory;
    }

    /**
     * Workflow Constructor
     * @param classLoader - classLoader to load classes into
     * @param module - Module name in format jarName:packageName
     * @param jarDirectory - Directory where JAR files are saved (for runAsJar)
     * @param remoteDirectory - Directory to load files from
     */
    public Workflow( ClassLoader classLoader, String module, String jarDirectory, String remoteDirectory){
        this.classLoader = classLoader;
        this.module = module;
        this.jarDirectory = jarDirectory;
        this.remoteDirectory = remoteDirectory;
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
        logger.info("Initialized "+blocksArray.length()+" blocks");
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
            // Load classes from packages using reflection
            blockTypes = new Reflections(packages.toArray(new String[packages.size()]),this.classLoader).getTypesAnnotatedWith(BlockType.class);

        }
        else
            //Load classes from specific module
            blockTypes = new Reflections(module.split(":")[1],classLoader).getTypesAnnotatedWith(BlockType.class);

        for(Class blockType:blockTypes){
                //Instantiate block
                Block block= new Block(blockType.newInstance(),this);
                Annotation annotation = blockType.getAnnotation(BlockType.class);
                Class<? extends Annotation> type = annotation.annotationType();

                //Load information from annotations
                String blockTypeName = (String)type.getDeclaredMethod("type").invoke(annotation, (Object[])null);
                String blockTypeFamily = (String)type.getDeclaredMethod("family").invoke(annotation, (Object[])null);
                Boolean jarExecutable = (Boolean) type.getDeclaredMethod("runAsJar").invoke(annotation, (Object[])null);
                String description = (String)type.getDeclaredMethod("description").invoke(annotation, (Object[])null);
                block.setName(blockTypeName);
                block.setFamily(blockTypeFamily);
                block.setJarExecutable(jarExecutable);
                block.setDescription(description);

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
            if(block==null) {
                logger.error("No class for "+blockObject.getString("type") + " block type found");
                throw new FieldMismatchException(blockObject.getString("type"),"block type");
            }

            //Initialize values from the JSONObject
            block.fromJSON(blockObject);

            //Initialize the block I/O and configurations
            block.initialize();

            //Set reference ID of block
            blocks.put(blockObject.getInt("id"),block);

        }
        return blocks;
    }

    /**
     * populateWaitList - Joey Pinto
     *
     * Populate wait list of blocks that are unprocessed
     * and their source blocks are already processed or they only have properties
     * @param edgesArray - JSON from frontend containing connected edges
     * @param blocks - Map of block ID to block object
     * @return
     */
    public List<Integer> populateWaitList(JSONArray edgesArray, Map<Integer,Block>blocks){
        List<Integer>wait=new ArrayList<>();
        for(Integer blockId:blocks.keySet()){
            Block current=blocks.get(blockId);
            if(current.isProcessed())continue;
            if(current.getInput()==null||current.getInput().isEmpty()){
                wait.add(blockId);
                continue;
            }
            boolean readyFlag=true;
            for(int i=0;i<edgesArray.length();i++) {
                JSONObject edgeObject = edgesArray.getJSONObject(i);
                int block1Id=edgeObject.getInt("block1");
                int block2Id=edgeObject.getInt("block2");
                Block block1 = blocks.get(block1Id);
//                Block block2 = blocks.get(block2Id);

                if(blockId==block2Id && !block1.isProcessed()){
                    readyFlag=false;
                }
            }
            if(readyFlag){
                wait.add(blockId);
            }
        }

        logger.info("Wait list has "+wait.size()+" blocks");
        return wait;
    }


    /**
     * execute - Joey Pinto
     *
     * process a workflow JSON Object
     *
     * @param jObject JSON Workflow to be executed
     * @param outputFolder Folder to save output Files into
     * @param workflowOutputFile File to constantly update the progress of the workflow
     * @throws Exception
     */
    public JSONArray execute(JSONObject jObject, String outputFolder, String workflowOutputFile)
            throws InterruptedException, ExecutionException, InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException, FieldMismatchException, IOException {

        logger.info("Starting execution of Workflow");
        JSONArray blocksArray = jObject.getJSONArray("blocks");

        //Accumulate and index all blocks defined in the workflow
        Map<Integer,Block> blocks=indexBlocks(blocksArray);

        JSONArray edgesArray = jObject.getJSONArray("edges");

        //Block waitBlock;
        boolean error=false;
        while(!error){
            //Populate wait list
            List<Integer>wait= populateWaitList(edgesArray,blocks);

            //Wait queue is empty, exit
            if(wait.size()==0)break;


            BlockThreadPool blockThreadPool = new BlockThreadPool(jObject, outputFolder, workflowOutputFile, wait, blocks, this);
            error = blockThreadPool.createBlocksThreadPool();


            //Process wait queue
//            for (Integer aWait : wait) {
//                boolean ready = true;
//                int waitBlockId = aWait;
//                waitBlock = blocks.get(waitBlockId);
//
//                Map<Integer, Block> dependencies = new HashMap<>();
//
//
//                Map<String,InputField>fields=new HashMap<>();
//
//
//                //Check dependencies of waiting block
//                for (int i = 0; i < edgesArray.length(); i++) {
//                    JSONObject edgeObject = edgesArray.getJSONObject(i);
//
//                    //Choose only edges that end on block 2
//                    if (waitBlockId != edgeObject.getInt("block2")) continue;
//
//                    int block1Id = edgeObject.getInt("block1");
//                    Block block1 = blocks.get(block1Id);
//
//                    //Populate the dependencies into the maps
//                    populateDependencies(block1Id,block1,edgeObject,dependencies,fields);
//
//                    //A dependency is unprocessed so not ready
//                    if (!block1.isProcessed()) {
//                        ready = false;
//                        break;
//                    }
//                }
//
//                if (ready) {
//                    JSONObject block=getBlockById(blocksArray,waitBlockId);
//                    logger.info("Processing block with ID "+waitBlockId);
//                    StringBuilder stdOutBuilder =new StringBuilder();
//                    StringBuilder stdErrBuilder =new StringBuilder();
//
//                    //Process the ready block
//                    Object output = null;
//                    try {
//                        output = waitBlock.processBlock(dependencies, fields, stdOutBuilder, stdErrBuilder);
//                        block.put("error",false);
//                    }
//                    catch(Exception e){
//                        logger.error(e);
//                        error=true;
//                        block.put("error",true);
//                    }
//
//                    //Assemble the output JSON
//                    JSONObject jsonObject = new JSONObject();
//                    if(output==null){
//                        jsonObject = null;
//                    }
//                    else if (output.getClass().equals(String.class)){
//                        jsonObject.put("type","STRING");
//                        jsonObject.put("value",output);
//                    }
//                    else if (output.getClass().equals(File.class)){
//                        File file = (File) output;
//                        String destinationFileName="file_"+new Date().getTime()+"_"+file.getName();
//                        FileUtils.moveFile(file,new File(outputFolder+File.separator+destinationFileName));
//                        jsonObject.put("type","FILE");
//                        JSONObject fileObject=new JSONObject();
//                        fileObject.put("title",file.getName());
//                        fileObject.put("filename",destinationFileName);
//                        jsonObject.put("value",fileObject);
//                    }
//                    else if (output.getClass().equals(Table.class)){
//                        Table table=(Table)output;
//                        jsonObject.put("type","TABLE");
//                        jsonObject.put("value",table.toJSON());
//                        File file =File.createTempFile("temp_",".csv");
//                        FileUtils.writeStringToFile(file,table.toCSV(),Charset.defaultCharset());
//                        String destinationFileName="table_"+new Date().getTime()+".csv";
//                        FileUtils.moveFile(file,new File(outputFolder+File.separator+destinationFileName));
//                        JSONObject fileObject=new JSONObject();
//                        fileObject.put("title",destinationFileName);
//                        fileObject.put("filename",destinationFileName);
//                        jsonObject.put("value",fileObject);
//                    }
//                    else if (output.getClass().equals(Graph.class)){
//                        Graph graph=(Graph)output;
//                        jsonObject.put("type","GRAPH");
//                        jsonObject.put("value",graph.toJSON());
//                        File file =File.createTempFile("temp_",".json");
//                        FileUtils.writeStringToFile(file,graph.toJSON().toString(4),Charset.defaultCharset());
//                        String destinationFileName="graph_"+new Date().getTime()+".json";
//                        FileUtils.moveFile(file,new File(outputFolder+File.separator+destinationFileName));
//                        JSONObject fileObject=new JSONObject();
//                        fileObject.put("title",destinationFileName);
//                        fileObject.put("filename",destinationFileName);
//                        jsonObject.put("value",fileObject);
//                    }
//                    else{
//                        jsonObject.put("type","");
//                        jsonObject.put("value",output.toString());
//                    }
//
//                    if (jsonObject != null)
//                        block.put("output", jsonObject);
//                        block.put("stdout", stdOutBuilder.toString());
//                        block.put("stderr", stdErrBuilder.toString());
//                        block.put("completed", true);
//
//                    //Save Present state of output to file
//                    if(workflowOutputFile!=null){
//                        File workflowOutput=new File(workflowOutputFile);
//                        FileUtils.writeStringToFile(workflowOutput,blocksArray.toString(4),Charset.defaultCharset());
//                    }
//                }
//
//
//                if(error)break;
//            }

        }

        if(!error)
            logger.info("Workflow Execution completed successfully!");
        else
            logger.error("Workflow Execution failed!");
        return blocksArray;
    }



    /**
     * populateDependencies - Joey Pinto
     *
     * Populate the dependency maps of a block
     *
     * @param block1Id - ID of block to populate dependencies
     * @param block1 - Block object
     * @param edgeObject - JSON containg edge definitions
     * @param dependencies - Map of Blocks that are dependencies
     * @param fields - Input field Annotations
     */

    public void populateDependencies(int block1Id, Block block1, JSONObject edgeObject, Map<Integer,Block> dependencies, Map<String,InputField> fields) {
        JSONArray connector1 = edgeObject.getJSONArray("connector1");
        JSONArray connector2 = edgeObject.getJSONArray("connector2");

            InputField field;
            if(fields.containsKey(connector2.getString(0))){
                field = fields.get(connector2.getString(0));

            }
            else{
                field=new InputField();
                List<String>sourceParams = new ArrayList<>();
                field.setSourceParam(sourceParams);
                List<Integer>sourceBlocks = new ArrayList<>();
                field.setSourceBlock(sourceBlocks);
                field.setDestinationParam(connector2.getString(0));
                fields.put(field.getDestinationParam(),field);

            }
            field.getSourceParam().add(connector1.getString(0));
            field.getSourceBlock().add(block1Id);


        dependencies.put(block1Id, block1);
    }

    /**
     * Get a block from the JSON workflow by id
     *
     * @param blocksArray - JSON workflow
     * @param waitBlockId - Block ID
     * @return
     */
    public static JSONObject getBlockById(JSONArray blocksArray, int waitBlockId){
        for(int i=0;i<blocksArray.length();i++){
            JSONObject block=blocksArray.getJSONObject(i);
            if(block.getInt("id")==waitBlockId){
                return block;
            }
        }
        return null;
    }


    public String getJarDirectory() {
        return jarDirectory;
    }

    public void setJarDirectory(String jarDirectory) {
        this.jarDirectory = jarDirectory;
    }

    public String getRemoteDirectory() {
        return remoteDirectory;
    }

    public void setRemoteDirectory(String remoteDirectory) {
        this.remoteDirectory = remoteDirectory;
    }
}
