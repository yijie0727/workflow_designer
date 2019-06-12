package cz.zcu.kiv.WorkflowDesigner;

import cz.zcu.kiv.WorkflowDesigner.Annotations.*;
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

/***********************************************************************************************************************
 *
 * ContinuousBlock, GSoC 2019, 2019/05/06 Yijie Huang
 * Part of the methods are cited from the GSoC 2018 Block.java coded by Joey Pinto
 *
 * This file is the model for a single block to acheive the continuous data process in the workflow designer tool.
 **********************************************************************************************************************/

public class ContinuousBlock {
    private static Log logger = LogFactory.getLog(ContinuousBlock.class);

    private ContinuousWorkflow continuousWorkflow;
    private String name;
    private String family;
    private String module;
    private String description;
    private boolean jarExecutable;
    private boolean rmiExecutable;
    private Map<String, Data> input;
    private Map<String, Data> output;
    private Map<String,Property> properties;

    //Actual Object instance maintained as context
    private Object context;

    public static final int PENDING = 0;    //Wait to receive inputs/properties continuous data to process
    public static final int PROCESSING = 1; //Block is processing inputs/properties continuous data
    public static final int END = 2;        //No longer accept data(input/property) anymore (done all the process job already)（may still has outputs not be fetched by its destination Blocks ）

    private int blockStatus = PENDING;      //in the very beginning, every block is in the PENDING status
    private boolean comingPropertyPrepared = true;     //only useful for the Blocks only with @BlockProperty annotation without @BlockInput. In the very beginning, those block are by default receiving the coming continuous data
    private boolean outputPrepared = false; //prepared the output already or not
    private int connectedCount = 0;         //the number of destination Blocks it connects
    private int sentDataCount = 0;          //the number of it sends its previous output(just generated) to its different destination Blocks

    public ContinuousBlock(Object context, ContinuousWorkflow continuousWorkflow) {
        this.context = context;
        this.continuousWorkflow = continuousWorkflow;
    }

    /**
     * checkComingPropertyPrepared - Yijie Huang
     * -  only useful for the ContinuousBlocks only with @BlockProperty annotation.
     * In the very beginning, those block are by default receiving the coming continuous data
     *
     */
    public void checkComingPropertyPrepared(){

        logger.info("                                                                                         ^ checkComingPropertyPrepared for ContinuousBlock "+ this.getName());

        //for Blocks (not receive the continuous data)
        //this variable: comingPropertyPrepared is meaningless

        //for ContinuousBlocks has inputs, they no need to check this variable to modify the comingPropertyPrepared

        //reflect to get the  ContinuousProperty of the context of the Continuous Block and detect whether it has continuous data or not
        for (Field f: context.getClass().getDeclaredFields()) {
            f.setAccessible(true);

            ContinuousProperty continuousProperty = f.getAnnotation(ContinuousProperty.class);
            if (continuousProperty != null) {
                if(f.getGenericType().toString().equals("boolean")){
                    try
                    {
                        boolean continuous = (Boolean)f.get(this.getContext());
                        logger.info("                                                                                         # Now @ContinuousProperty continuous = "+ continuous + " for ContinuousBlock "+ this.getName());
                        if(!continuous) {
                            this.setComingPropertyPrepared(false);
                            logger.info("!!!  !!! !!!  !!! !!!  !!! !!!  !!! !!!  !!! !!!  !!! !!!  !!! !!!  !!! !!!  !!! !!!  !!! SET boolean comingPropertyPrepared = "+ comingPropertyPrepared + " for ContinuousBlock "+ this.getName());

                            break;
                        }
                    }catch ( IllegalAccessException e ) {
                        logger.error(e);
                    }
                }
            }
        }
    }

    public void checkComingPropertyPrepared1(){
        logger.info("                                                                                  ^ check ComingPropertyPrepared 【 true or false 】for ContinuousBlock "+ this.getName());

        Method getContinuousMethod = null;
        for(Method m : context.getClass().getDeclaredMethods()){
            m.setAccessible(true);
            if(m.getAnnotation(ContinuousGet.class)!=null){
                getContinuousMethod = m;
                break;
            }
        }
        logger.info("getContinuousMethod = "+ getContinuousMethod);

        boolean continuous = false;
        if(getContinuousMethod!=null){
            try{
                continuous = (boolean)getContinuousMethod.invoke(context);
                logger.info("                                                                          # Now @ContinuousProperty continuous = "+ continuous + " for ContinuousBlock "+ this.getName());

            }catch ( Exception e){
                e.printStackTrace();
            }
        }
        if(!continuous) {
            this.setComingPropertyPrepared(false);
            logger.info("!!!  !!! !!!  !!! !!!  !!! !!!  !!! !!!  !!! !!!  !!! !!!  !!! !!!  SET boolean comingPropertyPrepared = "+ comingPropertyPrepared + " for ContinuousBlock "+ this.getName());

        }

    }


