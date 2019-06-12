package cz.zcu.kiv.WorkflowDesigner;


import cz.zcu.kiv.WorkflowDesigner.Annotations.BlockType;
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

import static cz.zcu.kiv.WorkflowDesigner.ContinuousBlock.PENDING;
import static cz.zcu.kiv.WorkflowDesigner.ContinuousBlock.PROCESSING;
import static cz.zcu.kiv.WorkflowDesigner.ContinuousBlock.END;

/***********************************************************************************************************************
 *
 * ContinuousWorkflow, GSoC 2019, 2019/05/06 Yijie Huang
 * Part of the methods are cited from the GSoC 2018 Workflow.java coded by Joey Pinto
 *
 * This file hosts the methods used to dynamically create the Javascript files needed for the continuousWork designer,
 * and also to start the threadPool and assign Block Execution for continuous data in parallel.
 **********************************************************************************************************************/

public class ContinuousWorkflow {

    private static Log logger = LogFactory.getLog(ContinuousWorkflow.class);

    private String jarDirectory;
    private String remoteDirectory;
    private ClassLoader classLoader;
    private Map<Class,String> moduleSource;
    private String module;
    private List<ContinuousBlock> blockDefinitions = null;


    private static final int QUEUE_SIZE = 50; //task queue size


    public ContinuousWorkflow(ClassLoader classLoader, Map<Class, String> moduleSource, String jarDirectory, String remoteDirectory) {
        this.jarDirectory = jarDirectory;
        this.remoteDirectory = remoteDirectory;
        this.classLoader = classLoader;
        this.moduleSource = moduleSource;
    }

