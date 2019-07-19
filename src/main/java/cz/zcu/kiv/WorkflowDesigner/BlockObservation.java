package cz.zcu.kiv.WorkflowDesigner;

import cz.zcu.kiv.WorkflowDesigner.Annotations.*;
import cz.zcu.kiv.WorkflowDesigner.Visualizations.PlotlyGraphs.Graph;
import cz.zcu.kiv.WorkflowDesigner.Visualizations.Table;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.SerializationUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONArray;
import org.json.JSONObject;
import org.reflections.Reflections;

import java.io.*;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.net.MalformedURLException;
import java.nio.charset.Charset;
import java.rmi.Naming;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;

import static cz.zcu.kiv.WorkflowDesigner.Type.STREAM;

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
 * Block, 2018/16/05 13:32 Joey Pinto
 * BlockObservation, 2019 GSoC P1 P2 Yijie Huang
 *
 * In 2018, This file is the model for a single block in the workflow designer tool.
 * In 2019, it implements Observer Pattern design, making destination block execute
 * once they observer all its source blocks execute successfully.
 **********************************************************************************************************************/

public class BlockObservation extends Observable implements Observer, Runnable {

    private static Log logger = LogFactory.getLog(BlockObservation.class);

    //Fields used to initialize front end Block Tree imgs
    private String name;
    private String family;
    private String module;
    private String description;
    private Map<String, Data>       inputs;
    private Map<String, Data>       outputs;
    private Map<String, Property>   properties;
    private boolean jarExecutable;


    //Fields used to denote a BlockObservation in the workFlow relationship
    private int id;
    private Object context;    //Actual Object instance maintained as context
    private BlockWorkFlow blockWorkFlow;
    private Map<String, List<BlockSourceOutput>> IOMap;
    private Object finalOutputObject = null;
    private boolean complete;
    private boolean stream = true; //set false if one of the inputs of this block is not stream in connectIO() method


    //Fields used for observer and observable
    private List<BlockObservation> destinationObservers = new ArrayList<>();
    private List<BlockObservation> sourceObservables = new ArrayList<>();
    private int observablesCount = 0;
    private JSONObject blockObject;
    private String outputFolder;
    private boolean[] errorFlag = new boolean[1];
    private int[] count = new int[1];
    private final JSONArray blocksArray;
    private String workflowOutputFile;

    //Fields for RMI
    private boolean rmiFlag;
    private BlockData blockData;
    private IRemoteData remoteDataImpleServer;


    //Fields for continuous stream model (no cumulative data in blocks) (pipe)
    private PipedOutputStream[] pipedOuts;
    private PipedInputStream[] pipedInsTransit;
    private PipedOutputStream[] pipedOutsTransit;
    private PipedInputStream[] pipedIns;
    private Map<String, PipedOutputStream>   inTransitsMap;            //store Map<inputName(unique annotation name):  corresponding pipedOutsTransit     (connect this block's input's PipedInTransit)>
    private Map<String, PipedInputStream>    outTransitReadMap;        //store Map<outputName(unique annotation name): corresponding pipedInsTransit      (connect this block's output's PipedInTransit)>
    private Map<String, List<PipedOutputStream>> outTransitWriteMap;   //store Map<outputName(unique annotation name): corresponding pipedOutsTransitList (connect next blocks' input's PipeOutTransit)>


    //var for workFlow jobID: same workflow same jobID
    private long jobID;


    public BlockObservation(Object context, BlockWorkFlow blockWorkFlow, JSONArray blocksArray, String workflowOutputFile) {
        this.context = context;
        this.blockWorkFlow = blockWorkFlow;
        this.blocksArray = blocksArray;
        this.workflowOutputFile = workflowOutputFile;
    }


