package cz.zcu.kiv.WorkflowDesigner;

import cz.zcu.kiv.WorkflowDesigner.Annotations.BlockType;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONArray;
import org.json.JSONObject;
import org.reflections.Reflections;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/***********************************************************************************************************************
 *
 * This file is part of the Workflow Designer project

 * ==========================================
 *
 * Copyright (C) 2019 by University of West Bohemia (http://www.zcu.cz/en/)
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
 * BlockWorkFlow, 2019 GSoC P1 P2 Yijie Huang
 *
 * This file hosts the methods used to dynamically create the Javascript files needed for the workflow designer
 * Front end sends JSON to it to initialize block tree and execute workFlow.
 **********************************************************************************************************************/

public class BlockWorkFlow {

    private static Log logger = LogFactory.getLog(BlockWorkFlow.class);

    private String jarDirectory;
    private String remoteDirectory; //File location where the user upload their file waiting to be execute
    private ClassLoader classLoader;
    private Map<Class,String> moduleSource; //initialize workflow
    private String module; //initialize front-end blocks tree

    private List<BlockObservation> blockDefinitions;// all the blocks from one module only used for front end

    private Map<Integer,  BlockObservation> indexBlocksMap;
    private boolean[] errorFlag = new boolean[1]; //denote whether the whole workFlow completed successfully or not
    private Set<Integer> startBlocksSet;
    private int[] count = new int[1];


    private long jobID;//one workFlow one jobID


    private boolean continuousFlag;

    /**
     * Constructor for building BlockTrees for front-End  -- (Front-End call: initializeBlocks)
     */
    public BlockWorkFlow(ClassLoader classLoader,  String module, String jarDirectory, String remoteDirectory) {
        this.remoteDirectory = remoteDirectory;
        this.classLoader = classLoader;
        this.module = module;
        this.jarDirectory = jarDirectory;
    }

    /**
     * Constructor to execute the Continuous WorkFlow -- (Front-End call: execute)
     */
    public BlockWorkFlow(ClassLoader classLoader, Map<Class, String> moduleSource, String jarDirectory, String remoteDirectory, long jobID) {
        this.remoteDirectory = remoteDirectory;
        this.classLoader = classLoader;
        this.moduleSource = moduleSource;
        this.jarDirectory = jarDirectory;
        this.jobID = jobID;

    }


    /**
     * initializeBlocks - Joey Pinto
     * prepare JSON for the Front end to form the blocks tree
     * This method initializes a directory made up of javascript files with all annotated blocktypes
     */
    public JSONArray initializeBlocks() throws InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException {
        initializeBlockDefinitions();
        JSONArray blocksArray=new JSONArray();
        for( BlockObservation block : this.blockDefinitions){
            //Write JS file description of block to array
            blocksArray.put(block.toJSON());
        }
        logger.info("Initialized "+blocksArray.length()+" blocks");
        return blocksArray;
    }

    /**
     * initializeBlockDefinitions() - Joey Pinto, Yijie Huang
     * This method creates a singleton access to block definitions,
     * it initializes List<BlockObservation> blockDefinitions for front end to form blocks tree
     */
    public void initializeBlockDefinitions() throws InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException {
        if(blockDefinitions !=null) return;

        List< BlockObservation> blocksList = new ArrayList<>();
        Set<Class<?>> blockClasses = new Reflections(module.split(":")[1], classLoader).getTypesAnnotatedWith(BlockType.class);
        for(Class blockClass : blockClasses){

            BlockObservation currBlock = createBlockInstance(blockClass, module, null, null);
            currBlock.initializeIO();
            blocksList.add(currBlock);

        }
        setBlockDefinitions(blocksList);
    }

    /**
     * createBlockInstance - Joey Pinto, Yijie Huang
     * This method initializes a block according to the given info
     */
    private BlockObservation createBlockInstance(Class blockClass, String moduleStr, JSONArray blocksArray, String workflowOutputFile) throws InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException {
        BlockObservation currBlock = new  BlockObservation(blockClass.newInstance(), this, blocksArray, workflowOutputFile );

        Annotation annotation = blockClass.getAnnotation(BlockType.class);
        Class<? extends Annotation> blockType = annotation.annotationType();

        String blockTypeName = (String)blockType.getDeclaredMethod("type").invoke(annotation);
        String blockTypeFamily = (String)blockType.getDeclaredMethod("family").invoke(annotation);
        String description = (String)blockType.getDeclaredMethod("description").invoke(annotation);
        Boolean jarExecutable = (Boolean) blockType.getDeclaredMethod("runAsJar").invoke(annotation);
        Boolean jarRMI = (Boolean) blockType.getDeclaredMethod("jarRMI").invoke(annotation);

        currBlock.setName(blockTypeName);
        currBlock.setFamily(blockTypeFamily);
        currBlock.setModule(moduleStr);
        currBlock.setDescription(description);
        currBlock.setJarExecutable(jarExecutable);
        currBlock.setJobID(jobID);
        currBlock.setRmiFlag(jarRMI);

        return currBlock;
    }


