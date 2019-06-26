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
import java.nio.charset.Charset;
import java.util.*;

import static cz.zcu.kiv.WorkflowDesigner.Type.STREAM;

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




    public BlockObservation(Object context, BlockWorkFlow blockWorkFlow, JSONArray blocksArray, String workflowOutputFile) {
        this.context = context;
        this.blockWorkFlow = blockWorkFlow;
        this.blocksArray = blocksArray;
        this.workflowOutputFile = workflowOutputFile;
    }


    @Override
    public void run() {
        logger.info(" —————————————start————run ————————————————————————————————————————————————————————————————count[0] in this thread is "+count[0]+" —— id = "+getId()+", name = "+getName());

        boolean error = false;
        StringBuilder stdErr = new StringBuilder();
        StringBuilder stdOut = new StringBuilder();
        try{
            logger.info(" ———————————————————————————————————————————————————————————————————————————————————————————————————— Run the thread of id = "+getId()+", name = "+getName());

            this.connectIO();
            finalOutputObject = this.blockExecute(stdOut, stdErr);

        } catch (Exception e){
            logger.error("Error executing id = "+ getId()+", name = "+ getName()+" Block natively", e);
            error = true;
            synchronized (errorFlag){ errorFlag[0] = true; }

        }

        try {
            logger.info("in update ——————————————————— "+getId()+", name = "+getName() );
            updateJSON(error, stdErr.toString(), stdOut.toString());
        }catch (IOException e){
            logger.error("Error update JSON File of id = "+ getId()+", name = "+ getName()+" Block", e);
        }


        synchronized (count){  count[0]--; }


        logger.info(" ——————————————end—————run——————————————————————————————————————————————————————————————count[0] in this thread is "+count[0]+" —— id = "+getId()+", name = "+getName());
    }


    //—————————— for observers  (  destination blocks )  ————update—————————
    @Override
    public void update(Observable o, Object arg) {
        observablesCount ++;

        if(observablesCount == sourceObservables.size()){
            if(errorFlag[0]) return;

            synchronized (count){  count[0]++; }

            logger.info(" ——————  ——————  ——————  ——————  ——————  ——————  ——————  ——————  ——————  ——————  —————— Start the thread for block id = "+getId()+", name = "+getName()+" —————— ");
            Thread myExecuteThread = new Thread(this);
            myExecuteThread.start();

        }
    }

    public Object blockExecute(StringBuilder stdOut, StringBuilder stdErr) throws Exception {
        logger.info("Executing block id = "+ getId() +", name = "+getName());

        Object output;

        if(isJarExecutable() && blockWorkFlow.getJarDirectory()!=null && !stream){
            //Execute block as an external JAR file for normal data

            BlockData blockData = this.connectIO();
            output = executeAsJar(blockData, stdOut, stdErr);
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
                logger.error("Error executing "+getName()+" Block natively",e);
                throw e;
            }
        }

        setFinalOutputObject(output);
        setComplete(true);


        // observable tells all its observers that it has finished execute method
        // and after notify all, just delete all its observers
        setChanged();
        notifyObservers();
        this.deleteObservers();


        logger.info("Execution block id = "+ getId() +", name = "+getName()+ " block completed successfully");
        return output;
    }


    /**
     *  block execute natively(without execute Jar)
     */
    public Object executeInNative() throws IllegalAccessException, InvocationTargetException {
        logger.info("Natively Executing a block:  id-"+getId()+", name-"+getName());

        Object output = null;
        for(Method method : this.context.getClass().getDeclaredMethods()){
            method.setAccessible(true);
            if(method.getAnnotation(BlockExecute.class)!=null){
                output =  method.invoke(this.context);
                break;
            }
        }
        logger.info("Finish natively execute the block:  id-"+getId()+", name-"+getName());
        return output;
    }

    /**
     * Execute block externally as a JAR
     *
     * @param blockData Serializable BlockData model representing all the data needed by a block to execute
     * @param stdOut Standard Output Stream
     * @param stdErr Standard Error Stream
     * @return output returned by BlockExecute Method
     * @throws Exception when output file is not created
     */
    private Object executeAsJar(BlockData blockData, StringBuilder stdOut, StringBuilder stdErr) throws Exception {
        logger.info("Executing id = "+ getId() +", name = "+getName()+" as a JAR");

        Object output;
        try {
            String fileName = "obj_" + new Date().getTime() ;
            File inputFile=new File(blockWorkFlow.getJarDirectory()+File.separator+fileName+".in");
            File outputFile =new File(blockWorkFlow.getJarDirectory()+File.separator+fileName+".out");

            //Serialize and write BlockData object to a file
            FileOutputStream fos = new FileOutputStream(inputFile);
            ObjectOutputStream oos = new ObjectOutputStream(fos);
            oos.writeObject(blockData);
            oos.close();

            File jarDirectory = new File(blockWorkFlow.getJarDirectory());
            jarDirectory.mkdirs();
            String jarFilePath = jarDirectory.getAbsolutePath()+File.separator+getModule().split(":")[0];
            File jarFile = new File(jarFilePath);

            //Calling jar file externally with entry point at the Block's main method
            String defaultVmArgs = "-Xmx1G";
            String vmargs = System.getProperty("workflow.designer.vm.args");
            vmargs = vmargs != null ? vmargs : defaultVmArgs;
            String[]args=new String[]{"java", vmargs, "-cp",jarFile.getAbsolutePath() ,"cz.zcu.kiv.WorkflowDesigner.BlockObservation",inputFile.getAbsolutePath(),outputFile.getAbsolutePath(),getModule().split(":")[1]};
            logger.info("Passing arguments" + Arrays.toString(args));
            ProcessBuilder pb = new ProcessBuilder(args);
            //Store output and error streams to files
            logger.info("Executing jar file "+jarFilePath);
            File stdOutFile = new File("std_output.log");
            pb.redirectOutput(stdOutFile);
            File stdErrFile = new File("std_error.log");
            pb.redirectError(stdErrFile);
            Process ps = pb.start();
            ps.waitFor();
            stdOut.append(FileUtils.readFileToString(stdOutFile, Charset.defaultCharset()));
            String processErr =FileUtils.readFileToString(stdErrFile,Charset.defaultCharset());
            stdErr.append(processErr);
            InputStream is=ps.getErrorStream();
            byte b[]=new byte[is.available()];
            is.read(b,0,b.length);
            String errorString = new String(b);
            if(!errorString.isEmpty()){
                logger.error(errorString);
                stdErr.append(errorString);
            }

            is=ps.getInputStream();
            b=new byte[is.available()];
            is.read(b,0,b.length);
            String outputString = new String(b);
            if(!outputString.isEmpty()){
                logger.info(outputString);
                stdOut.append(outputString);
            }

            FileUtils.deleteQuietly(inputFile);

            if(outputFile.exists()){
                FileInputStream fis = new FileInputStream(outputFile);
                ObjectInputStream ois = new ObjectInputStream(fis);
                blockData = (BlockData) ois.readObject();
                ois.close();
                output=blockData.getProcessOutput();
                FileUtils.deleteQuietly(outputFile);
                for (Field f: context.getClass().getDeclaredFields()) {
                    f.setAccessible(true);
                    BlockOutput blockOutput = f.getAnnotation(BlockOutput.class);
                    if (blockOutput != null) {
                        f.set(context,blockData.getOutput().get(blockOutput.name()));
                    }
                }
            }
            else{
                String err = "Output file does not exist";
                if(processErr != null && !processErr.isEmpty()){
                    err = processErr;
                }
                throw new Exception(err);
            }
        }
        catch (Exception e) {
            e.printStackTrace();
            stdErr.append(org.apache.commons.lang3.exception.ExceptionUtils.getStackTrace(e));
            logger.error("Error executing Jar file", e);
            throw e;
        }
        return output;
    }




    //update the JSON file of "blocks"
    public void updateJSON(boolean error, String stdErr, String stdOut) throws IOException {

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


        synchronized (blocksArray){
            //Save Present JSON (with outputs, errors) to the original file
            logger.info("update blocksArray JSON in workflowOutputFile for block id = "+ getId() +", name = "+ getName());
            if(workflowOutputFile!=null){
                File workflowOutput = new File(workflowOutputFile);
                FileUtils.writeStringToFile(workflowOutput, blocksArray.toString(4), Charset.defaultCharset());
            }
        }

    }



    /**
     * connect the Input of this Block with all its SourceOutput of sourceBlocks
     * by setting the value of the Input using reflection
     *
     */
    public BlockData connectIO() throws IllegalAccessException, TypeMismatchException{
        logger.info(" ______ Connect all the Inputs of BlockObservation[ ID = "+this.id+", " +this.name+" ] with its SourceOutputs.");
        BlockData blockData = new BlockData(getName());//for normal data

        if(!stream){
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
        }

        if(inputs == null || inputs.isEmpty()) return blockData;

        Map<Integer, BlockObservation> indexBlocksMap = blockWorkFlow.getIndexBlocksMap();
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

        return blockData;
    }




    /**
     * exactly same functionality as initialize method by Joey Pinto
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
                if(blockOutput.type().equals(STREAM)){
                    cardinality=WorkflowCardinality.ONE_TO_ONE;
                }
                else{
                    cardinality=WorkflowCardinality.MANY_TO_MANY;
                }
                outputs.put(blockOutput.name(),new Data(blockOutput.name(),blockOutput.type(),cardinality));
            }
        }

        logger.info("Initialized I/O/properties of BlockID "+getId()+", name "+getName()+" block from annotations");

    }


    /**
     * reflection to set the block context instance 's properties field
     *
     */
    public void assignProperties(JSONObject blockObject) throws IllegalAccessException{

        if(properties == null || properties.isEmpty()) return;
        logger.info("Assign the value of properties for block "+getId()+", "+getName());

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
     *
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
                logger.info("Assign FileName " + values.getString(key));
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
     *  Externally access main function, modification of parameters will affect reflective access
     *
     * @param args 1) serialized input file 2) serialized output file 3) Package Name
     */
    public static void main(String[] args) {
        try {
            //Reading BlockData object from file
            BlockData blockData = SerializationUtils.deserialize(FileUtils.readFileToByteArray(new File(args[0])));

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
                logger.error("No classes with Workflow Designer BlockType Annotations were found!");
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
                logger.error("No method annotated with Workflow Designer BlockExecute was found");
                throw new Exception("Error finding Execute Method");
            }

            blockData.setOutput(new HashMap<String, Object>());

            for(Field field:type.getDeclaredFields()){
                field.setAccessible(true);
                if(field.getAnnotation(BlockOutput.class)!=null){
                    blockData.getOutput().put(field.getAnnotation(BlockOutput.class).name(),field.get(obj));
                }

            }

            //Write output object to file
            FileOutputStream fos = FileUtils.openOutputStream(new File(args[1]));
            SerializationUtils.serialize(blockData,fos);
            fos.close();
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
}