    /**
     * assignPipeTransit - Yijie Huang
     * assign the PipedOutputStream and PipedInputStream;
     * and Map<String, PipedOutputStream>   inTransitsMap;
     * Map<String, PipedInputStream>    outTransitReadMap
     */
    public void assignPipeTransit() throws IllegalAccessException, IOException {

        int outNum = 0, inNum = 0;

        if( outputs != null && outputs.size() != 0) {
            outNum = outputs.size();
            pipedOuts       = new PipedOutputStream[outNum];
            pipedInsTransit = new PipedInputStream[outNum];
        }

        if( inputs != null  && inputs.size() != 0) {
            inNum = inputs.size();
            pipedOutsTransit = new PipedOutputStream[inNum];
            pipedIns         = new PipedInputStream[inNum];
        }

        // connect original outputs pipedOutputStream and pipedInputTransits
        if(outNum != 0){
            outTransitReadMap  = new HashMap<>();
            outTransitWriteMap = new HashMap<>();  //empty now, entity will be put in BlockWorkFlow.java

            int i = 0;
            Field[] outputFields = context.getClass().getDeclaredFields();
            for (Field f : outputFields) {
                f.setAccessible(true);

                BlockOutput blockOutput = f.getAnnotation(BlockOutput.class);
                if (blockOutput != null) {

                    pipedOuts[i] = (PipedOutputStream) f.get(context);
                    pipedInsTransit[i] = new PipedInputStream();
                    pipedOuts[i].connect(pipedInsTransit[i]);

                    outTransitReadMap.put(blockOutput.name(), pipedInsTransit[i]);

                    i++;

                }
            }
        }

        // connect pipedOutputTransits and original inputs pipedInputStream
        if(inNum != 0){
            inTransitsMap = new HashMap<>();
            int j = 0;
            Field[] inputFields =  context.getClass().getDeclaredFields();
            for (Field f : inputFields) {
                f.setAccessible(true);

                BlockInput blockInput = f.getAnnotation(BlockInput.class);
                if (blockInput != null) {

                    pipedOutsTransit[j] = new PipedOutputStream();
                    pipedIns[j] = (PipedInputStream) f.get(context);
                    pipedOutsTransit[j].connect(pipedIns[j]);

                    inTransitsMap.put(blockInput.name(), pipedOutsTransit[j]);

                    j++;

                }
            }
        }


    }


    /**
     * run - Yijie Huang
     * Blocks connect IO and execute in thread
     */
    @Override
    public void run() {
        logger.info(" Start thread run for  —— id = "+getId()+", name = "+getName()+":  count[0] = "+count[0]+", in jobID "+jobID);

        boolean error = false;
        StringBuilder stdErr = new StringBuilder();
        StringBuilder stdOut = new StringBuilder();
        try{
            this.connectIO();// connect IO and assign blockdata
            finalOutputObject = this.blockExecute(stdOut, stdErr);

        } catch (Exception e){
            logger.error("Error executing id = "+ getId()+", name = "+ getName()+" Block"+", in jobID "+jobID, e);
            error = true;
            synchronized (errorFlag){ errorFlag[0] = true; }
        }

        try {
            updateJSON(error, stdErr.toString(), stdOut.toString());
        }catch (IOException e){
            logger.error("Error update JSON File of id = "+ getId()+", name = "+ getName()+" Block"+", in jobID "+jobID, e);
        }

        synchronized (count){  count[0]--; }


    }



    /**
     * update - Yijie Huang
     *
     * Destination blocks(Observers) will update the observablesCount,
     * once they receive notification from their source blocks(Observables).
     *
     * And when the observablesCount is equal to the number of their sourceBlocks,
     * destination blocks start their thread to execute.
     */
    @Override
    public void update(Observable o, Object arg) {
        logger.info("Observer Id = "+ getId()+", receives the notification from its Observable "+((BlockObservation) o).id+", in jobID "+jobID);
        //—————————— for observers  (  destination blocks )  ————update—————————
        observablesCount ++;

        if(observablesCount == sourceObservables.size()){
            if(errorFlag[0]) return;

            logger.info(" —————— Observation update ready for block id = "+getId()+", name = "+getName()+" —————— "+", in jobID "+jobID);
            Thread myExecuteThread = new Thread(this);
            myExecuteThread.start();

        }
    }

    /**
     * blockExecute - Joey Pinto, Yijie Huang
     *
     * Execute a block and return value returned by @BlockExecute block,
     * Once finish executing(no matter successfully or failed),
     * it(Observable) will notify all its destination blocks(observers).
     *
     * @param stdOut Standard Output stream builder
     * @param stdErr Error Stream builder
     * @return Object returned by @BlockExecute method
     * @throws Exception
     */
    public Object blockExecute(StringBuilder stdOut, StringBuilder stdErr) throws Exception {
        logger.info("Executing block id = "+ getId() +", name = "+getName()+", in jobID "+jobID);

        Object output;

        if(isJarExecutable() && blockWorkFlow.getJarDirectory()!=null && !stream){
            //Execute block as an external JAR file for normal data
            output = executeAsJar(stdOut, stdErr);
        }
        else{
            try {
                //Execute block natively in the current class loader for stream data
                output = executeInNative();
            }
            catch (Exception e){
                e.printStackTrace();
                stdErr.append(ExceptionUtils.getRootCauseMessage(e)+" \n");
                for(String trace:ExceptionUtils.getRootCauseStackTrace(e)){
                    stdErr.append(trace+" \n");
                }
                logger.error("Error executing id"+id+" "+name+" Block natively"+", in jobID "+jobID,e);
                throw e;
            }
        }

        setFinalOutputObject(output);
        setComplete(true);

        //—————————— for observables  (  source blocks )  ———— notify —————————
        // observable tells all its observers that it has finished execute method
        // and after notify all, just delete all its observers
        setChanged();
        notifyObservers();
        this.deleteObservers();

        logger.info("Execution block id = "+ getId() +", name = "+getName()+ " block completed successfully, now notify its Observers"+", in jobID "+jobID);
        return output;
    }