    /**
     * assignOutputWrites - Yijie Huang
     * assign each source block's  Map<String, List<PipedOutputStream>> outTransitWriteMap,
     * connect source block's output with all its next destination blocks' inputs
     */
    public void assignOutputWrites(JSONArray edgesArray){
        logger.info("put entity into outputWriteMap for each source block");

        for(int i = 0; i<edgesArray.length(); i++){
            JSONObject edge = edgesArray.getJSONObject(i);

            int block1ID = edge.getInt("block1");
            BlockObservation block1 = indexBlocksMap.get(block1ID);
            String sourceParam = edge.getJSONArray("connector1").getString(0);

            int block2ID = edge.getInt("block2");
            BlockObservation block2 = this.indexBlocksMap.get(block2ID);
            String destinationParam = edge.getJSONArray("connector2").getString(0);

            Map<String, List<PipedOutputStream>> outTransitWriteMap = block1.getOutTransitWriteMap();
            Map<String, PipedOutputStream>   inTransitsMap = block2.getInTransitsMap();
            if(!outTransitWriteMap.containsKey(sourceParam))
                outTransitWriteMap.put(sourceParam, new ArrayList<PipedOutputStream>());

            PipedOutputStream outTransit = inTransitsMap.get(destinationParam);
            outTransitWriteMap.get(sourceParam).add(outTransit);
        }

    }

    /**
     * assignPipes - Yijie Huang
     */
    public int assignPipes( ) throws IllegalAccessException, IOException{
        logger.info("assign PipedTransit for each block");

        int inputsNum = 0;
        for(int id: indexBlocksMap.keySet()){
            BlockObservation block = indexBlocksMap.get(id);
            block.assignPipeTransit();

            if(block.getInputs()!= null)
                inputsNum += block.getInputs().size();
        }

        return inputsNum;
    }


    /**
     * executeContinuous - Yijie Huang
     *
     * @param jObject               SONObject contains Blocks and Edges info
     *
     *  if all the @BlockType's continuousFlag are true, then execute in a continuous stream way:
     *   execute the whole workflow, using pipedInputStream and pipedOutputStream to connect all the blocks' @BlockInput and @BlockOutput
     *  if all these flags are false:  execute the workFlow in a cumulative data way
     */
    public JSONArray executeContinuous(JSONObject jObject) throws IOException, NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException, FieldMismatchException, InterruptedException {
        logger.info("  Start Continuous WorkFlow Execution …………………… ");

        JSONArray blocksArray = jObject.getJSONArray("blocks");
        JSONArray edgesArray  = jObject.getJSONArray("edges");
        errorFlag[0] = false;
        //mapIndexBlock(blocksArray, outputFolder, workflowOutputFile);

        int pipesInputsNum = assignPipes();

        assignOutputWrites(edgesArray);


        int poolSize  = blocksArray.length() + pipesInputsNum;
        ThreadPoolExecutor threadPool = new ThreadPoolExecutor(poolSize, poolSize, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>(poolSize), new ThreadPoolExecutor.AbortPolicy());


        logger.info("……………………………………………………………………   Submit ContinuousBlockThread to threadPool:  ……………………………………………………………………");
        for(int id: indexBlocksMap.keySet()){
            BlockObservation currBlock = indexBlocksMap.get(id);
            ContinuousBlockThread currTask = new ContinuousBlockThread(id, currBlock, errorFlag);
            threadPool.execute(currTask);
        }

        logger.info("……………………………………………………………………   Submit PipeTransitThread to threadPool:  ………………………………………………………………………………");
        for(int id: indexBlocksMap.keySet()){
            BlockObservation block = indexBlocksMap.get(id);
            if(block.getOutTransitReadMap()  == null) continue; // skip blocks without outputs(the las blocks in the workFlows)

            Map<String, PipedInputStream>        outTransitReadMap  = block.getOutTransitReadMap();
            Map<String, List<PipedOutputStream>> outTransitWriteMap = block.getOutTransitWriteMap();

            for(String outputName: outTransitReadMap.keySet()){

                PipedInputStream pipedInTransit = outTransitReadMap.get(outputName);
                List<PipedOutputStream> pipedOutTransitsList = outTransitWriteMap.get(outputName);

                threadPool.execute(  new PipeTransitThread(id, outputName,  pipedInTransit,  pipedOutTransitsList)  );

            }
        }

        threadPool.shutdown();
        boolean loop;
        do {
            loop = !threadPool.awaitTermination(2, TimeUnit.SECONDS);
        } while(loop && !errorFlag[0]);

        threadPool.shutdownNow();
        logger.info("………………………………………………………………………………………  ShutDown threadPool  …………………………………………………………………………………………………………………… ");




        if(!errorFlag[0])
            logger.info( "Workflow Execution completed successfully!");
        else
            logger.error("Workflow Execution failed!");


        return blocksArray;
    }