    /**
     * outputDataFetched - Yijie Huang
     *
     * used to check whether the Block sends this output to all its destination Blocks, if true reset its outputPrepared to false
     */
    public void outputDataFetched(int blockId){
        if(connectedCount == 0) return;
        if(connectedCount == sentDataCount){
            this.setSentDataCount(0);
            this.setOutputPrepared(false);
        }
        logger.info("                                                                                         # current checked continuousBlock " + this.getName() + " BlockId = "+blockId+", outputPrepared = " + this.isOutputPrepared() + ", sentDataCount = " +this.getSentDataCount());
    }

//    public boolean outputDataFetched(){
//        if(connectedCount == 0) return false;
//        return connectedCount == sentDataCount;
//    }


    /**
     * toJSON - Joey Pinto
     * Convert Block to JSON for front-end
     */
    public JSONObject toJSON(){
        JSONObject blockJs=new JSONObject();
        blockJs.put("name",getName());
        blockJs.put("family", getFamily());
        blockJs.put("module", getModule());
        blockJs.put("description", getDescription());
        JSONArray fields=new JSONArray();
        for(String key:properties.keySet()){
            Property property=properties.get(key);
            JSONObject field=new JSONObject();
            field.put("name",property.getName());
            field.put("type",property.getType());
            field.put("defaultValue",property.getDefaultValue());
            field.put("attrs","editable");
            field.put("description",property.getDescription());
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
     * fromJSON - Joey Pinto
     * @param blockObject - Initialize a Block object from a JSON representation
     */
    public void fromJSON(JSONObject blockObject) throws NoSuchMethodException, InstantiationException, IllegalAccessException, InvocationTargetException, FieldMismatchException {
        this.name = blockObject.getString("type");
        this.module = blockObject.getString("module");

        //Get block definition from workflow
        ContinuousBlock block = continuousWorkflow.getDefinition(this.name);

        if(block==null){
            logger.error("Could not find definition of block type "+this.name);
            throw new FieldMismatchException(this.name,"block type");
        }

        this.family = block.getFamily();
        this.properties = block.getProperties();
        this.input = block.getInput();
        this.output = block.getOutput();
        this.jarExecutable = block.isJarExecutable();


        JSONObject values = blockObject.getJSONObject("values");

        //Map properties to object parameters
        for(String key:this.properties.keySet()){
            if(values.has(key)){
                for (Field f: context.getClass().getDeclaredFields()) {
                    f.setAccessible(true);
                    BlockProperty blockProperty = f.getAnnotation(BlockProperty.class);
                    if (blockProperty != null) {
                        if(blockProperty.name().equals(key)){
                            properties.put(blockProperty.name(), new Property(blockProperty.name(), blockProperty.type(), blockProperty.defaultValue(), blockProperty.description()));

                            //Assign object attributes from properties
                            if(blockProperty.type().endsWith("[]")){

                                //Dealing with List properties
                                if(f.getType().isArray()){
                                    //Unsupported by reflection
                                    throw new IllegalAccessException("Arrays Not supported, Use List instead");
                                }
                                List<Object> components=new ArrayList<>();
                                JSONArray array=values.getJSONArray(key);
                                ParameterizedType listType = (ParameterizedType) f.getGenericType();
                                Class<?> listClass = (Class<?>) listType.getActualTypeArguments()[0];
                                for(int i=0;i<array.length();i++){
                                    if(listClass.equals(File.class)){
                                        components.add(new File(continuousWorkflow.getRemoteDirectory()+File.separator+array.get(i)));
                                    }
                                    else components.add(array.get(i));
                                }
                                f.set(context,components);
                            }
                            else f.set(context,getFieldFromJSON(f,values, key));
                            break;
                        }
                    }
                }
            }
        }

        logger.info("Instantiated "+getName()+" block from Workflow");
    }

    /**
     * Get value of actual property field - Joey pinto
     * @param f - Relfection field
     * @param values - JSON object containing values assigned to properties
     * @param key - Key to get Field
     * @return Actual object instance
     */
    private Object getFieldFromJSON(Field f, JSONObject values, String key) {
        try {
            if (f.getType().equals(int.class) || f.getType().equals(Integer.class))
                return values.getInt(key);
            else if (f.getType().equals(double.class) || f.getType().equals(Double.class))
                return values.getDouble(key);
            else if (f.getType().equals(boolean.class) || f.getType().equals(Boolean.class))
                return values.getBoolean(key);
            else if (f.getType().equals(File.class))
                //If type is file, instance of the actual file is passed rather than just the location
                return new File(continuousWorkflow.getRemoteDirectory() + File.separator + values.getString(key));

            return f.getType().cast(values.getString(key));
        }
        catch (Exception e){
            //Unpredictable result when reading from a field
            logger.error(e);
            return null;
        }
    }

    /**
     * initialize - Joey Pinto
     * Initialize a Block from a class
     */
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
                properties.put(blockProperty.name(),new Property(blockProperty.name(),blockProperty.type(),blockProperty.defaultValue(), blockProperty.description()));
            }

            BlockInput blockInput = f.getAnnotation(BlockInput.class);
            if (blockInput != null){
                String cardinality="";
                if(blockInput.type().endsWith("[]")){
                    cardinality=WorkflowCardinality.MANY_TO_MANY;
                }
                else{
                    cardinality=WorkflowCardinality.ONE_TO_ONE;
                }
                input.put(blockInput.name(),new Data(blockInput.name(),blockInput.type(),cardinality));
            }

            BlockOutput blockOutput = f.getAnnotation(BlockOutput.class);
            if (blockOutput != null){
                output.put(blockOutput.name(),new Data(blockOutput.name(),blockOutput.type(),WorkflowCardinality.MANY_TO_MANY));
            }
        }

