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
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ContinuousWorkFlow {

    private static Log logger = LogFactory.getLog(ContinuousWorkFlow.class);

    private String jarDirectory;
    private String remoteDirectory; //File location where the user upload their file waiting to be execute
    private ClassLoader classLoader;
    private Map<Class,String> moduleSource; //initialize workflow
    private String module; //initialize front-end blocks tree

    private List<ContinuousBlock> blockDefinitions;// all the blocks from one module only used for front end

    private Map<Integer, ContinuousBlock> indexBlocksMap;
    private boolean[] errorFlag = new boolean[1]; //denote whether the whole workFlow completed successfully or not

    /**
     * Constructor for building BlockTrees for front-End  -- (Front-End call: initializeBlocks)
     */
    public ContinuousWorkFlow(ClassLoader classLoader,  String module, String jarDirectory, String remoteDirectory) {
        this.remoteDirectory = remoteDirectory;
        this.classLoader = classLoader;
        this.module = module;
        this.jarDirectory = jarDirectory;
    }

    /**
     * Constructor to execute the Continuous WorkFlow -- (Front-End call: execute)
     */
    public ContinuousWorkFlow(ClassLoader classLoader, Map<Class, String> moduleSource, String jarDirectory, String remoteDirectory) {
        this.remoteDirectory = remoteDirectory;
        this.classLoader = classLoader;
        this.moduleSource = moduleSource;
        this.jarDirectory = jarDirectory;
    }


    /**
     * initializeBlocks
     * prepare JSON for the Front end to form the blocks tree
     *
     */
    public JSONArray initializeBlocks() throws InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException {
        initializeBlockDefinitions();
        JSONArray blocksArray=new JSONArray();
        for(ContinuousBlock block : this.blockDefinitions){
            //Write JS file description of block to array
            blocksArray.put(block.toJSON());
        }
        logger.info("Initialized "+blocksArray.length()+" blocks");
        return blocksArray;
    }

    public void initializeBlockDefinitions() throws InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException {
        if(blockDefinitions !=null) return;

        List<ContinuousBlock> blocksList = new ArrayList<>();
        Set<Class<?>> blockClasses = new Reflections(module.split(":")[1], classLoader).getTypesAnnotatedWith(BlockType.class);
        for(Class blockClass : blockClasses){

            ContinuousBlock currBlock = createBlockInstance(blockClass, module);
            currBlock.initializeIO();
            blocksList.add(currBlock);

        }
        setBlockDefinitions(blocksList);
    }

    private ContinuousBlock createBlockInstance(Class blockClass, String moduleStr) throws InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException {
        ContinuousBlock currBlock = new ContinuousBlock(blockClass.newInstance(), this);

        Annotation annotation = blockClass.getAnnotation(BlockType.class);
        Class<? extends Annotation> blockType = annotation.annotationType();

        String blockTypeName = (String)blockType.getDeclaredMethod("type").invoke(annotation);
        String blockTypeFamily = (String)blockType.getDeclaredMethod("family").invoke(annotation);
        String description = (String)blockType.getDeclaredMethod("description").invoke(annotation);
        Boolean jarExecutable = (Boolean) blockType.getDeclaredMethod("runAsJar").invoke(annotation);

        currBlock.setName(blockTypeName);
        currBlock.setFamily(blockTypeFamily);
        currBlock.setModule(moduleStr);
        currBlock.setDescription(description);
        currBlock.setJarExecutable(jarExecutable);

        return currBlock;
    }

        /**
         * execute
         * @param jObject               SONObject contains Blocks and Edges info
         * @param outputFolder          Folder to save the output File
         * @param workflowOutputFile    File workflowOutputFile = File.createTempFile("job_"+getId(),".json",new File(WORKING_DIRECTORY));
         *                                  -- > File to put the Blocks JSONArray info with the output info, stdout, stderr, error info after the execution
         */
    public JSONArray execute(JSONObject jObject, String outputFolder, String workflowOutputFile) throws IOException, NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException, FieldMismatchException, InterruptedException {
        logger.info(" Start Continuous WorkFlow Execution …… ");

        JSONArray blocksArray = jObject.getJSONArray("blocks");
        JSONArray edgesArray  = jObject.getJSONArray("edges");

        //initialize  and  set  map<ContinuousBlockID, ContinuousBlock> indexBlocksMap(config I/Os and assign properties)
        mapIndexBlock(blocksArray);

        //initialize IO map
        mapBlocksIO(edgesArray);


        logger.info("…………………………………………………………………………………………………………………………………………………………………………  Start a Thread Pool:  ………………………………………………………………………………………………………………………………………………………………………………………………………………………………………………………… ");
        errorFlag[0] = false;
        int poolSize  = blocksArray.length();
        int queueSize = blocksArray.length();
        ThreadPoolExecutor workFlowThreadPool = new ThreadPoolExecutor(poolSize, poolSize, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>(queueSize), new ThreadPoolExecutor.AbortPolicy());
        for(int i = 0; i<blocksArray.length(); i++){
            JSONObject blockObject = blocksArray.getJSONObject(i);
            int blockID = blockObject.getInt("id");
            ContinuousBlock currBlock = indexBlocksMap.get(blockID);
            ContinuousTask blockExecuteTask = new ContinuousTask(blockID, currBlock, errorFlag, blockObject, outputFolder);
            workFlowThreadPool.submit(blockExecuteTask);
        }
        workFlowThreadPool.shutdown();
        boolean loop;
        do {
            loop = !workFlowThreadPool.awaitTermination(2, TimeUnit.SECONDS);
        } while(loop);
        logger.info("……………………………………………………………………………………………………  ShutDown the Thread Pool successfully. Close all the taskThreads of this workFlow. …………………………………………………………………………………………………………………………………………………………… ");


        //Save Present JSON (with outputs, errors) to the original file
        if(workflowOutputFile!=null){
            File workflowOutput=new File(workflowOutputFile);
            FileUtils.writeStringToFile(workflowOutput, blocksArray.toString(4), Charset.defaultCharset());
        }

        if(!errorFlag[0])
            logger.info( "Workflow Execution completed successfully!");
        else
            logger.error("Workflow Execution failed!");

        return blocksArray;
    }


    /**
     * mapBlockIndex
     * the same functionality of the method: indexBlocks - Joey Pinto 2018
     *
     * map all the ContinuousBlocks related in this workflow according to the front-end blocks JSONArray
     * initialize Map<Integer, ContinuousBlock> indexMap, and initialize all the properties
     */
    public void mapIndexBlock(JSONArray blocksArray) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException, FieldMismatchException {
        logger.info("initialize all the related ContinuousBlocks(including I/O/properties initialization) in this workFlow and set the idBlocksMap");
        Map<Integer, ContinuousBlock> idBlocksMap = new HashMap<>();

        for(int i = 0; i<blocksArray.length(); i++){
            ContinuousBlock currBlock = null;

            JSONObject blockObject = blocksArray.getJSONObject(i);
            String blockTypeStr = blockObject.getString("type");
            int id = blockObject.getInt("id");
            String module = blockObject.getString("module");

            // get class from Constructor:  Map<Class, String> moduleSource,
            // when execute, moduleSource map is initialized not module string
            Set<Class> blockClasses = moduleSource.keySet();
            for(Class blockClass : blockClasses){

                Annotation annotation = blockClass.getAnnotation(BlockType.class);
                Class<? extends Annotation> blockType = annotation.annotationType();
                String blockTypeName = (String)blockType.getDeclaredMethod("type").invoke(annotation);

                if(blockTypeName.equals(blockTypeStr)){
                    currBlock = createBlockInstance(blockClass, module);
                    currBlock.setId(id);
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

            idBlocksMap.put(id, currBlock);
        }
        setIndexBlocksMap(idBlocksMap);
    }


    /**
     * mapBlocksIO
     * set Map<String, List<SourceOutput></SourceOutput>> IOMap for each Blocks according to the JSONArray edgeArray;
     *
     */
    public void mapBlocksIO(JSONArray edgesArray){
        logger.info("Set IOMap for each destination Blocks ");

        for(int i = 0; i<edgesArray.length(); i++){
            JSONObject edge = edgesArray.getJSONObject(i);

            int block1ID = edge.getInt("block1");
            ContinuousBlock block1 = indexBlocksMap.get(block1ID);
            // ContinuousBlock block1 = indexBlocksMap.get(block1ID);
            String sourceParam = edge.getJSONArray("connector1").getString(0);
            SourceOutput sourceOutput = new SourceOutput(block1ID, block1, sourceParam);

            int block2ID = edge.getInt("block2");
            ContinuousBlock block2 = this.indexBlocksMap.get(block2ID);
            String destinationParam = edge.getJSONArray("connector2").getString(0);

            Map<String, List<SourceOutput>> IOMap =block2.getIOMap();
            if(!IOMap.containsKey(destinationParam))
                IOMap.put(destinationParam, new ArrayList<SourceOutput>());

            IOMap.get(destinationParam).add(sourceOutput);
            block2.setIOMap(IOMap);
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

    public List<ContinuousBlock> getBlockDefinitions() {
        return blockDefinitions;
    }

    public void setBlockDefinitions(List<ContinuousBlock> blockDefinitions) {
        this.blockDefinitions = blockDefinitions;
    }

    public Map<Integer, ContinuousBlock> getIndexBlocksMap() {
        return indexBlocksMap;
    }

    public void setIndexBlocksMap(Map<Integer, ContinuousBlock> indexBlocksMap) {
        this.indexBlocksMap = indexBlocksMap;
    }

    public String getJarDirectory() {
        return jarDirectory;
    }

    public void setJarDirectory(String jarDirectory) {
        this.jarDirectory = jarDirectory;
    }
}