    /**
     * executeCumulative - Yijie Huang, Joey Pinto
     *
     * This method receives front end workflow's JSON file and to execute the workFlow.
     * @param jObject               SONObject contains Blocks and Edges info
     * @param outputFolder          Folder to save the output File
     * @param workflowOutputFile    File workflowOutputFile = File.createTempFile("job_"+getId(),".json",new File(WORKING_DIRECTORY));
     *                                  -- > File to put the Blocks JSONArray info with the output info, stdout, stderr, error info after the execution
     */
    public JSONArray execute(JSONObject jObject, String outputFolder, String workflowOutputFile) throws WrongTypeException, IOException, NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException, FieldMismatchException, InterruptedException {

        JSONArray blocksArray = jObject.getJSONArray("blocks");
        JSONArray edgesArray  = jObject.getJSONArray("edges");

        //initialize  and  set  map<ID,  BlockObservation> indexBlocksMap(config I/Os and assign properties)
        mapIndexBlock(blocksArray, outputFolder, workflowOutputFile);
        if(continuousFlag)
            return executeContinuous(jObject);

        logger.info("  Start Cumulative WorkFlow Execution …………………… ");

        count[0] = blocksArray.length();
        errorFlag[0] = false;

        //initialize IO map and block start list for thread
        mapBlocksIO(edgesArray);

        //add observers to their corresponding observables (add destination blocks to their corresponding source blocks)
        registerObservers();


        logger.info("………………………………………………………………………………………………  Start the threads for blocks in the start list:  ………………………………………………………………………………………………………………… ");
        for(int startBlockId : startBlocksSet){
            //count[0]++;
            BlockObservation startBlock = indexBlocksMap.get(startBlockId);
            logger.info("Start the execution of Blocks in the startBlocksSet - id "+startBlock.getId()+", name "+startBlock.getName()+ "in the start list");
            Thread myExecuteThread = new Thread(startBlock);
            myExecuteThread.start();
        }
        logger.info(" ………………… Submitted all the block threads in the start list ………………………");

        do{
            Thread.sleep(2000);
        }while(count[0]!= 0 && !errorFlag[0]);


        logger.info("……………………………………………………………………………………………………………………………………… All the threads finished …………………………………………………………………………………………………………………………………………………  ");
        if(!errorFlag[0])  logger.info( "Workflow Execution completed successfully!");
        else logger.error("Workflow Execution failed!");

        return blocksArray;
    }



    /**
     * mapBlockIndex - Yijie Huang, Joey Pinto
     *
     * map all the BlockObservations related in this workflow according to the front-end blocks JSONArray
     * initialize Map<Integer,  BlockObservation> indexMap
     * initialize all the properties
     * set continuousFlag to decide whether this workFlow should be executed in a continuous way or cumulative way.
     */
    public void mapIndexBlock(JSONArray blocksArray, String outputFolder, String workflowOutputFile) throws WrongTypeException, NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException, FieldMismatchException {
        logger.info("initialize all the related ContinuousBlocks(including I/O/properties initialization) in this workFlow and set the idBlocksMap");
        Map<Integer, BlockObservation> idBlocksMap = new HashMap<>();



        for(int i = 0; i<blocksArray.length(); i++){
            BlockObservation currBlock = null;

            JSONObject blockObject = blocksArray.getJSONObject(i);
            String blockTypeStr = blockObject.getString("type");
            int id = blockObject.getInt("id");
            String module = blockObject.getString("module");

            // get class from Constructor:  Map<Class, String> moduleSource,
            // when execute, moduleSource map is initialized not module string
            Set<Class> blockClasses = moduleSource.keySet();
            for(Class blockClass : blockClasses){

                Annotation annotation = blockClass.getAnnotation(BlockType.class);
                if(annotation == null) continue;
                Class<? extends Annotation> blockType = annotation.annotationType();
                String blockTypeName = (String)blockType.getDeclaredMethod("type").invoke(annotation);

                if(blockTypeName.equals(blockTypeStr)){
                    currBlock = createBlockInstance(blockClass, module, blocksArray, workflowOutputFile);
                    currBlock.setId(id);

                    boolean flagTmp = (boolean)blockType.getDeclaredMethod("continuousFlag").invoke(annotation);
                    if(i != 0 && continuousFlag != flagTmp){
                        throw new WrongTypeException();
                    }
                    continuousFlag = flagTmp;

                    break;
                }
            }
            if(currBlock == null){
                logger.error("No class for "+blockObject.getString("type") + " block type found");
                throw new FieldMismatchException(blockObject.getString("type"),"block type");
            }

            //Initialize the block I/O/properties and configurations
            currBlock.initializeIO();
            currBlock.assignProperties(blockObject);
            currBlock.setBlockObject(blockObject);
            currBlock.setOutputFolder(outputFolder);
            currBlock.setErrorFlag(errorFlag);
            currBlock.setCount(count);

            idBlocksMap.put(id, currBlock);
        }
        setIndexBlocksMap(idBlocksMap);
    }