        logger.info("Initialized "+getName()+" block from annotations");
    }

    /**
     * Process a block and return value returned by BlockExecute block - Joey Pinto
     * @param blocks Map of blocks
     * @param fields Mapping of fieldName to actual field
     * @param stdOut Standard Output stream builder
     * @param stdErr Error Stream builder
     * @return Object returned by BlockExecute method
     * @throws Exception
     */
    public Object processBlock(Map<Integer, ContinuousBlock> blocks, Map<String,InputField> fields, StringBuilder stdOut, StringBuilder stdErr) throws Exception {
        Object output;
        BlockData blockData=new BlockData(getName());

        logger.info("Processing a "+getName()+" block");

        //Assign inputs to the instance
        assignInputs(blocks,fields,blockData);

        if(isJarExecutable() && continuousWorkflow.getJarDirectory()!=null){
            //Execute block as an external JAR file
            output = executeAsJar(blockData,stdOut,stdErr);
        }
        else{
            logger.info("Executing "+getName()+" block natively");
            try {
                //Execute block natively in the current class loader
                output = process();
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

        setBlockStatus(PENDING); /** update flags*/
        logger.info("                                                                                      (0）   Execute Successfully. SET "+getName()+ " to 【 PENDING 】");

        for (Field f: context.getClass().getDeclaredFields()) {
            f.setAccessible(true);

            ContinuousProperty continuousProperty = f.getAnnotation(ContinuousProperty.class);
            if (continuousProperty != null) {
                if(f.getGenericType().toString().equals("boolean")){
                    try
                    {
                        f.set(context, false);
                        boolean continuous = (Boolean)f.get(this.getContext());

                        logger.info("                                                                                         # Now @ContinuousProperty continuous = "+ continuous + " for ContinuousBlock "+ this.getName());
                        if(!continuous) {
                            this.setComingPropertyPrepared(false);
                            logger.info("!!!  !!! !!!  !!! !!!  !!! !!!  !!! !!!  !!! !!!  !!! !!!  !!! !!!  !!! !!!  !!! !!!  !!! SET boolean comingPropertyPrepared = "+ comingPropertyPrepared + " for ContinuousBlock "+ this.getName());

                            break;
                        }
                    }catch ( IllegalAccessException e ) {
                        logger.error(e);
                    }
                }
            }
        }








        return output;
    }

    /**
     * Execute block externally as a JAR - Joey Pinto
     *
     * modify the creation mode and storing place of obj.in/out and .log file to avoid race in multi-thread Parallel Block Execution,
     * since under the multi-thread case, every Block Execution task will write into those files
     *
     * @param blockData Serializable BlockData model representing all the data needed by a block to execute
     * @param stdOut Standard Output Stream
     * @param stdErr Standard Error Stream
     * @return output returned by BlockExecute Method
     * @throws Exception when output file is not created
     */
    private Object executeAsJar(BlockData blockData, StringBuilder stdOut, StringBuilder stdErr) throws Exception {

        Object output;
        logger.info("Executing "+getName()+" as a JAR");
        try {
            File tempInFile =  File.createTempFile("obj_in_",".in",   new File(continuousWorkflow.getJarDirectory()));
            File tempOutFile = File.createTempFile("obj_out_",".out", new File(continuousWorkflow.getJarDirectory()));
            File inputFile  = new File(tempInFile.getAbsolutePath());
            File outputFile = new File(tempOutFile.getAbsolutePath());
            tempInFile.deleteOnExit();
            tempOutFile.deleteOnExit();

            //Serialize and write BlockData object to a file
            FileOutputStream fos = new FileOutputStream(inputFile);
            ObjectOutputStream oos = new ObjectOutputStream(fos);
            oos.writeObject(blockData);
            oos.close();

            File jarDirectory = new File(continuousWorkflow.getJarDirectory());
            jarDirectory.mkdirs();
            String jarFilePath = jarDirectory.getAbsolutePath()+File.separator+getModule().split(":")[0];
            File jarFile = new File(jarFilePath);

            //Calling jar file externally with entry point at the ContinuousBlock's main method
            String defaultVmArgs = "-Xmx1G";
            String vmargs = System.getProperty("workflow.designer.vm.args");
            vmargs = vmargs != null ? vmargs : defaultVmArgs;
            String[]args=new String[]{"java", vmargs, "-cp",jarFile.getAbsolutePath() ,"cz.zcu.kiv.WorkflowDesigner.ContinuousBlock",inputFile.getAbsolutePath(),outputFile.getAbsolutePath(),getModule().split(":")[1]};
            logger.info("Passing arguments" + Arrays.toString(args));
            ProcessBuilder pb = new ProcessBuilder(args);
            //Store output and error streams to files
            logger.info("Executing jar file "+jarFilePath);

            File tempStdOutFile =  File.createTempFile("std_output_",".log", new File(continuousWorkflow.getJarDirectory()));
            File stdOutFile = new File(tempStdOutFile.getAbsolutePath());
            File tempStdErrFile =  File.createTempFile("std_error_",".log", new File(continuousWorkflow.getJarDirectory()));
            File stdErrFile = new File(tempStdErrFile.getAbsolutePath());
            tempStdOutFile.deleteOnExit();
            tempStdErrFile.deleteOnExit();

            pb.redirectOutput(stdOutFile);
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


    /**
     * Assign Inputs - Maps output fields of previous block to input fields of next block and initializes properties
     * Add setSentDataCount to update the source blocks'sentDataCount to the GSoC 2018 project by Joey Pinto
     * @param blocks
     * @param fields
     * @param blockData
     * @throws FieldMismatchException
     * @throws IllegalAccessException
     * @throws ClassNotFoundException
     */
    private void assignInputs(Map<Integer,ContinuousBlock> blocks, Map<String,InputField> fields, BlockData blockData) throws FieldMismatchException, IllegalAccessException, ClassNotFoundException {
        //Assign properties to object instance
        for (Field f: context.getClass().getDeclaredFields()) {
            f.setAccessible(true);

            BlockProperty blockProperty = f.getAnnotation(BlockProperty.class);
            if (blockProperty != null) {
                if(blockProperty.type().equals(Type.FILE)){
                    blockData.getProperties().put(blockProperty.name(),new File(continuousWorkflow.getRemoteDirectory()+File.separator+f.get(context)));
                }
                else blockData.getProperties().put(blockProperty.name(),f.get(context));
            }
        }

        if(getInput()!=null&&getInput().size()>0) {

            //Assigning inputs
            for (String key : getInput().keySet()) {
                InputField field = fields.get(key);
                if(field==null){
                    //Missing input field
                    continue;
                }
                //Getting destination for data
                Data destinationData=getInput().get(key);

                int inputCardinality = field.getSourceParam().size();
                List<Object> components=new ArrayList<>();

                for(int i=0;i<inputCardinality;i++){
                    Object value = null;
                    String sourceParam = field.getSourceParam().get(i);
                    int sourceBlockId = field.getSourceBlock().get(i);
                    ContinuousBlock sourceBlock = blocks.get(sourceBlockId);
                    if(field.getSourceBlock()==null){
                        logger.error("Could not find the source block for the input "+key+" for a "+getName()+" block");
                        throw new FieldMismatchException(key,"source");
                    }
                    Map<String, Data> source = sourceBlock.getOutput();
                    Data sourceData=null;

                    if(source.containsKey(sourceParam)){
                        sourceData=source.get(sourceParam);
                    }
                    if(sourceData==null) {
                        throw new FieldMismatchException(key,"source");
                    }

                    for (Field f: sourceBlock.getContext().getClass().getDeclaredFields()) {
                        f.setAccessible(true);

                        BlockOutput blockOutput = f.getAnnotation(BlockOutput.class);
                        if (blockOutput != null){
                            if(blockOutput.name().equals(sourceData.getName())) {
                                value = f.get(sourceBlock.getContext());

                                //update source blocks'sentDataCount
                                int sentCount = sourceBlock.getSentDataCount();
                                sourceBlock.setSentDataCount(++sentCount);
                                logger.info("                                                        # SourceBlock "+sourceBlock.getName() +", SourceBlock's sentCount = "+sourceBlock.getSentDataCount());

                                break;
                            }
                        }



                    }
                    components.add(value);
                }

                //Assigning outputs to destination
                for (Field f: context.getClass().getDeclaredFields()) {
                    f.setAccessible(true);

                    BlockInput blockInput = f.getAnnotation(BlockInput.class);
                    if (blockInput != null) {
                        if(blockInput.name().equals(destinationData.getName())){
                            if(!blockInput.type().endsWith("[]")){
                                Object val=components.get(0);
                                f.set(context,val);
                                blockData.getInput().put(destinationData.getName(),val);
                            }
                            else{
                                if(f.getType().isArray()){
                                    throw new IllegalAccessException("Arrays not supported, Use Lists Instead");
                                }
                                f.set(context,f.getType().cast(components));
                                blockData.getInput().put(destinationData.getName(),components);
                            }
                            break;
                        }
                    }
                }
            }
        }
    }

    /**
     * process - execute a method annotated by Block Execute annotation - Joey Pinto
     * @return whatever object is returned by the method
     */
    public Object process() throws InvocationTargetException, IllegalAccessException {
        Object output = null;
        for(Method method:context.getClass().getDeclaredMethods()){
            method.setAccessible(true);
            if(method.getAnnotation(BlockExecute.class)!=null){
                output =  method.invoke(context);
                break;
            }
        }
        return output;
    }


    /**
     *  main - Joey Pinto
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

    public boolean isJarExecutable() {
        return jarExecutable;
    }

    public void setJarExecutable(boolean jarExecutable) {
        this.jarExecutable = jarExecutable;
    }

    public boolean isRmiExecutable() {
        return rmiExecutable;
    }

    public void setRmiExecutable(boolean rmiExecutable) {
        this.rmiExecutable = rmiExecutable;
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

    public Map<String, Property> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, Property> properties) {
        this.properties = properties;
    }

    public Object getContext() {
        return context;
    }

    public void setContext(Object context) {
        this.context = context;
    }

    public int getBlockStatus() {
        return blockStatus;
    }

    public void setBlockStatus(int blockStatus) {
        this.blockStatus = blockStatus;
    }

    public boolean isComingPropertyPrepared() {
        return comingPropertyPrepared;
    }

    public void setComingPropertyPrepared(boolean comingPropertyPrepared) {
        this.comingPropertyPrepared = comingPropertyPrepared;
    }

    public boolean isOutputPrepared() {
        return outputPrepared;
    }

    public void setOutputPrepared(boolean outputPrepared) {
        this.outputPrepared = outputPrepared;
    }

    public int getConnectedCount() {
        return connectedCount;
    }

    public void setConnectedCount(int connectedCount) {
        this.connectedCount = connectedCount;
    }

    public int getSentDataCount() {
        return sentDataCount;
    }

    public void setSentDataCount(int sentDataCount) {
        this.sentDataCount = sentDataCount;
    }
}