    /**
     * executeInNative() - Joey Pinto
     * block execute natively(without execute Jar)
     */
    public Object executeInNative() throws IllegalAccessException, InvocationTargetException {
        logger.info("Executing id-"+getId()+", name-"+getName()+" in Native."+", in jobID "+jobID);

        Object output = null;
        for(Method method : this.context.getClass().getDeclaredMethods()){
            method.setAccessible(true);
            if(method.getAnnotation(BlockExecute.class)!=null){
                output =  method.invoke(this.context);
                break;
            }
        }

        return output;
    }

    /**
     *  prepareRMI() - Yijie Huang
     *  return Remote object
     */
    public Remote prepareRMI(int port) throws RemoteException, MalformedURLException {
        Remote remoteObj = null;
        // set this BlockData to the server remote Implementation class
        // register communication port and path

        remoteDataImpleServer = new RemoteDataImple();
        String blockIdName = "["+id + " " + name +"] properties and inputs, in prepareRMI from rmiServer";
        remoteDataImpleServer.setBlockData(blockData, blockIdName);

        // use the rmi registry command to create and start a remote object registry on the specified port on the current host.
        remoteObj = LocateRegistry.createRegistry(port);

        String url = "rmi://localhost:" + port + "/IRemoteData";
        Naming.rebind(url, remoteDataImpleServer); //  rebind the designated remote object
        System.out.println("Server registers in port "+ port+",  block "+getId()+", "+getName());

        return remoteObj;
    }

    /**
     * generatePort()
     * create unique port id used in rmi
     */
    private int generatePort(){
        int port = (int)(jobID%1024)*10 + 1024 + id*100;
        if(port == 8680)
            return port + id*100;
        return port;
    }

    /**
     * Execute block externally as a JAR - Joey Pinto
     * transfer data through RMI or FILE
     * @param stdOut Standard Output Stream
     * @param stdErr Standard Error Stream
     * @return output returned by BlockExecute Method
     * @throws Exception when output file is not created
     */
    private Object executeAsJar(StringBuilder stdOut, StringBuilder stdErr) throws Exception {
        logger.info("Executing id = " + getId() + ", name = " + getName() + " as a JAR." + ", in jobID " + jobID);

        Object output = null;
        String fileName = "jobID_" + jobID + "_bID_" + id + "_" + new Date().getTime() + "_";
        Remote remoteObj = null;        //for RMI
        File inputFile   = null;        //for FILE
        File outputFile = null;         //for FILE

        //get jarFile.getAbsolutePath()
        File jarDirectory = new File(blockWorkFlow.getJarDirectory());
        jarDirectory.mkdirs();
        String jarFilePath = jarDirectory.getAbsolutePath() + File.separator + getModule().split(":")[0];
        File jarFile = new File(jarFilePath);

        //Calling jar file externally with entry point at the Block's main method
        String defaultVmArgs = "-Xmx1G";
        String vmargs = System.getProperty("workflow.designer.vm.args");
        vmargs = vmargs != null ? vmargs : defaultVmArgs;
        String[] args;
        String blockIdName = id + " " + name;

        try {
            // execute as jar and fetch blockData through RMI, otherwise through FILE
            if (rmiFlag) {

                int port = generatePort();//set an unique port number
                remoteObj = this.prepareRMI(port);

                args = new String[]{"java", vmargs, "-cp", jarFile.getAbsolutePath(), "cz.zcu.kiv.WorkflowDesigner.BlockObservation", blockIdName,    String.valueOf(port),   getModule().split(":")[1],   "RMI"};
            }
            //if transfer data through FILE instead of RMI
            else {
                File inputFileTmp = File.createTempFile(fileName, ".in", new File(blockWorkFlow.getJarDirectory()));
                File outputFileTmp = File.createTempFile(fileName, ".out", new File(blockWorkFlow.getJarDirectory()));
                inputFile = new File(inputFileTmp.getAbsolutePath());
                outputFile = new File(outputFileTmp.getAbsolutePath());

                //Serialize and write BlockData object to a file
                FileOutputStream fos = new FileOutputStream(inputFile);
                ObjectOutputStream oos = new ObjectOutputStream(fos);
                oos.writeObject(blockData);
                oos.close();

                args = new String[]{"java", vmargs, "-cp", jarFile.getAbsolutePath(), "cz.zcu.kiv.WorkflowDesigner.BlockObservation", inputFile.getAbsolutePath(), outputFile.getAbsolutePath(), getModule().split(":")[1], "FILE"};
            }

            logger.info("Passing arguments" + Arrays.toString(args) + ", in jobID " + jobID);
            ProcessBuilder pb = new ProcessBuilder(args);
            //Store output and error streams to files
            logger.info("Executing jar file " + jarFilePath + ", in jobID " + jobID);
            File stdOutFile = new File("std_output.log");
            pb.redirectOutput(stdOutFile);
            File stdErrFile = new File("std_error.log");
            pb.redirectError(stdErrFile);
            Process ps = pb.start();
            ps.waitFor();
            stdOut.append(FileUtils.readFileToString(stdOutFile, Charset.defaultCharset()));
            String processErr = FileUtils.readFileToString(stdErrFile, Charset.defaultCharset());
            stdErr.append(processErr);
            InputStream is = ps.getErrorStream();
            byte[] b = new byte[is.available()];
            is.read(b, 0, b.length);
            String errorString = new String(b);
            if (!errorString.isEmpty()) {
                logger.error(errorString + ", in jobID " + jobID);
                stdErr.append(errorString);
            }
            is = ps.getInputStream();
            b = new byte[is.available()];
            is.read(b, 0, b.length);
            String outputString = new String(b);
            if (!outputString.isEmpty()) {
                logger.info(outputString + ", in jobID " + jobID);
                stdOut.append(outputString);
            }


            // execute as jar and fetch blockData through RMI, otherwise through FILE
            if (rmiFlag) {
                blockData = remoteDataImpleServer.getBlockData("["+blockIdName+"], for output, in executeAsJar from rmiServer");
                output = blockData.getProcessOutput();

                // release rmi port (send data)
                UnicastRemoteObject.unexportObject(remoteObj,true);
            }
            //if transfer data through FILE instead of RMI
            else{
                if (outputFile != null && outputFile.exists()) {
                    FileInputStream fis = new FileInputStream(outputFile);
                    ObjectInputStream ois = new ObjectInputStream(fis);
                    blockData = (BlockData) ois.readObject();
                    ois.close();
                    output = blockData.getProcessOutput();
                    FileUtils.deleteQuietly(outputFile);
                } else {
                    String err = "Output file does not exist for block " + id + " " + name + ", in jobID " + jobID +". ";
                    if (processErr != null && !processErr.isEmpty()) {
                        err += processErr;
                    }
                    throw new Exception(err);
                }
            }

            for (Field f : context.getClass().getDeclaredFields()) {
                f.setAccessible(true);
                BlockOutput blockOutput = f.getAnnotation(BlockOutput.class);
                if (blockOutput != null) {
                    f.set(context, blockData.getOutput().get(blockOutput.name()));
                }
            }

        }
        catch (Exception e) {
            e.printStackTrace();
            stdErr.append(org.apache.commons.lang3.exception.ExceptionUtils.getStackTrace(e));
            logger.error("Error executing Jar file for block " + id + " " + name + ", in jobID " + jobID, e);
            throw e;
        }

        return output;
    }