    /**
     * mapBlocksIO - Yijie Huang
     * set Map<String, List< BlockSourceOutput>> IOMap for each Blocks according to the JSONArray edgeArray;
     * assign destinationObservers and sourceObservables for each block for Observer Pattern design;
     * initialize the startBlocksSet(): BlockObservations with no source blocks
     */
    public void mapBlocksIO(JSONArray edgesArray){
        logger.info("Set IOMap for each destination Blocks ");

        startBlocksSet = new HashSet<>(indexBlocksMap.keySet());

        for(int i = 0; i<edgesArray.length(); i++){
            JSONObject edge = edgesArray.getJSONObject(i);

            int block1ID = edge.getInt("block1");
            BlockObservation block1 = indexBlocksMap.get(block1ID);
            String sourceParam = edge.getJSONArray("connector1").getString(0);
            BlockSourceOutput sourceOutput = new BlockSourceOutput(block1ID, block1, sourceParam);

            int block2ID = edge.getInt("block2");
            BlockObservation block2 = this.indexBlocksMap.get(block2ID);
            String destinationParam = edge.getJSONArray("connector2").getString(0);

            Map<String, List<BlockSourceOutput>> IOMap =block2.getIOMap();
            if(!IOMap.containsKey(destinationParam))
                IOMap.put(destinationParam, new ArrayList<BlockSourceOutput>());
            IOMap.get(destinationParam).add(sourceOutput);
            block2.setIOMap(IOMap);


            // Set destinationObservers and sourceObservables for Observer Pattern
            List<BlockObservation> destinationObservers = block1.getDestinationObservers();
            if(!destinationObservers.contains(block2))
                destinationObservers.add(block2);
            block1.setDestinationObservers(destinationObservers);

            List<BlockObservation>  sourceObservables   = block2.getSourceObservables();
            if(!sourceObservables.contains(block1))
                sourceObservables.add(block1);
            block2.setSourceObservables(sourceObservables);

            // Deal with the start lists
            if(startBlocksSet.contains(block2ID)) startBlocksSet.remove(block2ID);
        }

        for(int id: indexBlocksMap.keySet()){
            BlockObservation currBlock = indexBlocksMap.get(id);
            logger.info("current block id"+currBlock.getId()+", name "+currBlock.getName()+", sourceObservables size = "+currBlock.getSourceObservables().size());
        }

    }

    /**
     * registerObservers() - Yijie Huang
     * add all the corresponding observers(dest BlockObservations) to the observables(source BlockObservations)
     */
    public void registerObservers(){
        for(int blockID : indexBlocksMap.keySet()){
            BlockObservation sourceBlock = indexBlocksMap.get(blockID);
            for(BlockObservation destinationBlock: sourceBlock.getDestinationObservers()){
                //add destinationBlock as observer to the sourceBlock observable
                sourceBlock.addObserver(destinationBlock);

            }
        }
    }



    public String getRemoteDirectory() {
        return remoteDirectory;
    }

    public void setRemoteDirectory(String remoteDirectory) {
        this.remoteDirectory = remoteDirectory;
    }

    public ClassLoader getClassLoader() {
        return classLoader;
    }

    public void setClassLoader(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    public Map<Class, String> getModuleSource() {
        return moduleSource;
    }

    public void setModuleSource(Map<Class, String> moduleSource) {
        this.moduleSource = moduleSource;
    }

    public String getModule() {
        return module;
    }

    public void setModule(String module) {
        this.module = module;
    }

    public List< BlockObservation > getBlockDefinitions() {
        return blockDefinitions;
    }

    public void setBlockDefinitions(List< BlockObservation > blockDefinitions) {
        this.blockDefinitions = blockDefinitions;
    }

    public Map<Integer,  BlockObservation> getIndexBlocksMap() {
        return indexBlocksMap;
    }

    public void setIndexBlocksMap(Map<Integer,  BlockObservation> indexBlocksMap) {
        this.indexBlocksMap = indexBlocksMap;
    }

    public String getJarDirectory() {
        return jarDirectory;
    }

    public void setJarDirectory(String jarDirectory) {
        this.jarDirectory = jarDirectory;
    }

    public Set<Integer> getStartBlocksSet() {
        return startBlocksSet;
    }

    public void setStartBlocksSet(Set<Integer> startBlocksSet) {
        this.startBlocksSet = startBlocksSet;
    }
}