    public ContinuousWorkflow(ClassLoader classLoader, String module, String jarDirectory, String remoteDirectory) {
        this.jarDirectory = jarDirectory;
        this.remoteDirectory = remoteDirectory;
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
        for(ContinuousBlock block:getBlockDefinitions()){
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
    private List<ContinuousBlock> getBlockDefinitions() throws IllegalAccessException, InstantiationException, NoSuchMethodException, InvocationTargetException {
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
            //Instantiate ContinuousBlock
            ContinuousBlock block= new ContinuousBlock(blockType.newInstance(),this);
            Annotation annotation = blockType.getAnnotation(BlockType.class);
            Class<? extends Annotation> type = annotation.annotationType();

            //Load information from annotations
            String blockTypeName = (String)type.getDeclaredMethod("type").invoke(annotation, (Object[])null);
            String blockTypeFamily = (String)type.getDeclaredMethod("family").invoke(annotation, (Object[])null);
            Boolean jarExecutable = (Boolean) type.getDeclaredMethod("runAsJar").invoke(annotation, (Object[])null);
            //Boolean rmiExecutable = (Boolean) type.getDeclaredMethod("runByRMI").invoke(annotation, (Object[])null);
            String description = (String)type.getDeclaredMethod("description").invoke(annotation, (Object[])null);
            block.setName(blockTypeName);
            block.setFamily(blockTypeFamily);
            block.setJarExecutable(jarExecutable);
            //block.setRmiExecutable(rmiExecutable);
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
     * @return ContinuousBlock attributes
     */
    public ContinuousBlock getDefinition(String name) throws InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException {
        for(ContinuousBlock block:getBlockDefinitions()){
            if(block.getName().equals(name)){
                return block;
            }
        }
        return null;
    }


    /**
     * indexBlocks - Joey Pinto
     * Index ContinuousBlocks from JSONArray to Map
     * @param blocksArray
     * @return  Map of ContinuousBlocks indexed with block ids
     */
    public Map<Integer, ContinuousBlock> indexBlocks(JSONArray blocksArray) throws IllegalAccessException, NoSuchMethodException, InvocationTargetException, InstantiationException, FieldMismatchException {
        Map<Integer,ContinuousBlock> blocks=new HashMap<>();
        for(int i=0; i<blocksArray.length(); i++){

            JSONObject blockObject=blocksArray.getJSONObject(i);
            ContinuousBlock block = null;

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
                    block = new ContinuousBlock(blockType.newInstance(),this);
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
     * populateWaitList
     * It is modified based on the GSoC 2018 populateWaitList by Joey Pinto
     *
     * 1. Populate wait list of blocks that are unprocessed
     *
     * 2. check whether the block is no longer accept data(input/property) anymore
     *    and set the ContinuousBlock status according to the situation
     *
     * and their source blocks are already processed or they only have properties
     * @param edgesArray - JSON from frontend containing connected edges
     * @param blocks - Map of block ID to block object
     * @return
     */
    public int populateWaitList(List<Integer> wait, JSONArray edgesArray, Map<Integer, ContinuousBlock> blocks) {
        logger.info("                                                                                      Begin populateWaitList: ");
        int endCount = 0;

        for(Integer currBlockId : blocks.keySet()){
            ContinuousBlock current = blocks.get(currBlockId);

            //update Block Flags: whether all its destination fetch its previous output
            current.outputDataFetched(currBlockId);
//            if(current.outputDataFetched()){
//                current.setOutputPrepared(false);
//                current.setSentDataCount(0);
//            }
            logger.info("                                                                                         # In populateWaitList, currentBlock is "+current.getName()+", BlockID = "+currBlockId+", Status = "+current.getBlockStatus());

            if(current.getBlockStatus() == PROCESSING)
                continue;
            if( current.getBlockStatus() == END ) {
                endCount++;
                continue;
            }

            //if ContinuousBlock only has properties and no inputs
            if( (current.getInput()==null||current.getInput().isEmpty())  &&  current.getBlockStatus() == PENDING ){
                current.checkComingPropertyPrepared();

                if( !current.isComingPropertyPrepared() ){
                    current.setBlockStatus(END);
                    endCount++;
                    logger.info("                                                                                         (2） SET current(No Inputs) = "+current.getName()+", ID = "+currBlockId + " to 【 END 】, endCount = "+ endCount);

                }
                if( current.isComingPropertyPrepared() && !current.isOutputPrepared() ){
                    wait.add(currBlockId);
                }
                continue;
            }

            //if ContinuousBlock has both properties and inputs
            boolean readyFlag = true;
            boolean endFlag = true;
            for(int i = 0; i < edgesArray.length(); i++) {

                JSONObject edgeObject = edgesArray.getJSONObject(i);
                int block1Id=edgeObject.getInt("block1");
                int block2Id=edgeObject.getInt("block2");
                ContinuousBlock block1 = blocks.get(block1Id);
                ContinuousBlock block2 = blocks.get(block2Id);

                if(currBlockId != block2Id) continue;

                //update Block1 Flags: whether sourceBlock still has output prepared or already fetched
                block1.outputDataFetched(block1Id);
                //For blocks has Source Blocks, no need to consider their coming  properties, they will end when they cannot receive their source Blocks' data and source Blocks in END status
                if( block2.getBlockStatus() == PROCESSING  ||  block1.getBlockStatus() != END  ||  block1.isOutputPrepared()){
                    endFlag = false;
                }

                if( !block1.isOutputPrepared() || block2.getBlockStatus() != PENDING || block2.isOutputPrepared() ){
                    readyFlag = false;
                    break;
                }
            }
            if(readyFlag){
                wait.add(currBlockId);
            }
            if(endFlag){
                current.setBlockStatus(END);
                endCount++;
                logger.info("                                                                                         (2） SET  current(block2) = "+current.getName()+", Block2ID = "+currBlockId + ", to【 END 】, endCount = "+ endCount);
            }
        }
        logger.info("Wait list has "+wait.size()+" blocks");

        return endCount;
    }


    /**
     * assignConnectedCount - Yijie Huang
     *
     * loop the edges to check the number of destination Blocks that every Block is connects with
     */
    public void assignConnectedCount(JSONArray edgesArray, Map<Integer, ContinuousBlock> blocks){

        for(int currBlockId: blocks.keySet()){

            int connectedCount = 0;
            ContinuousBlock currentBlock = blocks.get(currBlockId);

            for(int i = 0; i < edgesArray.length(); i++){
                JSONObject edgeObject = edgesArray.getJSONObject(i);
                int block1Id = edgeObject.getInt("block1");

                if(currBlockId != block1Id) continue;

                connectedCount++;
            }

            currentBlock.setConnectedCount(connectedCount);
            logger.info("                                                                                         @ Block "+currentBlock.getName() + ", BlockID "+currBlockId+", has " +connectedCount+ " next blocks");
        }
    }



    /**
     * execute - Yijie Huang
     *
     * process a continuous data workflow with parallel block execution, based on GSoC 2018 project Joey Pinto
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
        JSONArray edgesArray  = jObject.getJSONArray("edges");
        Map<Integer, ContinuousBlock> blocks = indexBlocks(blocksArray);
        assignConnectedCount(edgesArray, blocks);//assign all the Blocks'conectedCount

        logger.info("…………………………………………………………………………………………………………………………………………………………………………  Start a Thread Pool:  ………………………………………………………………………………………………………………………………………………………………………………………………………………………………………………………… ");
        int poolSize = blocks.size();
        ThreadPoolExecutor workFlowThreadPool = new ThreadPoolExecutor(poolSize, poolSize, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>(QUEUE_SIZE), new ThreadPoolExecutor.AbortPolicy());

        //TODO: maybe latter can be changed to CompletableFuture or not
        CompletionService<Boolean> blockTasksService = new ExecutorCompletionService<>(workFlowThreadPool);
        List<Future<Boolean>> futuresList = new ArrayList<>();

        boolean error=false;
        while(!error){

            //Populate wait list
            List<Integer> wait = new ArrayList<>();
            int endCount = populateWaitList(wait, edgesArray, blocks);

            //Exit Loop FLAG 1: All the Blocks in the JSONArray are in the END status, then the pool no need to submit execution thread task any more
            if(endCount == blocks.size()) break;

            //Wait queue is empty: when wait is empty, the populateWaitList maybe called many times and always generate 0 wait Block at that period, since some blocks in the PROCESSING status may take long time to do
            if(wait.size()==0) continue;

            for(Integer aWait : wait){
                int waitBlockId = aWait;
                logger.info("                                                                                         … … … … … … … … … … … … … … … … … … … … … Submit a new thread Task to the Pool for [ blockID = "+waitBlockId +" ] … … … … … … … … … … … … … … … … … … … … … … … … … … … …");

                ContinuousBlock waitBlock = blocks.get(waitBlockId);
                waitBlock.setBlockStatus(PROCESSING);
                logger.info("                                                                                         (1） SET blockName = "+waitBlock.getName()+", ID = "+waitBlockId+" to 【 PROCESSING 】");
                // new a Block Execution task and submit it
                ContinuousBlockTask blockTask = new ContinuousBlockTask (waitBlockId, waitBlock, blocks, this, jObject, outputFolder, workflowOutputFile);

                blockTasksService.submit(blockTask);
                //futuresList.add(errorFlag);
            }

            //Exit Loop FLAG 2： Block Execution throws exception, no need to do the following tasks
            error = blockTasksService.take().get();
        }

        logger.info("……………………………………………………………………………………………………… Wait all the current submitted threads finish and then shutdown the Thread Pool: ……………………………………………………………………………………………………………………………………………………………………………………… ");
        workFlowThreadPool.shutdown();
        boolean loop;
        do {
            loop = !workFlowThreadPool.awaitTermination(2, TimeUnit.SECONDS);
        } while(loop);
        logger.info("……………………………………………………………………………………………………  ShutDown the Thread Pool successfully. Close all the taskThreads of this workFlow. …………………………………………………………………………………………………………………………………………………………………………………");

        if(!error)  logger.info("Workflow Execution completed successfully!");
        else        logger.error("Workflow Execution failed!");
        return blocksArray;
    }


    /**
     * populateDependencies - Joey Pinto
     *
     * Populate the dependency maps of a block
     *
     * @param block1Id - ID of block to populate dependencies
     * @param block1 - ContinuousBlock object
     * @param edgeObject - JSON containg edge definitions
     * @param dependencies - Map of Blocks that are dependencies
     * @param fields - Input field Annotations
     */
    public void populateDependencies(int block1Id, ContinuousBlock block1, JSONObject edgeObject, Map<Integer,ContinuousBlock> dependencies, Map<String,InputField> fields) {
        JSONArray connector1 = edgeObject.getJSONArray("connector1");
        JSONArray connector2 = edgeObject.getJSONArray("connector2");

        //each time we get for example Map<"Operand1", InputField>fields
        //InputField=> List<String> SourceParam = ["Operand"], List<Integer>sourceBlock = [1], String destinationParam = "Operand1";
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
     * Get a block from the JSON workflow by id - Joey Pinto
     *
     * @param blocksArray - JSON workflow
     * @param waitBlockId - ContinuousBlock ID
     * @return
     */
    public static JSONObject getBlockById(JSONArray blocksArray, int waitBlockId){
        for(int i=0;i<blocksArray.length();i++){
            JSONObject block=blocksArray.getJSONObject(i);
            if(block.getInt("id") == waitBlockId){
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