    /**
     * updateJSON - Joey Pinto
     * update the JSON file of "blocks"
     */
    public void updateJSON(boolean error, String stdErr, String stdOut) throws IOException {
        synchronized (blocksArray){

            logger.info("Update JSON for block "+getId()+", name = "+getName() +", in jobID "+jobID);

            blockObject.put("error", error);
            blockObject.put("stderr", stdErr);
            blockObject.put("stdout", stdOut);
            blockObject.put("completed", true);

            JSONObject JSONOutput = new JSONObject();
            if(finalOutputObject==null){
                JSONOutput = null;
            }  else if (finalOutputObject.getClass().equals(String.class)){

                JSONOutput.put("type","STRING");
                JSONOutput.put("value",finalOutputObject);

            }  else if (finalOutputObject.getClass().equals(File.class)){

                File file = (File) finalOutputObject;
                String destinationFileName="file_"+new Date().getTime()+"_"+file.getName();
                FileUtils.moveFile(file, new File(outputFolder + File.separator + destinationFileName));
                JSONOutput.put("type", "FILE");
                JSONObject fileObject = new JSONObject();
                fileObject.put("title", file.getName());
                fileObject.put("filename", destinationFileName);
                JSONOutput.put("value", fileObject);

            }else if (finalOutputObject.getClass().equals(Table.class)){

                Table table=(Table)finalOutputObject;
                JSONOutput.put("type", "TABLE");
                JSONOutput.put("value", table.toJSON());
                File file =File.createTempFile("temp_",".csv");
                FileUtils.writeStringToFile(file,table.toCSV(), Charset.defaultCharset());
                String destinationFileName = "table_" + new Date().getTime() + ".csv";
                FileUtils.moveFile(file, new File(outputFolder + File.separator + destinationFileName));
                JSONObject fileObject=new JSONObject();
                fileObject.put("title", destinationFileName);
                fileObject.put("filename", destinationFileName);
                JSONOutput.put("value", fileObject);

            }
            else if (finalOutputObject.getClass().equals(Graph.class)){

                Graph graph=(Graph)finalOutputObject;
                JSONOutput.put("type", "GRAPH");
                JSONOutput.put("value", graph.toJSON());
                File file =File.createTempFile("temp_",".json");
                FileUtils.writeStringToFile(file, graph.toJSON().toString(4), Charset.defaultCharset());
                String destinationFileName = "graph_"+ new Date().getTime() + ".json";
                FileUtils.moveFile(file, new File(outputFolder + File.separator + destinationFileName));
                JSONObject fileObject=new JSONObject();
                fileObject.put("title", destinationFileName);
                fileObject.put("filename", destinationFileName);
                JSONOutput.put("value", fileObject);

            }
            else{
                JSONOutput.put("type","");
                JSONOutput.put("value",finalOutputObject.toString());
            }

            if (JSONOutput != null)
                blockObject.put("output", JSONOutput);


            //Save Present JSON (with outputs, errors) to the original file
            logger.info("-Update blocksArray JSON in workflowOutputFile for block id = "+ getId() +", name = "+ getName()+", in jobID "+jobID);
            if(workflowOutputFile!=null){
                File workflowOutput = new File(workflowOutputFile);
                FileUtils.writeStringToFile(workflowOutput, blocksArray.toString(4), Charset.defaultCharset());
            }
        }

    }



