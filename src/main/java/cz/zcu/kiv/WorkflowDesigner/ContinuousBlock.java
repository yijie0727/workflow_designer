package cz.zcu.kiv.WorkflowDesigner;

import cz.zcu.kiv.WorkflowDesigner.Annotations.BlockExecute;
import cz.zcu.kiv.WorkflowDesigner.Annotations.BlockInput;
import cz.zcu.kiv.WorkflowDesigner.Annotations.BlockOutput;
import cz.zcu.kiv.WorkflowDesigner.Annotations.BlockProperty;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public class ContinuousBlock {

    private static Log logger = LogFactory.getLog(ContinuousBlock.class);

    //Fields used to initialize front end Block Tree imgs
    private String name;
    private String family;
    private String module;
    private String description;
    private Map<String, Data>       inputs;
    private Map<String, Data>       outputs;
    private Map<String, Property>   properties;


    //Fields used to denote a ContinuousBlock in the workFlow relationship
    private int id;
    private Object context;    //Actual Object instance maintained as context
    private ContinuousWorkFlow continuousWorkFlow;
    private Map<String, SourceOutput> IOMap;  //since this continuousBlock ony deal with stream Inputs/outputs/Properties, so every destination Input can only be connected with one Output stream Source
    private Object finalOutputObject;
    private boolean error = false;
    private String stdErr = null;


    public ContinuousBlock(Object context, ContinuousWorkFlow continuousWorkFlow) {
        this.context = context;
        this.continuousWorkFlow = continuousWorkFlow;
    }



    /**
     *  block execute
     */
    public Object blockExecute() throws IllegalAccessException, InvocationTargetException {
        logger.info("Natively Executing a block:  id-"+getId()+", name-"+getName());

        Object output = null;
        for(Method method : this.context.getClass().getDeclaredMethods()){
            method.setAccessible(true);
            if(method.getAnnotation(BlockExecute.class)!=null){
                output =  method.invoke(this.context);
                break;
            }
        }

        setFinalOutputObject(output);
        return output;
    }




    /**
     * connect the Input of this Block with all its SourceOutput of sourceBlocks
     * by setting the value of the Input using reflection
     *
     * Since all the parameters are not the 8 primitive types, so this should be a reference type,  byte[] or other else
     * and when the Source Outputs changed due to execution, input should also change automatically due to reflection
     */
    public void connectIO() throws IllegalAccessException{
        logger.info(" ______ Connect all the Inputs of ContinuousBlock[ ID = "+this.id+", " +this.name+" ] with its SourceOutputs.");

        if(inputs == null || inputs.isEmpty()) return;

        Map<Integer, ContinuousBlock> indexBlocksMap = continuousWorkFlow.getIndexBlocksMap();
        for(String destinationParam : IOMap.keySet()){
            Object outStream = null;

            //get SourceBlock
            SourceOutput sourceOutput = IOMap.get(destinationParam);
            int sourceBlockID = sourceOutput.getSourceBlockID();
            String sourceParam = sourceOutput.getSourceParam();
            ContinuousBlock sourceBlock = indexBlocksMap.get(sourceBlockID);

            //get O
            Field[] outputFields = sourceBlock.getContext().getClass().getDeclaredFields();
            for (Field f: outputFields) {
                f.setAccessible(true);

                BlockOutput blockOutput = f.getAnnotation(BlockOutput.class);
                if (blockOutput != null){
                    if(blockOutput.name().equals(sourceParam)){
                        outStream = f.get(sourceBlock.getContext());
                        break;
                    }
                }
            }

            //get I
            Field[] inputFields = this.context.getClass().getDeclaredFields();
            for (Field f: inputFields) {
                f.setAccessible(true);

                BlockInput blockInput = f.getAnnotation(BlockInput.class);

                if (blockInput != null){
                    if(blockInput.name().equals(destinationParam)){
                        f.set(this.context,outStream);
                        break;
                    }
                }
            }

        }

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
            setIOMap(new HashMap<String, SourceOutput>());

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
                inputs.put(blockInput.name(),new Data(blockInput.name(),blockInput.type(),cardinality));
            }

            BlockOutput blockOutput = f.getAnnotation(BlockOutput.class);
            if (blockOutput != null){
                outputs.put(blockOutput.name(),new Data(blockOutput.name(),blockOutput.type(),WorkflowCardinality.MANY_TO_MANY));
            }
        }

        logger.info("Initialized I/O/properties of BlockID "+getId()+", name "+getName()+" block from annotations");

    }


    /**
     * reflection to set the block context instance 's properties field
     *
     * // TODO how to receive the properties since it is stream data ????/////////
     *
     */
    public void assignProperties(JSONObject blockObject) throws IllegalAccessException{

        if(properties == null || properties.isEmpty()) return;
        logger.info("Assign the value of properties for block "+getId()+", "+getName());


        JSONObject values = blockObject.getJSONObject("values");

        //Map properties to object parameters
        for(String key : this.properties.keySet()){
            //"values": {
            //"File": "/Users/yijie/Desktop/INCF/input.txt"
            // }
            if(values.has(key)){
                for (Field f: context.getClass().getDeclaredFields()) {
                    f.setAccessible(true);
                    BlockProperty blockProperty = f.getAnnotation(BlockProperty.class);
                    if (blockProperty != null) {
                        if(blockProperty.name().equals(key)){

                            //Assign object attributes from properties
                            f.set(context,  getFieldFromJSON(f,values, key));
                            break;
                        }
                    }
                }
            }
        }

    }


    /**
     * //TODO Get value of actual property field from JSON, maybe get field val from Json is no longer available for streams,,,,,No idea how to send real time stream data from front end to back end
     * @param f - Relfection field
     * @param values - JSON object containing values assigned to properties
     * @param key - Key to get Field
     * @return Actual object instance
     */
    private Object getFieldFromJSON(Field f, JSONObject values, String key) {
        try {
            if (f.getType().equals(File.class)) {//stream data is file Input Stream ()
                //If type is file, instance of the actual file is passed rather than just the location
                //return new File(continuousWorkFlow.getRemoteDirectory() + File.separator + values.getString(key));
                logger.info("Assign FileName " + values.getString(key));
                return new File(values.getString(key));
            }
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

    public ContinuousWorkFlow getContinuousWorkFlow() {
        return continuousWorkFlow;
    }

    public void setContinuousWorkFlow(ContinuousWorkFlow continuousWorkFlow) {
        this.continuousWorkFlow = continuousWorkFlow;
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

    public Map<String, SourceOutput> getIOMap() {
        return IOMap;
    }

    public void setIOMap(Map<String, SourceOutput> IOMap) {
        this.IOMap = IOMap;
    }

    public Object getFinalOutputObject() {
        return finalOutputObject;
    }

    public void setFinalOutputObject(Object finalOutputObject) {
        this.finalOutputObject = finalOutputObject;
    }

    public boolean isError() {
        return error;
    }

    public void setError(boolean error) {
        this.error = error;
    }

    public String getStdErr() {
        return stdErr;
    }

    public void setStdErr(String stdErr) {
        this.stdErr = stdErr;
    }
}
