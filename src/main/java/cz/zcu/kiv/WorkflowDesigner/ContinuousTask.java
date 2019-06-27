package cz.zcu.kiv.WorkflowDesigner;

import cz.zcu.kiv.WorkflowDesigner.Annotations.BlockInput;
import cz.zcu.kiv.WorkflowDesigner.Visualizations.PlotlyGraphs.Graph;
import cz.zcu.kiv.WorkflowDesigner.Visualizations.Table;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.Charset;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

public class ContinuousTask implements Callable<Boolean> {

    private static Log logger = LogFactory.getLog(ContinuousTask.class);


    private int blockID;
    private ContinuousBlock currBlock;
    private JSONObject blockObject;
    private Object output;       //return value of the blockExecute method
    private boolean error;       //error of the this block execution
    private StringBuilder stdErr = new StringBuilder();
    private StringBuilder stdOut = new StringBuilder();
    private String outputFolder;
    private String workflowOutputFile;
    private final JSONArray blocksArray;

    private boolean[] errorFlag; //errorFlag of the whole workFlow


    public ContinuousTask(int blockID, ContinuousBlock currBlock, boolean[] errorFlag, JSONObject blockObject, String outputFolder, String workflowOutputFile, JSONArray blocksArray) {
        this.blockID = blockID;
        this.currBlock = currBlock;
        this.errorFlag = errorFlag;
        this.blockObject = blockObject;
        this.outputFolder = outputFolder;
        this.workflowOutputFile = workflowOutputFile;
        this.blocksArray = blocksArray;
    }


    @Override
    public Boolean call() throws IllegalAccessException, IOException, InterruptedException, InvocationTargetException, TypeMismatchException {
        logger.info(" _______________ start Callable call() for id = "+blockID +", name = "+currBlock.getName()+" _______________ ");

        //not execute the block execute method and return the thread if a block in the workflow already caught error
        if(errorFlag[0]) return false;

        //blocks with inputs
        if(currBlock.getInputs() != null && currBlock.getInputs().size() > 0 ) {

            if(currBlock.isStream()){
                //when input is stream
                logger.info(" ______________block id = "+blockID +", name = "+currBlock.getName()+" deal with stream");

                currBlock.connectIO();
                while (!checkInputsReady()) {
                    if(errorFlag[0]) return false;
                    logger.info(" __ StreamInput not ready for "+currBlock.getId()+" "+currBlock.getName());
                    Thread.sleep(1000);
                    currBlock.connectIO();
                }

            } else {
                //when input is not stream like primitive data, list, File
                logger.info(" ______________block id = "+blockID +", name = "+currBlock.getName()+" deal with normal data type");

                while (!checkSourceComplete()) {
                    if(errorFlag[0]) return false;
                    logger.info(" __ Normal data type Input not ready for "+currBlock.getId()+" "+currBlock.getName());
                    Thread.sleep(1000);
                }
            }
        }

        //check errorFlag again, if errorFlag = true, immediately end the thread
        if(errorFlag[0]) return false;

        //execute block
        try{
            output = currBlock.blockExecute(stdOut, stdErr);
            updateJSON();
            //currBlock.setComplete(true);


        } catch (Exception e){
            error = true;
            synchronized (errorFlag){ errorFlag[0] = true; }

            updateJSON();
            logger.error("Error executing id = "+blockID +", name = "+ currBlock.getName()+" Block.", e);
        }
        return false;
    }


    //update the JSON file of "blocks"
    public void updateJSON() throws IOException{
        logger.info("update JSON for block id = "+blockID +", name = "+currBlock.getName());

        blockObject.put("error", error);
        blockObject.put("stderr", stdErr.toString());
        blockObject.put("stdout", stdOut.toString());
        blockObject.put("completed", true);

        JSONObject JSONOutput = new JSONObject();
        if(output==null){
            JSONOutput = null;
        }  else if (output.getClass().equals(String.class)){

            JSONOutput.put("type","STRING");
            JSONOutput.put("value",output);

        }  else if (output.getClass().equals(File.class)){

            File file = (File) output;
            String destinationFileName="file_"+new Date().getTime()+"_"+file.getName();
            FileUtils.moveFile(file, new File(outputFolder + File.separator + destinationFileName));
            JSONOutput.put("type", "FILE");
            JSONObject fileObject = new JSONObject();
            fileObject.put("title", file.getName());
            fileObject.put("filename", destinationFileName);
            JSONOutput.put("value", fileObject);

        }else if (output.getClass().equals(Table.class)){

            Table table=(Table)output;
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
        else if (output.getClass().equals(Graph.class)){

            Graph graph=(Graph)output;
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
            JSONOutput.put("value",output.toString());

        }

        if (JSONOutput != null)
            blockObject.put("output", JSONOutput);


        synchronized (blocksArray){
            //Save Present JSON (with outputs, errors) to the original file
            logger.info("update blocksArray JSON in workflowOutputFile for block id = "+blockID +", name = "+currBlock.getName());
            if(workflowOutputFile!=null){
                File workflowOutput = new File(workflowOutputFile);
                FileUtils.writeStringToFile(workflowOutput, blocksArray.toString(4), Charset.defaultCharset());
            }
        }



    }




    /**
     * Only used when the blocks are dealt with the Stream(all the inputs of the destination blocks are stream)
     * Then the block need not wait its sources block are executed, it can just fetch the output stream from its source
     * And the source Stream is ready for the destination Stream as long as the outputStream is not null
     *
     * @return true if all its source blocks are completed
     */
    public boolean checkInputsReady() throws IllegalAccessException, IOException{
        //check all the inputStreams of this block (already fetch that stream through reflection before the thread task call)
        Field[] inputFields = currBlock.getContext().getClass().getDeclaredFields();
        for (Field f: inputFields) {
            f.setAccessible(true);

            BlockInput blockInput = f.getAnnotation(BlockInput.class);

            if (blockInput != null){
                Object input = f.get(currBlock.getContext());
                if (input == null) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Only used when the blocks are dealt with the primitive data like int, double ……
     * Then the block must wait until its sources block are executed, otherwise cannot fetch the correct data
     *
     * @return true if all its source blocks are completed
     */
    public boolean checkSourceComplete() throws IllegalAccessException, TypeMismatchException{

        Map<String, List<SourceOutput>> IOMap =currBlock.getIOMap();
        for(String destinationParam : IOMap.keySet()){
            List<SourceOutput> sourceOutputs = IOMap.get(destinationParam);
            for(SourceOutput sourceOutput: sourceOutputs) {

                ContinuousBlock sourceBlock = sourceOutput.getSourceBlock();
                if(!sourceBlock.isComplete())
                    return false;
            }
        }
//        currBlock.connectIO();
        return true;
    }








}