    /**
     * connectIO() - Joey Pinto, Yijie Huang
     * connect the Input of this Block with all its SourceOutput of sourceBlocks and assign the variable blockdata used for serialize
     * by setting the value of the Input using reflection
     *
     */
    public void connectIO() throws IllegalAccessException {
        logger.info(" Connect all the Inputs of BlockObservation[ ID = "+this.id+", " +this.name+" ] with its SourceOutputs."+", in jobID "+jobID);
        blockData = new BlockData(getName());//for normal data


        //Assign properties specific to blockData serialized object
        for (Field f: context.getClass().getDeclaredFields()) {
            f.setAccessible(true);

            BlockProperty blockProperty = f.getAnnotation(BlockProperty.class);
            if (blockProperty != null) {
                if(blockProperty.type().equals(Type.FILE)){
                    blockData.getProperties().put(blockProperty.name(), new File(blockWorkFlow.getRemoteDirectory()+File.separator+f.get(context)));
                }
                else blockData.getProperties().put(blockProperty.name(), f.get(context));
            }
        }

        if(inputs == null || inputs.isEmpty()) return;

        //Map<Integer, BlockObservation> indexBlocksMap = blockWorkFlow.getIndexBlocksMap();
        for(String destinationParam : IOMap.keySet()){

            //get SourceBlocks if it is list[]
            List<BlockSourceOutput> sourceOutputs = IOMap.get(destinationParam);
            List<Object> components=new ArrayList<>();

            //get O
            for(BlockSourceOutput sourceOutput: sourceOutputs) {
                Object sourceOut = null;

                int sourceBlockID = sourceOutput.getSourceBlockID();
                String sourceParam = sourceOutput.getSourceParam();
                BlockObservation sourceBlock = sourceOutput.getBlockObservation();

                Field[] outputFields = sourceBlock.getContext().getClass().getDeclaredFields();
                for (Field f : outputFields) {
                    f.setAccessible(true);

                    BlockOutput blockOutput = f.getAnnotation(BlockOutput.class);
                    if (blockOutput != null) {
                        if (blockOutput.name().equals(sourceParam)) {
                            sourceOut = f.get(sourceBlock.getContext());
                            break;
                        }
                    }
                }
                components.add(sourceOut);
            }

            //get I
            Field[] inputFields = this.context.getClass().getDeclaredFields();
            for (Field f : inputFields) {
                f.setAccessible(true);

                BlockInput blockInput = f.getAnnotation(BlockInput.class);

                if (blockInput != null) {
                    if (blockInput.name().equals(destinationParam)) {
                        if(!blockInput.type().endsWith("[]")){ // input not comes from multiple outputs
                            f.set(this.context, components.get(0));
                            blockData.getInput().put(destinationParam, components.get(0));

                        } else {// input comes from multiple outputs
                            if(f.getType().isArray()){
                                throw new IllegalAccessException("Arrays not supported, Use Lists Instead");
                            }
                            f.set(context,f.getType().cast(components));
                            blockData.getInput().put(destinationParam, components);

                        }
                        break;
                    }
                }
            }
        }

    }




    /**
     * initializeIO() - Joey Pinto
     * Initialize a Block from a class
     */
    public void initializeIO(){

        if(getProperties()==null)
            setProperties(new HashMap<String,Property>());
        if(getInputs()==null)
            setInputs(new HashMap<String, Data>());
        if(getOutputs()==null)
            setOutputs(new HashMap<String, Data>());
        if(getIOMap()==null)
            setIOMap(new HashMap<String, List< BlockSourceOutput>>());

        for (Field f: context.getClass().getDeclaredFields()) {
            f.setAccessible(true);

            BlockProperty blockProperty = f.getAnnotation(BlockProperty.class);
            if (blockProperty != null){
                properties.put(blockProperty.name(),new Property(blockProperty.name(),blockProperty.type(),blockProperty.defaultValue(), blockProperty.description()));
            }

            BlockInput blockInput = f.getAnnotation(BlockInput.class);
            if (blockInput != null){

                if(!blockInput.type().equals(STREAM)){
                    this.setStream(false);
                }

                String cardinality="";
                if(blockInput.type().endsWith("[]")){
                    cardinality=WorkflowCardinality.MANY_TO_MANY;
                }
                else{
                    cardinality=WorkflowCardinality.ONE_TO_ONE;
                }
                inputs.put(blockInput.name(),new Data(blockInput.name(),blockInput.type(),cardinality));
            }

            BlockOutput blockOutput = f.getAnnotation(BlockOutput.class);
            if (blockOutput != null){
                String cardinality="";
                cardinality=WorkflowCardinality.MANY_TO_MANY;

                outputs.put(blockOutput.name(),new Data(blockOutput.name(),blockOutput.type(),cardinality));
            }
        }

        logger.info("Initialized I/O/properties of BlockID "+getId()+", name "+getName()+" block from annotations"+", in jobID "+jobID);

    }


    /**
     * assignProperties - Yijie Huang
     * reflection to set the block context instance 's properties field
     */
    public void assignProperties(JSONObject blockObject) throws IllegalAccessException{

        if(properties == null || properties.isEmpty()) return;
        logger.info("Assign the value of properties for block "+getId()+", "+getName()+", in jobID "+jobID);

        //"values": {
        //      "File": "/Users/xxxxx/Desktop/INCF/input.txt"
        // }

        JSONObject values = blockObject.getJSONObject("values");

        //Map properties to object parameters
        for(String key : this.properties.keySet()){
            if(values.has(key)){
                for (Field f: context.getClass().getDeclaredFields()) {
                    f.setAccessible(true);
                    BlockProperty blockProperty = f.getAnnotation(BlockProperty.class);
                    if (blockProperty != null) {
                        if(blockProperty.name().equals(key)){

                            //Assign object attributes from properties
                            if(blockProperty.type().endsWith("[]")){

                                //Dealing with List properties
                                if(f.getType().isArray()){
                                    //Unsupported by reflection
                                    throw new IllegalAccessException("Arrays Not supported, Use List instead");
                                }
                                List<Object> components = new ArrayList<>();
                                JSONArray array = values.getJSONArray(key);
                                ParameterizedType listType = (ParameterizedType) f.getGenericType();
                                Class<?> listClass = (Class<?>) listType.getActualTypeArguments()[0];
                                for(int i = 0; i < array.length(); i++){
                                    if(listClass.equals(File.class)){
                                        components.add(new File( blockWorkFlow.getRemoteDirectory() + File.separator + array.get(i)));
                                    }
                                    else
                                        components.add(array.get(i));
                                }
                                f.set(context, components);
                            }
                            else
                                f.set(context, getFieldFromJSON(f, values, key));

                            break;
                        }
                    }
                }
            }
        }

    }


    /**
     * getFieldFromJSON - Joey Pinto
     * @param f - Relfection field
     * @param values - JSON object containing values assigned to properties
     * @param key - Key to get Field
     * @return Actual object instance
     */
    private Object getFieldFromJSON(Field f, JSONObject values, String key) {
        try {
            if (f.getType().equals(File.class)) {//stream data is file Input Stream ()
                //If type is file, instance of the actual file is passed rather than just the location
                //return new File( blockWorkFlow.getRemoteDirectory() + File.separator + values.getString(key));
                logger.info("Assign FileName " + values.getString(key)+", in jobID "+jobID);
                return new File( blockWorkFlow.getRemoteDirectory() + File.separator + values.getString(key));
            }
            else if (f.getType().equals(int.class) || f.getType().equals(Integer.class))
                return values.getInt(key);
            else if (f.getType().equals(double.class) || f.getType().equals(Double.class))
                return values.getDouble(key);
            else if (f.getType().equals(boolean.class) || f.getType().equals(Boolean.class))
                return values.getBoolean(key);
            return f.getType().cast(values.getString(key));
        }
        catch (Exception e){
            //Unpredictable result when reading from a field
            logger.error(e);
            return null;
        }
    }


    /**
     * toJSON() - Joey Pinto
     * Convert Block to JSON for front-end by Joey Pinto
     */
    public JSONObject toJSON(){
        JSONObject blockJs=new JSONObject();
        blockJs.put("name",getName());
        blockJs.put("family", getFamily());
        blockJs.put("module", getModule());
        blockJs.put("description", getDescription());
        JSONArray fields=new JSONArray();
        for(String key : properties.keySet()){
            Property property=properties.get(key);
            JSONObject field=new JSONObject();
            field.put("name",property.getName());
            field.put("type",property.getType());
            field.put("defaultValue",property.getDefaultValue());
            field.put("attrs","editable");
            field.put("description",property.getDescription());
            fields.put(field);
        }

        if(inputs!=null && inputs.size()!=0) {
            for(String inputParam:inputs.keySet()) {
                Data inputValue=inputs.get(inputParam);
                JSONObject inputObj = new JSONObject();
                inputObj.put("name", inputValue.getName());
                inputObj.put("type", inputValue.getType());
                inputObj.put("attrs", "input");
                inputObj.put("card", inputValue.getCardinality());
                fields.put(inputObj);
            }
        }

        if(outputs!=null && outputs.size()!=0) {
            for(String outputParam:outputs.keySet()){
                Data outputValue=outputs.get(outputParam);
                JSONObject outputObj = new JSONObject();
                outputObj.put("name", outputValue.getName());
                outputObj.put("type", outputValue.getType());
                outputObj.put("attrs", "output");
                outputObj.put("card", outputValue.getCardinality());
                fields.put(outputObj);
            }
        }
        blockJs.put("fields", fields);

        return blockJs;
    }




    /**
     * main - Joey Pinto, Yijie Huang
     *
     * Transfer blockData through RMI or FILE
     * Externally access main function, modification of parameters will affect reflective access
     *  args 0) blockIdName string      1) RMI port                 2) Package Name     3) "RMI"
     *  args 0) serialized input file   1) serialized output file   2) Package Name     3) "FILE"
     */
    public static void main(String[] args) {
        try {
            //blockIdName,                   String.valueOf(port),          getModule().split(":")[1],    "RMI"
            //      0                               1                               2                       3
            //inputFile.getAbsolutePath(),  outputFile.getAbsolutePath(),   getModule().split(":")[1],    "FILE"

            String rmiOrFile = args[3];
            BlockData blockData = null;
            IRemoteData iRemoteDataClient = null;

            if ("RMI".equals(rmiOrFile)) {
                // set RMI url
                int port = Integer.parseInt(args[1]);
                String url = "rmi://localhost:" + port + "/IRemoteData";
                iRemoteDataClient = (IRemoteData) Naming.lookup(url);
                blockData = iRemoteDataClient.getBlockData("["+args[0] + "], for properties and inputs, in main from rmiClient");
            }
            else if ("FILE".equals(rmiOrFile)) {
                //Reading BlockData object from file
                blockData = SerializationUtils.deserialize(FileUtils.readFileToByteArray(new File(args[0])));
            }

            //find block class
            Set<Class<?>> blockTypes = new Reflections(args[2]).getTypesAnnotatedWith(BlockType.class);
            Class type = null;
            for (Class blockType : blockTypes) {
                Annotation annotation = blockType.getAnnotation(BlockType.class);
                Class<? extends Annotation>  currentType = annotation.annotationType();

                String blockTypeName = (String) currentType.getDeclaredMethod("type").invoke(annotation, (Object[]) null);
                if (blockData != null && blockData.getName().equals(blockTypeName)) {
                    type=blockType;
                    break;
                }
            }
            Object obj;
            if(type!=null){
                obj=type.newInstance();
            } else{
                logger.error("No classes with Workflow Designer BlockType Annotations were found!");
                throw new Exception("Error Finding Annotated Class");
            }

            //fetch block properties and inputs
            for(Field field:type.getDeclaredFields()){
                field.setAccessible(true);
                if(field.getAnnotation(BlockInput.class)!=null){
                    field.set(obj,blockData.getInput().get(field.getAnnotation(BlockInput.class).name()));
                }
                else if(field.getAnnotation(BlockProperty.class)!=null){
                    field.set(obj,blockData.getProperties().get(field.getAnnotation(BlockProperty.class).name()));
                }
            }

            //get block execute method and execute
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
            } else{
                logger.error("No method annotated with Workflow Designer BlockExecute was found");
                throw new Exception("Error finding Execute Method");
            }

            //assign block execution return output and outputs
            blockData.setOutput(new HashMap<String, Object>());
            for(Field field:type.getDeclaredFields()){
                field.setAccessible(true);
                if(field.getAnnotation(BlockOutput.class)!=null){
                    blockData.getOutput().put(field.getAnnotation(BlockOutput.class).name(),field.get(obj));
                }
            }

            if("RMI".equals(rmiOrFile)) {
                //set output object to remote blockData
                iRemoteDataClient.setBlockData(blockData,  "["+args[0] +"] outputs, in main from rmiClient");
            }
            else if ("FILE".equals(rmiOrFile)) {//?
                //Write output object to file
                FileOutputStream fos = FileUtils.openOutputStream(new File(args[1]));
                SerializationUtils.serialize(blockData,fos);
                fos.close();
            }

        }
        catch (Exception e) {
            //We need to write error to System.err to
            //propagate exception to the process that started this block.
            //This block is usually started in @executeAsJar and std error
            //output is redirected to propagate exception.
            if(e.getCause() != null)
                e.getCause().printStackTrace();
            else
                e.printStackTrace();
        }


    }




    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Object getContext() {
        return context;
    }

    public void setContext(Object context) {
        this.context = context;
    }

    public BlockWorkFlow getBlockWorkFlow() {
        return blockWorkFlow;
    }

    public void setBlockWorkFlow(BlockWorkFlow blockWorkFlow) {
        this.blockWorkFlow = blockWorkFlow;
    }

    public Map<String, Data> getInputs() {
        return inputs;
    }

    public void setInputs(Map<String, Data> inputs) {
        this.inputs = inputs;
    }

    public Map<String, Data> getOutputs() {
        return outputs;
    }

    public void setOutputs(Map<String, Data> outputs) {
        this.outputs = outputs;
    }

    public Map<String, Property> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, Property> properties) {
        this.properties = properties;
    }

    public boolean isJarExecutable() {
        return jarExecutable;
    }

    public void setJarExecutable(boolean jarExecutable) {
        this.jarExecutable = jarExecutable;
    }

    public Map<String, List< BlockSourceOutput>> getIOMap() {
        return IOMap;
    }

    public void setIOMap(Map<String, List< BlockSourceOutput>> IOMap) {
        this.IOMap = IOMap;
    }

    public Object getFinalOutputObject() {
        return finalOutputObject;
    }

    public void setFinalOutputObject(Object finalOutputObject) {
        this.finalOutputObject = finalOutputObject;
    }

    public boolean isComplete() {
        return complete;
    }

    public void setComplete(boolean complete) {
        this.complete = complete;
    }

    public boolean isStream() {
        return stream;
    }

    public void setStream(boolean stream) {
        this.stream = stream;
    }

    public List<BlockObservation> getDestinationObservers() {
        return destinationObservers;
    }

    public void setDestinationObservers(List<BlockObservation> destinationObservers) {
        this.destinationObservers = destinationObservers;
    }

    public int getObservablesCount() {
        return observablesCount;
    }

    public void setObservablesCount(int observablesCount) {
        this.observablesCount = observablesCount;
    }

    public List<BlockObservation> getSourceObservables() {
        return sourceObservables;
    }

    public void setSourceObservables(List<BlockObservation> sourceObservables) {
        this.sourceObservables = sourceObservables;
    }

    public JSONObject getBlockObject() {
        return blockObject;
    }

    public void setBlockObject(JSONObject blockObject) {
        this.blockObject = blockObject;
    }

    public String getOutputFolder() {
        return outputFolder;
    }

    public void setOutputFolder(String outputFolder) {
        this.outputFolder = outputFolder;
    }

    public boolean[] getErrorFlag() {
        return errorFlag;
    }

    public void setErrorFlag(boolean[] errorFlag) {
        this.errorFlag = errorFlag;
    }

    public int[] getCount() {
        return count;
    }

    public void setCount(int[] count) {
        this.count = count;
    }

    public long getJobID() {
        return jobID;
    }

    public void setJobID(long jobID) {
        this.jobID = jobID;
    }




    public boolean isRmiFlag() {
        return rmiFlag;
    }

    public void setRmiFlag(boolean rmiFlag) {
        this.rmiFlag = rmiFlag;
    }





    public PipedOutputStream[] getPipedOuts() {
        return pipedOuts;
    }

    public void setPipedOuts(PipedOutputStream[] pipedOuts) {
        this.pipedOuts = pipedOuts;
    }

    public PipedInputStream[] getPipedInsTransit() {
        return pipedInsTransit;
    }

    public void setPipedInsTransit(PipedInputStream[] pipedInsTransit) {
        this.pipedInsTransit = pipedInsTransit;
    }

    public PipedOutputStream[] getPipedOutsTransit() {
        return pipedOutsTransit;
    }

    public void setPipedOutsTransit(PipedOutputStream[] pipedOutsTransit) {
        this.pipedOutsTransit = pipedOutsTransit;
    }

    public PipedInputStream[] getPipedIns() {
        return pipedIns;
    }

    public void setPipedIns(PipedInputStream[] pipedIns) {
        this.pipedIns = pipedIns;
    }

    public Map<String, PipedOutputStream> getInTransitsMap() {
        return inTransitsMap;
    }

    public void setInTransitsMap(Map<String, PipedOutputStream> inTransitsMap) {
        this.inTransitsMap = inTransitsMap;
    }

    public Map<String, PipedInputStream> getOutTransitReadMap() {
        return outTransitReadMap;
    }

    public void setOutTransitReadMap(Map<String, PipedInputStream> outTransitReadMap) {
        this.outTransitReadMap = outTransitReadMap;
    }

    public Map<String, List<PipedOutputStream>> getOutTransitWriteMap() {
        return outTransitWriteMap;
    }

    public void setOutTransitWriteMap(Map<String, List<PipedOutputStream>> outTransitWriteMap) {
        this.outTransitWriteMap = outTransitWriteMap;
    }
}
